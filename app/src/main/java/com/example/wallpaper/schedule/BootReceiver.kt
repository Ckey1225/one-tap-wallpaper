package com.example.wallpaper.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.wallpaper.data.PreferenceStore

/**
 * 开机恢复接收器：设备重启后自动恢复定时换壁纸任务。
 * 需要 RECEIVE_BOOT_COMPLETED 权限。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = PreferenceStore(context)
        if (prefs.scheduleEnabled) {
            WallpaperScheduler.schedule(context, prefs.scheduleIntervalMs)
        }
    }
}
