package com.example.wallpaper.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.wallpaper.data.PreferenceStore
import com.example.wallpaper.domain.ChangeEntry
import com.example.wallpaper.shortcut.QuickWallpaperService

/**
 * 定时换壁纸闹钟接收器。
 *
 * 由 [WallpaperScheduler] 触发：
 * 1. 启动 [QuickWallpaperService]（Receiver 前台运行豁免期内允许启动 Service），
 *    由它调用唯一换壁纸方法静默执行"拉新图 + 换壁纸"；
 * 2. 若定时开关仍开启，继续调度下一轮（保持定时链）。
 */
class QuickChangeAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // 静默换壁纸（Service 内部读取当前配置并调用统一换壁纸方法）
        val serviceIntent = Intent(context, QuickWallpaperService::class.java)
            .putExtra(QuickWallpaperService.EXTRA_ENTRY, ChangeEntry.SCHEDULE.name)
        context.startService(serviceIntent)

        // 调度下一轮（保持定时链）
        val prefs = PreferenceStore(context)
        if (prefs.scheduleEnabled) {
            WallpaperScheduler.schedule(context, prefs.scheduleIntervalMs)
        }
    }
}
