package com.example.wallpaper.data

import android.content.Context
import android.content.SharedPreferences
import com.example.wallpaper.domain.WallpaperTarget
import org.json.JSONArray
import org.json.JSONObject

/**
 * 单条换壁纸记录（时间 / 触发入口 / 成功与否 / 附加信息 / 应用成功的壁纸文件名）
 */
data class WallpaperLog(
    val time: Long,
    val entry: String,
    val success: Boolean,
    val message: String,
    /** 成功应用的壁纸文件名（用于"壁纸记录"页展示缩略图，失败时为空） */
    val wallpaper: String = ""
)

/**
 * 数据层（Data Layer）：轻量配置持久化 + 换壁纸记录。
 *
 * 使用 SharedPreferences 保存用户配置，供设置页与后台任务（定时/外链/磁贴）共享：
 * - 图片 API 地址
 * - 壁纸目标（主屏幕 / 锁屏 / 主屏+锁屏）
 * - 定时切换开关与间隔
 * - 缓存预取数量（默认 100 张，队列上限）与壁纸记录保留条数
 * - 最近换壁纸记录（成功/失败均记录，条数可配置，供"壁纸记录"页展示）
 */
class PreferenceStore(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 图片 API 地址（为空时回退默认内置接口） */
    var imageUrl: String
        get() = sp.getString(KEY_IMAGE_URL, null) ?: WallpaperRepository.DEFAULT_IMAGE_URL
        set(value) {
            sp.edit().putString(KEY_IMAGE_URL, value.trim()).apply()
        }

    /** 壁纸目标（解析失败回退主屏幕） */
    var target: WallpaperTarget
        get() = runCatching {
            WallpaperTarget.valueOf(sp.getString(KEY_TARGET, WallpaperTarget.SYSTEM.name)!!)
        }.getOrDefault(WallpaperTarget.SYSTEM)
        set(value) {
            sp.edit().putString(KEY_TARGET, value.name).apply()
        }

    /** 定时切换开关 */
    var scheduleEnabled: Boolean
        get() = sp.getBoolean(KEY_SCHEDULE_ENABLED, false)
        set(value) {
            sp.edit().putBoolean(KEY_SCHEDULE_ENABLED, value).apply()
        }

    /** 定时切换间隔（毫秒），默认 2 小时 */
    var scheduleIntervalMs: Long
        get() = sp.getLong(KEY_SCHEDULE_INTERVAL_MS, DEFAULT_INTERVAL_MS)
        set(value) {
            sp.edit().putLong(KEY_SCHEDULE_INTERVAL_MS, value).apply()
        }

    /** 缓存预取数量（2~5 张，默认 3 张） */
    var cacheSize: Int
        get() = sp.getInt(KEY_CACHE_SIZE, DEFAULT_CACHE_SIZE)
            .coerceIn(MIN_CACHE_SIZE, MAX_CACHE_SIZE)
        set(value) {
            sp.edit().putInt(KEY_CACHE_SIZE, value.coerceIn(MIN_CACHE_SIZE, MAX_CACHE_SIZE)).apply()
        }

    /** 壁纸记录最多保留条数（默认 30，可配 10/30/50/100） */
    var logMaxCount: Int
        get() = sp.getInt(KEY_LOG_MAX_COUNT, DEFAULT_LOG_MAX_COUNT)
            .coerceAtLeast(MIN_LOG_MAX_COUNT)
        set(value) {
            sp.edit().putInt(KEY_LOG_MAX_COUNT, value.coerceAtLeast(MIN_LOG_MAX_COUNT)).apply()
        }

    /** 追加一条换壁纸记录，并裁剪到最近 [logMaxCount] 条 */
    fun addLog(entry: String, success: Boolean, message: String, wallpaper: String = "") {
        val logs = parseLogs(sp.getString(KEY_LOGS, "[]") ?: "[]")
        logs.add(
            WallpaperLog(
                time = System.currentTimeMillis(),
                entry = entry,
                success = success,
                message = message,
                wallpaper = wallpaper
            )
        )
        // 只保留最近 logMaxCount 条
        val max = logMaxCount
        val trimmed = if (logs.size > max) logs.subList(logs.size - max, logs.size) else logs
        val json = JSONArray()
        trimmed.forEach { log ->
            json.put(
                JSONObject()
                    .put("t", log.time)
                    .put("e", log.entry)
                    .put("ok", log.success)
                    .put("m", log.message)
                    .put("w", log.wallpaper)
            )
        }
        sp.edit().putString(KEY_LOGS, json.toString()).apply()
    }

    /** 读取换壁纸记录（最新在前） */
    fun logs(): List<WallpaperLog> = parseLogs(sp.getString(KEY_LOGS, "[]") ?: "[]").reversed()

    private fun parseLogs(raw: String): MutableList<WallpaperLog> {
        val result = mutableListOf<WallpaperLog>()
        return try {
            val json = JSONArray(raw)
            for (i in 0 until json.length()) {
                val obj = json.getJSONObject(i)
                result.add(
                    WallpaperLog(
                        time = obj.optLong("t"),
                        entry = obj.optString("e"),
                        success = obj.optBoolean("ok"),
                        message = obj.optString("m"),
                        wallpaper = obj.optString("w")
                    )
                )
            }
            result
        } catch (e: Exception) {
            result
        }
    }

    companion object {
        private const val PREFS_NAME = "wallpaper_prefs"
        private const val KEY_IMAGE_URL = "image_url"
        private const val KEY_TARGET = "wallpaper_target"
        private const val KEY_SCHEDULE_ENABLED = "schedule_enabled"
        private const val KEY_SCHEDULE_INTERVAL_MS = "schedule_interval_ms"
        private const val KEY_CACHE_SIZE = "cache_size"
        private const val KEY_LOG_MAX_COUNT = "log_max_count"
        private const val KEY_LOGS = "wallpaper_logs"

        /** 默认定时间隔：2 小时 */
        const val DEFAULT_INTERVAL_MS = 2 * 60 * 60 * 1000L

        /** 缓存预取数量范围与默认值（用户可自定义，上限 100） */
        const val MIN_CACHE_SIZE = 2
        const val MAX_CACHE_SIZE = 100
        const val DEFAULT_CACHE_SIZE = 5

        /** 记录条数默认值与下限 */
        const val MIN_LOG_MAX_COUNT = 10
        const val DEFAULT_LOG_MAX_COUNT = 30
    }
}
