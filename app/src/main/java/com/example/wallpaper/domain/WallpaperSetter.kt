package com.example.wallpaper.domain

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import java.io.IOException

/**
 * 领域层（Domain Layer）：封装修饰系统壁纸的行为。
 *
 * 职责：
 * - 将指定 Bitmap 设置为目标壁纸（主屏幕 / 锁屏 / 主屏+锁屏）
 * - 按 Android 版本选择正确的壁纸 API（不做任何降级，失败如实抛出）
 *
 * 说明：
 *   SET_WALLPAPER 属于普通权限，声明后即可使用，无需运行时授权。
 */
object WallpaperManagerWrapper {

    /**
     * 设置壁纸。
     *
     * @param context  上下文（建议传 Activity，避免使用 UI 上下文）
     * @param bitmap   已解码的壁纸位图
     * @param target   目标：主屏幕 / 锁屏 / 主屏+锁屏（[WallpaperTarget]）
     *
     * @throws IOException 设置失败时抛出
     */
    @Throws(IOException::class)
    fun setWallpaper(context: Context, bitmap: Bitmap, target: WallpaperTarget) {
        val wm = WallpaperManager.getInstance(context.applicationContext)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Android 7.0 (API 24) 起支持单独设置系统/锁屏壁纸。
            // 使用带 which 参数的高层 API，允许壁纸在系统解锁前即可生效（allowBackup=true）。
            // BOTH 模式直接传入组合 flag（FLAG_SYSTEM or FLAG_LOCK）同时设置两处。
            // 不做任何降级：设置失败会如实抛出，由上层提示用户。
            wm.setBitmap(bitmap, null, true, target.flag)
        } else {
            // Android 7.0 以下：系统仅支持同时设置所有屏幕的壁纸。
            // 使用不带 which 参数的 setBitmap（API 1+），等价于同时设置系统与锁屏。
            wm.setBitmap(bitmap)
        }
    }
}
