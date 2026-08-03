package com.example.wallpaper

import android.app.Application
import android.util.Log
import com.example.wallpaper.data.WallpaperCache
import com.example.wallpaper.shortcut.ShortcutHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 应用 Application 类。
 *
 * 启动时：
 * 1. 注册长按图标磁贴（「设置」入口）；
 * 2. 后台预取壁纸缓存（保证第一次点击图标换壁纸时也有缓存可秒换）。
 *
 * 注意：磁贴注册 / 预取都属于后台辅助行为，任何异常都必须兜底，
 * 绝不能让应用一启动就崩溃。
 */
class WallpaperApp : Application() {

    /** 应用级后台协程作用域（用于预取壁纸缓存等非关键任务） */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 注册"设置"磁贴：用户长按桌面图标即可进入设置界面。
        // 失败只影响磁贴展示，不影响应用任何功能，务必兜底。
        try {
            ShortcutHelper.pushSettingsShortcut(this)
        } catch (e: Exception) {
            Log.w(TAG, "设置磁贴注册失败（不影响换壁纸功能）：${e.message}")
        }

        // 后台预取壁纸缓存（FIFO 队列，默认 3 张），
        // 让点击图标 / 外链换壁纸时能直接命中本地缓存、瞬间完成。
        appScope.launch {
            runCatching { WallpaperCache(this@WallpaperApp).ensureFull() }
                .onFailure { Log.w(TAG, "启动预取缓存失败（下次换壁纸后自动重试）：${it.message}") }
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        appScope.cancel()
    }

    companion object {
        /** 全局应用实例，便于在非 Activity 单例中获取上下文 */
        lateinit var instance: WallpaperApp
            private set

        private const val TAG = "WallpaperApp"
    }
}
