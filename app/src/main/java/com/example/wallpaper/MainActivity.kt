package com.example.wallpaper

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.example.wallpaper.domain.ChangeEntry
import com.example.wallpaper.shortcut.QuickWallpaperService

/**
 * 主入口（透明不可见跳板）。
 *
 * 入口约定：
 * - 点击应用图标：直接静默换壁纸（无任何窗口 / 提示 / 黑框）
 * - 浏览器访问外链 wallpaper://change：同样静默换壁纸
 *
 * 实现：
 * - 全透明主题 + 禁用预览窗口，界面对用户完全不可见；
 * - 不调用 setContentView，onCreate 立即启动后台 [QuickWallpaperService]
 *   （前台 Activity 启动 Service 合法，不受后台限制），随后毫秒级 finish；
 * - 换壁纸结果不在此展示：失败由 Service 发通知，点击通知进入日志界面查看原因。
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        triggerSilentChange(intent)
    }

    /** singleTop：实例已存在时再次唤起（重复点击图标 / 外链）走这里 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        triggerSilentChange(intent)
    }

    private fun triggerSilentChange(source: Intent?) {
        try {
            // 区分触发来源：外链（wallpaper://change）还是直接点击图标，用于日志
            val data = source?.data
            val isDeepLink = data?.scheme == "wallpaper" && data.host == "change"
            val entry = if (isDeepLink) ChangeEntry.DEEP_LINK else ChangeEntry.ICON

            val service = Intent(this, QuickWallpaperService::class.java)
                .putExtra(QuickWallpaperService.EXTRA_ENTRY, entry.name)
            startService(service)
        } catch (e: Exception) {
            // 静默场景不展示任何提示，仅记录日志
            Log.w(TAG, "静默换壁纸启动失败：${e.message}")
        } finally {
            finish()
            // 关闭进入/退出动画，避免窗口闪动
            overridePendingTransition(0, 0)
        }
    }

    private companion object {
        private const val TAG = "MainActivity"
    }
}
