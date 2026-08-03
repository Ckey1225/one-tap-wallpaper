package com.example.wallpaper.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * 数据层（Data Layer）：壁纸资源仓库。
 *
 * 职责：
 * - 从自定义图片 API 地址异步下载图片字节
 * - 内置【加载失败重试机制】（指数退避重试）
 * - 校验下载内容是否为有效图片，并解码为 Bitmap
 *
 * 说明：API 地址预期**直接返回图片字节流**（如 https://t.alcy.cc/pc/ 重定向到图片）。
 *      若返回的是 JSON/HTML 列表页，请更换为直接返回图片数据的接口。
 */
class WallpaperRepository(private val context: Context) {

    companion object {
        /** 默认图片 API 地址（留空：需在设置页自行填写，开箱无内置接口） */
        const val DEFAULT_IMAGE_URL = ""

        private const val TAG = "WallpaperRepo"

        /** 最大下载尝试次数 */
        private const val MAX_ATTEMPTS = 3

        /** 首次重试退避延迟（毫秒），之后指数翻倍 */
        private const val BASE_RETRY_DELAY_MS = 1500L

        /** 下载成功后临时图片文件的生命周期（毫秒），过期在下次下载前清理 */
        private const val CACHE_TTL_MS = 30 * 60 * 1000L
    }

    /**
     * 从 [imageUrl] 下载图片并解码为 Bitmap。
     *
     * @param imageUrl 图片直链
     * @return 解码后的 Bitmap
     * @throws IOException 多次重试仍失败时抛出
     */
    suspend fun fetchImage(imageUrl: String): Bitmap = withContext(Dispatchers.IO) {
        val imageFile = downloadBytes(imageUrl)
        // 越界缩放采样，避免一次性载入超大位图导致 OOM
        val bitmap = decodeSampledBitmap(imageFile)
        if (bitmap == null) {
            throw IOException("下载内容不是有效图片：${imageFile.absoluteFile}")
        }
        // 解码成功即删除临时文件，释放空间（Bitmap 已驻留内存）
        imageFile.delete()
        bitmap
    }

    /** 下载图片字节到缓存目录，失败自动指数退避重试 */
    private fun downloadBytes(imageUrl: String): File {
        val targetFile = File(context.cacheDir, "wallpaper_" + System.currentTimeMillis() + ".img")

        // 清理过期的旧临时壁纸文件，避免缓存膨胀
        clearExpiredCacheFiles()

        var attempt = 0
        while (true) {
            attempt++
            try {
                val request = Request.Builder().url(imageUrl).get().build()
                NetworkModule.client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP 错误码：${response.code}")
                    }
                    response.body?.byteStream()?.use { input ->
                        targetFile.outputStream().use { output -> input.copyTo(output) }
                    } ?: throw IOException("响应体为空")
                }
                // 下载完成即返回
                return targetFile
            } catch (e: Exception) {
                Log.w(TAG, "第 $attempt 次下载失败：${e.message}")
                if (attempt >= MAX_ATTEMPTS) {
                    throw IOException("图片下载失败，已重试 $MAX_ATTEMPTS 次：${e.message}", e)
                }
                // 指数退避后重试，减小瞬时网络抖动影响
                Thread.sleep(BASE_RETRY_DELAY_MS * (1L shl (attempt - 1)))
            }
        }
    }

    /** 按目标尺寸采样解码，防止 OOM */
    private fun decodeSampledBitmap(file: File): Bitmap? {
        // 第一步：只读尺寸
        val opts1 = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts1)
        if (opts1.outWidth <= 0 || opts1.outHeight <= 0) return null

        // 第二步：计算采样率（把长边限制在屏幕级别，略超2K即可）
        val maxDimension = 4096
        var sample = 1
        val longest = maxOf(opts1.outWidth, opts1.outHeight)
        while (longest / sample > maxDimension) sample *= 2

        val opts2 = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.absolutePath, opts2)
    }

    /** 清理超过 TTL 的临时图片 */
    private fun clearExpiredCacheFiles() {
        val now = System.currentTimeMillis()
        context.cacheDir.listFiles { f -> f.name.startsWith("wallpaper_") }?.forEach { f ->
            if (now - f.lastModified() > CACHE_TTL_MS) f.delete()
        }
    }
}