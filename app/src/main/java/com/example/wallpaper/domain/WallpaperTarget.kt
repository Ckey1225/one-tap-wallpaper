package com.example.wallpaper.domain

import android.app.WallpaperManager

/**
 * 换壁纸的目标类型。
 *
 * - SYSTEM：仅设置主屏幕（桌面）壁纸
 * - LOCK  ：仅设置锁屏壁纸
 * - BOTH  ：主屏幕 + 锁屏一起设置
 *
 * 对应 Android 壁纸标志：
 * - WallpaperManager.FLAG_SYSTEM
 * - WallpaperManager.FLAG_LOCK
 */
enum class WallpaperTarget(val displayName: String, val flag: Int) {
    SYSTEM("主屏幕", WallpaperManager.FLAG_SYSTEM),
    LOCK("锁屏", WallpaperManager.FLAG_LOCK),
    BOTH("主屏+锁屏", WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
}
