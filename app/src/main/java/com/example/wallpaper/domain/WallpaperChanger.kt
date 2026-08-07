package com.example.wallpaper.domain

import android.content.Context
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.example.wallpaper.data.PreferenceStore
import com.example.wallpaper.data.WallpaperCache
import com.example.wallpaper.data.WallpaperRepository
import com.example.wallpaper.schedule.WallpaperScheduler
import java.io.IOException

/** 换壁纸触发来源（写入记录，便于排查） */
enum class ChangeEntry(val label: String) {
    /** 点击应用图标进入 */
    ICON("点击图标"),

    /** 浏览器访问外链 wallpaper://change */
    DEEP_LINK("外链"),

    /** 定时任务触发 */
    SCHEDULE("定时"),

    /** 设置页手动点击"立即换壁纸" */
    MANUAL("设置页"),

    /** 长按图标磁贴"切换上一张" */
    PREVIOUS("上一张"),

    /** 缓存/记录页单击某张图，直接设为壁纸 */
    GALLERY("单点")
}

/**
 * 领域层（Domain Layer）：统一换壁纸方法（大一统）。
 *
 * 整个应用只有这一种换壁纸方式：
 *   （优先本地缓存 ->）网络拉取图片 -> 解码 -> 应用到目标壁纸（主屏 / 锁屏 / 主屏+锁屏）
 *
 * 所有入口（点击图标、外链、定时任务、设置页手动）都只调用 [change] 这一个方法：
 * 1. 读取用户当前配置（API 地址 + 目标壁纸）
 * 2. 【缓存优先】从 FIFO 缓存队列取最早一张应用（秒换，零网络等待）；
 *    缓存耗尽时回退实时下载兜底，保证任何时候都能换壁纸
 * 3. 应用到壁纸
 * 4. 无论成功失败都写入换壁纸记录（供"壁纸记录"页展示）
 * 5. 成功后补充预取一张到缓存队列尾部，并幂等续排定时闹钟
 */
object WallpaperChanger {

    /** 换壁纸结果 */
    data class ChangeResult(val success: Boolean, val message: String)

    /**
     * 执行一次换壁纸（唯一核心方法）。
     *
     * @param context 上下文（内部统一使用 applicationContext）
     * @param entry   触发来源（用于记录）
     * @return 成功与否 + 结果信息（失败时为失败原因）
     */
    suspend fun change(context: Context, entry: ChangeEntry): ChangeResult {
        val appContext = context.applicationContext
        val prefs = PreferenceStore(appContext)
        val target = prefs.target

        var result: ChangeResult
        // 成功应用的那张壁纸文件名（写进记录，供"壁纸记录"页展示缩略图）
        var appliedName = ""
        val cache = WallpaperCache(appContext)
        try {
            // 1. 优先从本地缓存队列取最旧一张（序号 1）并移入历史
            val cached = cache.takeForApply()
            val bitmap = if (cached != null) {
                appliedName = cached.name ?: ""
                cache.decodeImage(cached)
                    ?: throw IOException("缓存壁纸解码失败：$appliedName")
            } else {
                // 2. 缓存耗尽兜底：实时下载一张并保存进历史（供缩略图）
                WallpaperRepository(appContext).fetchImage(prefs.imageUrl).also { bmp ->
                    appliedName = cache.saveApplied(bmp)
                }
            }

            // 3. 应用到目标壁纸（主屏 / 锁屏 / 主屏+锁屏）
            WallpaperManagerWrapper.setWallpaper(appContext, bitmap, target)
            result = ChangeResult(success = true, message = "设置成功（${target.displayName}）")
            Log.i(TAG, "换壁纸成功：${entry.name} / ${target.displayName} / $appliedName")
        } catch (t: Throwable) {
            // 捕获 Throwable（含 OOM 等 Error）：大图解码内存不足、系统壁纸服务异常等
            // 都应转为失败结果记入记录，而不是让整个进程崩溃
            result = ChangeResult(success = false, message = t.message ?: "未知错误")
            Log.w(TAG, "换壁纸失败：${entry.name} / ${result.message}")
        }

        // 无论成败都写入换壁纸记录
        prefs.addLog(entry.name, result.success, result.message, appliedName)

        if (result.success) {
            // 定时服务保障：成功后若开关仍开启则幂等续排闹钟
            if (prefs.scheduleEnabled) {
                WallpaperScheduler.schedule(appContext, prefs.scheduleIntervalMs)
            }
            // 补充预取：非阻塞（fire-and-forget），立即返回不拖慢换壁纸。
            // 后台并行补满缓存到队尾，失败静默忽略、下次时机自动重试。
            cache.ensureFullAsync()
        }
        return result
    }

    /**
     * 切换上一张壁纸（长按图标磁贴入口）。
     *
     * 从已应用历史中取"当前壁纸的上一张"（历史最新在前，[0]=当前、[1]=上一张），
     * 重新应用到目标壁纸；不改变缓存队列与历史文件本身，仅写入一条记录。
     */
    /**
     * 从缓存 / 历史记录中单击某张壁纸直接应用。
     *
     * 适用于设置页「缓存」「记录」两个标签，用户触点图片即换壁纸。
     * 直接从本地文件解码并设置，不涉及网络 / API 地址，不改变缓存队列。
     *
     * @param context 上下文（内部统一使用 applicationContext）
     * @param doc     已存储的壁纸文件（缓存 / 已应用均可）
     * @return 换壁纸结果
     */
    suspend fun changeFromFile(context: Context, doc: DocumentFile): ChangeResult {
        val appContext = context.applicationContext
        val prefs = PreferenceStore(appContext)
        val target = prefs.target
        val cache = WallpaperCache(appContext)

        return try {
            val bitmap = cache.decodeImage(doc)
                ?: throw IOException("壁纸文件解码失败")
            WallpaperManagerWrapper.setWallpaper(appContext, bitmap, target)
            prefs.addLog(ChangeEntry.GALLERY.name, true,
                "已应用（${target.displayName}）", doc.name ?: "")
            if (prefs.scheduleEnabled) {
                WallpaperScheduler.schedule(appContext, prefs.scheduleIntervalMs)
            }
            ChangeResult(success = true, message = "已应用（${target.displayName}）")
        } catch (t: Throwable) {
            val result = ChangeResult(success = false, message = t.message ?: "未知错误")
            prefs.addLog(ChangeEntry.GALLERY.name, false, result.message, doc.name ?: "")
            result
        }
    }

    suspend fun changePrevious(context: Context): ChangeResult {
        val appContext = context.applicationContext
        val prefs = PreferenceStore(appContext)
        val target = prefs.target
        val cache = WallpaperCache(appContext)

        // 历史最新在前：[0] 是当前壁纸，上一张是 [1]
        val prev = cache.appliedItems().drop(1).firstOrNull()
        if (prev == null) {
            val result = ChangeResult(success = false, message = "没有上一张壁纸")
            prefs.addLog(ChangeEntry.PREVIOUS.name, false, result.message, "")
            return result
        }

        return try {
            val bitmap = cache.decodeImage(prev) ?: throw IOException("上一张壁纸解码失败")
            WallpaperManagerWrapper.setWallpaper(appContext, bitmap, target)
            prefs.addLog(ChangeEntry.PREVIOUS.name, true, "切换上一张（${target.displayName}）", prev.name ?: "")
            if (prefs.scheduleEnabled) {
                WallpaperScheduler.schedule(appContext, prefs.scheduleIntervalMs)
            }
            ChangeResult(success = true, message = "已切换上一张（${target.displayName}）")
        } catch (t: Throwable) {
            val result = ChangeResult(success = false, message = t.message ?: "未知错误")
            prefs.addLog(ChangeEntry.PREVIOUS.name, false, result.message, prev.name ?: "")
            result
        }
    }

    private const val TAG = "WallpaperChanger"
}
