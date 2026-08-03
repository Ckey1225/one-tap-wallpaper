package com.example.wallpaper.shortcut

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.wallpaper.R
import com.example.wallpaper.ui.settings.SettingsActivity

/**
 * 应用磁贴（App Shortcut）助手（Shortcut Layer）。
 *
 * 入口约定：
 * - 点击应用图标：直接静默换壁纸（主入口，见 [com.example.wallpaper.MainActivity]）
 * - 长按应用图标：只提供一个「设置」磁贴，点击进入设置界面
 *
 * 设置磁贴由应用启动时注册（见 [com.example.wallpaper.WallpaperApp]）。
 */
object ShortcutHelper {

    private const val TAG = "ShortcutHelper"

    /** 外链一键换壁纸：浏览器访问该地址即静默更换壁纸（对应 Manifest 中 deep link） */
    const val DEEP_LINK = "wallpaper://change"

    /** 设置磁贴唯一 ID（同名重复注册即覆盖更新） */
    const val SETTINGS_SHORTCUT_ID = "settings"

    /** 注册（或更新）长按应用图标后出现的「设置」磁贴 */
    fun pushSettingsShortcut(context: Context) {
        try {
            // 先获取 launcher activity，确保磁贴关联到正确的 Activity
            val launcherIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val launcherComponent = launcherIntent?.resolveActivity(context.packageManager)

            val builder = if (launcherComponent != null) {
                ShortcutInfoCompat.Builder(context, SETTINGS_SHORTCUT_ID)
                    .setActivity(launcherComponent)
            } else {
                ShortcutInfoCompat.Builder(context, SETTINGS_SHORTCUT_ID)
            }

            // 必须设置 ACTION_VIEW，否则系统会拒绝添加磁贴
            val settingsIntent = Intent(context, SettingsActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

            val shortcut = builder
                .setShortLabel(context.getString(R.string.shortcut_settings_name))
                .setLongLabel(context.getString(R.string.shortcut_settings_long_name))
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_launcher))
                .setIntent(settingsIntent)
                .build()

            // 先移除所有旧动态磁贴，再设置唯一的「设置」磁贴
            try {
                ShortcutManagerCompat.removeAllDynamicShortcuts(context)
            } catch (_: Exception) {
                // 无动态磁贴时可能抛异常，忽略
            }

            val success = ShortcutManagerCompat.setDynamicShortcuts(context, listOf(shortcut))
            android.util.Log.i(TAG, "磁贴注册结果: $success")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "磁贴注册失败：${e.message}", e)
        }
    }
}
