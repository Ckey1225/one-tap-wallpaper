package com.example.wallpaper.shortcut

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.wallpaper.MainActivity
import com.example.wallpaper.R
import com.example.wallpaper.ui.settings.SettingsActivity

/**
 * 应用磁贴（App Shortcut）助手（Shortcut Layer）。
 *
 * 入口约定：
 * - 点击应用图标：直接静默换壁纸（主入口，见 [com.example.wallpaper.MainActivity]）
 * - 长按应用图标：提供两个磁贴
 *   - 「设置」：进入设置界面
 *   - 「切换上一张」：回到上一张用过的壁纸（静默）
 *
 * 磁贴由应用启动时注册（见 [com.example.wallpaper.WallpaperApp]）。
 */
object ShortcutHelper {

    private const val TAG = "ShortcutHelper"

    /** 设置磁贴唯一 ID（同名重复注册即覆盖更新） */
    const val SETTINGS_SHORTCUT_ID = "settings"

    /** "切换上一张壁纸"磁贴唯一 ID */
    const val PREVIOUS_SHORTCUT_ID = "previous"

    /** 注册（或更新）长按应用图标后的磁贴：「设置」+「切换上一张」 */
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
            val settingsShortcut = builder
                .setShortLabel(context.getString(R.string.shortcut_settings_name))
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_launcher))
                .setIntent(settingsIntent)
                .build()

            // "切换上一张壁纸"磁贴：跳转透明跳板，静默应用历史上一张
            val previousIntent = Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_PREVIOUS, true)
            val previousBuilder = if (launcherComponent != null) {
                ShortcutInfoCompat.Builder(context, PREVIOUS_SHORTCUT_ID)
                    .setActivity(launcherComponent)
            } else {
                ShortcutInfoCompat.Builder(context, PREVIOUS_SHORTCUT_ID)
            }
            val previousShortcut = previousBuilder
                .setShortLabel(context.getString(R.string.shortcut_previous_name))
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_launcher))
                .setIntent(previousIntent)
                .build()

            // 先移除所有旧动态磁贴，再设置唯一的两个磁贴
            try {
                ShortcutManagerCompat.removeAllDynamicShortcuts(context)
            } catch (_: Exception) {
                // 无动态磁贴时可能抛异常，忽略
            }

            val success = ShortcutManagerCompat.setDynamicShortcuts(
                context, listOf(previousShortcut, settingsShortcut)
            )
            android.util.Log.i(TAG, "磁贴注册结果: $success")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "磁贴注册失败：${e.message}", e)
        }
    }
}
