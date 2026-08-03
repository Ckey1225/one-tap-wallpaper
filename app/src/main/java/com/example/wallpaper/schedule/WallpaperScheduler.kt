package com.example.wallpaper.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * 定时调度器（Schedule Layer）：基于 AlarmManager 的定时换壁纸。
 *
 * 说明：
 * - 使用 setExactAndAllowWhileIdle 尽量准点执行（同时唤醒深度休眠设备）；
 * - Android 12+（API 31+）若未授予 SCHEDULE_EXACT_ALARM 权限会抛 SecurityException，
 *   此时自动降级为宽松调度（setAndAllowWhileIdle），保证功能可用。
 * - 同一 PendingIntent（相同 requestCode + 组件）重复调度会覆盖旧闹钟。
 */
object WallpaperScheduler {

    private const val REQUEST_CODE = 1001

    /**
     * 安排下一次定时换壁纸。
     *
     * @param intervalMs 距下次执行的间隔（毫秒）
     */
    fun schedule(context: Context, intervalMs: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context)
        val triggerAt = System.currentTimeMillis() + intervalMs
        try {
            // 精准 + 免打扰深度休眠，尽量按点执行
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } catch (e: SecurityException) {
            // 无 SCHEDULE_EXACT_ALARM 权限时降级为宽松调度
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    /** 取消定时换壁纸 */
    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context))
    }

    /** 是否支持精准闹钟调度（Android 12+ 需权限，低版本恒为 true） */
    fun canScheduleExactAlarms(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, QuickChangeAlarmReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
