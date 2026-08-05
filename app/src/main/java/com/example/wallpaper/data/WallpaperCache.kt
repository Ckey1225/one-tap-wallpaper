package com.example.wallpaper.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 壁纸缓存管理器（FIFO 队列式预取缓存）。
 *
 * 设计（用户确认的"序号 1、2、3 … 栈式排列"）：
 * - 应用启动 / 每次换壁纸后自动预取，保证缓存队列始终达到配置数量（默认 3 张，可配 2~5）；
 * - 点击"换壁纸"时：取队列中最旧一张（序号 1）→ 移入"已应用"历史 → 网络补一张到队尾（序号 N+1）；
 * - 换壁纸永远优先命中本地缓存（瞬间完成），只有缓存耗尽时才回退实时下载兜底；
 * - 已应用壁纸文件按时间保留最近 [MAX_APPLIED] 张，供"壁纸记录"页展示缩略图。
 *
 * 存储位置（用户要求，外部可见，方便直接查看缓存壁纸）：
 * - 默认目录：Android/data/<包名>/files/wallpapers/（应用专属外部目录，无需权限）
 * - 自定义目录：设置页通过 SAF 选择任意目录（content:// 持久授权）
 * - 子目录结构：cache/（待用队列，序号 1 最先被应用）、applied/（已应用历史）
 *
 * 文件后缀：下载内容按真实格式保存为 .jpg 或 .png；
 * 其他格式（webp/gif 等）自动转码为 .jpg，保证后缀与实际内容一致。
 */
class WallpaperCache(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = PreferenceStore(appContext)

    /** 根目录（File 模式 = file://，自定义 SAF 模式 = content://） */
    private val rootDoc: DocumentFile
    private val backend: Backend

    init {
        val customUri = prefs.cacheDirUri
        rootDoc = if (customUri.isNotBlank()) {
            DocumentFile.fromTreeUri(appContext, Uri.parse(customUri)) ?: defaultRoot()
        } else {
            defaultRoot()
        }
        backend = if (rootDoc.uri.scheme == "file") {
            FileBackend(appContext, rootDoc)
        } else {
            SafBackend(appContext, rootDoc)
        }
    }

    /** 默认目录：Android/data/<包名>/files/wallpapers（外部存储不可用时回退内部私有目录） */
    private fun defaultRoot(): DocumentFile {
        val ext = appContext.getExternalFilesDir(null)
        val root = if (ext != null) File(ext, "wallpapers")
        else File(appContext.filesDir, "wallpapers")
        root.mkdirs()
        return DocumentFile.fromFile(root)
    }

    /** 当前是否使用默认目录 */
    val isDefaultDir: Boolean get() = prefs.cacheDirUri.isBlank()

    /** 缓存队列当前数量 */
    fun cachedCount(): Int = backend.cachedItems().size

    /** 缓存中的壁纸（升序：序号 1 最先被应用） */
    fun cachedItems(): List<DocumentFile> = backend.cachedItems()

    /** 已应用历史壁纸（最新在前） */
    fun appliedItems(): List<DocumentFile> = backend.appliedItems()

    /** 按文件名查找已应用壁纸（供"壁纸记录"页缩略图），不存在返回 null */
    fun appliedItem(name: String): DocumentFile? =
        backend.appliedItems().firstOrNull { it.name == name }

    /**
     * 从缓存队列取最旧一张（序号 1）并移入已应用历史。
     * 取操作用独立短锁保护（[takeLock]），与后台预取（[fullLock]）互不阻塞，
     * 确保快速连点换壁纸时始终秒级命中缓存、不被预取拖慢。
     * @return 移入历史后的壁纸；队列为空时返回 null（调用方回退实时下载）
     */
    fun takeForApply(): DocumentFile? = synchronized(takeLock) { backend.takeForApply() }

    /**
     * 保存一张已应用壁纸（实时下载兜底场景）到历史目录。
     * @return 保存的文件名（失败时文件不存在，记录页自动隐藏缩略图）
     */
    fun saveApplied(bitmap: Bitmap): String {
        val tmp = createTempFile()
        runCatching {
            tmp.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }
        }
        val name = "w_${System.currentTimeMillis()}.jpg"
        backend.importTempFile(tmp, name, intoApplied = true)
        return name
    }

    /** 解码一张壁纸（缓存/历史均可，File 与 SAF 两种模式统一入口），失败返回 null */
    fun decodeImage(doc: DocumentFile, maxDimension: Int = 4096): Bitmap? =
        backend.decode(doc, maxDimension)

    /**
     * 打开壁纸文件的原始字节流（File 与 SAF 统一入口）。
     * 用于"导出原图到相册"等需要完整原图数据的场景；失败返回 null。
     */
    fun open(doc: DocumentFile): java.io.InputStream? = runCatching {
        backend.openStream(doc)
    }.getOrNull()?.let { it }

    /**
     * 补充预取直到缓存队列满（数量 = 配置值）。
     * 队列已满时立即返回 true；网络失败时中断，下次换壁纸 / 启动时机自动重试。
     * 全程持有 [fullLock]，与 [takeForApply] 的 [takeLock] 相互独立，不阻塞取缓存。
     * @return 是否达到配置数量
     */
    suspend fun ensureFull(): Boolean = synchronized(fullLock) {
        var ok = true
        while (ok && cachedCount() < prefs.cacheSize) {
            ok = prefetchOne(prefs.imageUrl)
        }
        cachedCount() >= prefs.cacheSize
    }

    /**
     * 非阻塞补预取（fire-and-forget）。
     * 在后台协程补满缓存，立即返回，不拖慢换壁纸/启动；
     * 同一时间只允许一个预取任务，连点/多入口并发时不会重复下载。
     */
    fun ensureFullAsync() {
        if (cachedCount() >= prefs.cacheSize) return
        if (!prefetchRunning.compareAndSet(false, true)) return
        prefetchScope.launch {
            try {
                ensureFull()
            } catch (e: Exception) {
                Log.w(TAG, "后台补预取失败：${e.message}")
            } finally {
                prefetchRunning.set(false)
            }
        }
    }

    /** 临时下载文件（应用缓存目录，仅中转，不落盘持久存储） */
    private fun createTempFile(): File = File(appContext.cacheDir, "tmp_wp_${System.currentTimeMillis()}")

    /** 下载一张并导入缓存队列；失败返回 false（内部最多尝试 [MAX_PREFETCH_ATTEMPTS] 次） */
    private fun prefetchOne(url: String): Boolean {
        var attempt = 0
        while (attempt < MAX_PREFETCH_ATTEMPTS) {
            attempt++
            val tmp = createTempFile()
            try {
                val request = Request.Builder().url(url).get().build()
                NetworkModule.client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    val body = response.body ?: throw IOException("响应体为空")
                    body.byteStream().use { input ->
                        tmp.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                when (val kind = detectKind(tmp)) {
                    ImgKind.INVALID -> throw IOException("下载内容不是有效图片")
                    ImgKind.TRANSCODE -> {
                        // webp/gif 等：解码后转码为 JPEG，保证后缀真实
                        val bmp = decodeFile(tmp, 4096) ?: throw IOException("图片转码失败")
                        val trans = createTempFile()
                        trans.outputStream().use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 90, out) }
                        tmp.delete()
                        return backend.importTempFile(trans, "w_${System.currentTimeMillis()}.jpg", intoApplied = false) != null
                    }
                    else -> {
                        val suffix = if (kind == ImgKind.PNG) "png" else "jpg"
                        return backend.importTempFile(tmp, "w_${System.currentTimeMillis()}.$suffix", intoApplied = false) != null
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "预取第 $attempt 次失败：${e.message}")
                tmp.delete()
                Thread.sleep(1000L * attempt)
            }
        }
        return false
    }

    /** 判断下载内容的真实格式；无效图片返回 [ImgKind.INVALID] */
    private fun detectKind(file: File): ImgKind {
        val header = ByteArray(8)
        file.inputStream().use { ins -> if (ins.read(header) < 4) return ImgKind.INVALID }
        val isPng = header[0] == 0x89.toByte() && header[1] == 0x50.toByte() &&
            header[2] == 0x4E.toByte() && header[3] == 0x47.toByte()
        val isJpg = header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte()
        if (isPng) return if (hasValidBounds(file)) ImgKind.PNG else ImgKind.INVALID
        if (isJpg) return if (hasValidBounds(file)) ImgKind.JPG else ImgKind.INVALID
        // 其他格式（webp/gif 等）：能解码则转码，否则视为无效
        return if (hasValidBounds(file)) ImgKind.TRANSCODE else ImgKind.INVALID
    }

    private fun hasValidBounds(file: File): Boolean {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        return opts.outWidth > 0 && opts.outHeight > 0
    }

    private enum class ImgKind { JPG, PNG, TRANSCODE, INVALID }

    companion object {
        private const val TAG = "WallpaperCache"
        private const val DIR_CACHE = "cache"
        private const val DIR_APPLIED = "applied"

        /** 预取单张最多尝试次数 */
        private const val MAX_PREFETCH_ATTEMPTS = 2

        /** 已应用历史最多保留文件数 */
        const val MAX_APPLIED = 100

        /** 后台预取协程作用域（进程级单例，不随页面销毁） */
        private val prefetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** 后台预取运行标志：同一时间只允许一个预取任务，防连点/多入口重复下载 */
        private val prefetchRunning = AtomicBoolean(false)

        /** 取缓存短锁：只保护"取最旧一张"的原子性，几毫秒即释放，绝不阻塞换壁纸 */
        private val takeLock = Any()

        /** 预取长锁：串行化网络预取（下载慢），与取缓存锁相互独立 */
        private val fullLock = Any()

        /**
         * 按目标最大边长采样解码本地图片文件，防止 OOM。
         * @param maxDimension 采样后长边上限；传 0 时仅读取尺寸信息
         */
        fun decodeFile(file: File, maxDimension: Int = 4096): Bitmap? {
            val opts1 = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts1)
            if (opts1.outWidth <= 0 || opts1.outHeight <= 0) return null

            var sample = 1
            if (maxDimension > 0) {
                val longest = maxOf(opts1.outWidth, opts1.outHeight)
                while (longest / sample > maxDimension) sample *= 2
            }
            val opts2 = BitmapFactory.Options().apply { inSampleSize = sample }
            return BitmapFactory.decodeFile(file.absolutePath, opts2)
        }
    }

    // ==================== 存储后端（File / SAF 双实现） ====================

    /** 后端抽象：屏蔽默认目录（File）与自定义目录（SAF content://）的差异 */
    private interface Backend {
        /** 缓存队列中的壁纸（升序） */
        fun cachedItems(): List<DocumentFile>

        /** 已应用历史壁纸（最新在前） */
        fun appliedItems(): List<DocumentFile>

        /** 取最旧一张并移入历史 */
        fun takeForApply(): DocumentFile?

        /** 将临时文件导入 cache / applied 目录，返回正式条目；失败返回 null */
        fun importTempFile(temp: File, name: String, intoApplied: Boolean): DocumentFile?

        /** 解码单张壁纸（采样，防 OOM） */
        fun decode(doc: DocumentFile, maxDimension: Int): Bitmap?

        /** 打开壁纸原始字节流（供导出原图到相册） */
        fun openStream(doc: DocumentFile): java.io.InputStream
    }

    /** 默认目录后端：直接 File 操作（最快，rename 移动） */
    private class FileBackend(private val appContext: Context, root: DocumentFile) : Backend {
        private val rootFile = File(root.uri.path!!)

        private fun cacheDir(): File = File(rootFile, DIR_CACHE).apply { mkdirs() }
        private fun appliedDir(): File = File(rootFile, DIR_APPLIED).apply { mkdirs() }

        override fun cachedItems(): List<DocumentFile> =
            cacheDir().listFiles { f -> f.isFile }?.sortedBy { it.name }
                ?.map { DocumentFile.fromFile(it) } ?: emptyList()

        override fun appliedItems(): List<DocumentFile> =
            appliedDir().listFiles { f -> f.isFile }?.sortedByDescending { it.name }
                ?.map { DocumentFile.fromFile(it) } ?: emptyList()

        override fun takeForApply(): DocumentFile? {
            val next = cachedItems().firstOrNull() ?: return null
            val src = File(next.uri.path!!)
            val dst = File(appliedDir(), src.name)
            if (src.renameTo(dst)) {
                trimApplied()
                return DocumentFile.fromFile(dst)
            }
            return null
        }

        override fun importTempFile(temp: File, name: String, intoApplied: Boolean): DocumentFile? {
            val dir = if (intoApplied) appliedDir() else cacheDir()
            val dst = File(dir, name)
            if (!temp.renameTo(dst)) {
                // rename 偶发失败时回退为复制 + 删除
                runCatching { temp.copyTo(dst, overwrite = true) }
                temp.delete()
            }
            trimApplied()
            return DocumentFile.fromFile(dst).takeIf { it.isFile }
        }

        override fun decode(doc: DocumentFile, maxDimension: Int): Bitmap? =
            decodeFile(File(doc.uri.path!!), maxDimension)

        override fun openStream(doc: DocumentFile): java.io.InputStream =
            File(doc.uri.path!!).inputStream()

        private fun trimApplied() {
            val files = appliedDir().listFiles { f -> f.isFile } ?: return
            if (files.size <= MAX_APPLIED) return
            files.sortedBy { it.name }
                .take(files.size - MAX_APPLIED)
                .forEach { it.delete() }
        }
    }

    /** 自定义目录后端：SAF DocumentFile 操作（复制 + 删除实现移动） */
    private class SafBackend(private val appContext: Context, private val root: DocumentFile) : Backend {
        private fun cacheDir(): DocumentFile =
            root.findFile(DIR_CACHE) ?: root.createDirectory(DIR_CACHE)!!

        private fun appliedDir(): DocumentFile =
            root.findFile(DIR_APPLIED) ?: root.createDirectory(DIR_APPLIED)!!

        override fun cachedItems(): List<DocumentFile> =
            cacheDir().listFiles().sortedBy { it.name }

        override fun appliedItems(): List<DocumentFile> =
            appliedDir().listFiles().sortedByDescending { it.name }

        override fun takeForApply(): DocumentFile? {
            val next = cachedItems().firstOrNull() ?: return null
            val name = next.name ?: return null
            val dir = appliedDir()
            val target = dir.findFile(name)
                ?: dir.createFile(mimeOf(name), name) ?: return null
            if (copyDoc(next, target)) {
                next.delete()
                trimApplied()
                return target
            }
            return null
        }

        override fun importTempFile(temp: File, name: String, intoApplied: Boolean): DocumentFile? {
            val dir = if (intoApplied) appliedDir() else cacheDir()
            val doc = dir.findFile(name) ?: dir.createFile(mimeOf(name), name) ?: return null
            val ok = runCatching {
                temp.inputStream().use { ins ->
                    appContext.contentResolver.openOutputStream(doc.uri)?.use { out -> ins.copyTo(out) }
                }
            }.isSuccess
            temp.delete()
            if (!ok) doc.delete()
            trimApplied()
            return doc.takeIf { it.exists() }
        }

        private fun copyDoc(src: DocumentFile, dst: DocumentFile): Boolean = runCatching {
            appContext.contentResolver.openInputStream(src.uri)?.use { ins ->
                appContext.contentResolver.openOutputStream(dst.uri)?.use { out -> ins.copyTo(out) }
            }
        }.isSuccess

        override fun decode(doc: DocumentFile, maxDimension: Int): Bitmap? {
            val opts1 = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            appContext.contentResolver.openInputStream(doc.uri)?.use {
                BitmapFactory.decodeStream(it, null, opts1)
            } ?: return null
            if (opts1.outWidth <= 0 || opts1.outHeight <= 0) return null

            var sample = 1
            if (maxDimension > 0) {
                val longest = maxOf(opts1.outWidth, opts1.outHeight)
                while (longest / sample > maxDimension) sample *= 2
            }
            val opts2 = BitmapFactory.Options().apply { inSampleSize = sample }
            return appContext.contentResolver.openInputStream(doc.uri)?.use {
                BitmapFactory.decodeStream(it, null, opts2)
            }
        }

        override fun openStream(doc: DocumentFile): java.io.InputStream =
            appContext.contentResolver.openInputStream(doc.uri)
                ?: throw java.io.IOException("无法打开：${doc.name}")

        private fun trimApplied() {
            val files = appliedItems()
            if (files.size <= MAX_APPLIED) return
            files.sortedBy { it.name }
                .take(files.size - MAX_APPLIED)
                .forEach { it.delete() }
        }
    }
}

/** 按文件名推断 MIME（SAF createFile 需要） */
private fun mimeOf(name: String): String = if (name.endsWith(".png")) "image/png" else "image/jpeg"
