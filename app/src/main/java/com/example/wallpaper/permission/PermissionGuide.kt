package com.example.wallpaper.permission

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * 后台保护 / 自启动权限引导（Permission Guide）。
 *
 * 定时换壁纸依赖系统在后台准点唤醒应用，但以下限制会拦截：
 * 1. 电池优化（Doze）：需要把应用加入"不优化电池"白名单；
 * 2. 国产 ROM 自启动管理（MIUI/EMUI/ColorOS/OriginOS 等）：需要允许应用自启动/后台运行。
 *
 * 这些属于系统设置而非运行时权限，只能通过 Intent 引导用户手动开启。
 */
object PermissionGuide {

    /** 是否已豁免电池优化 */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** 引导用户把本应用加入电池优化白名单（部分系统会直接弹出开关） */
    fun requestBatteryOptimizationExemption(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            // 个别 ROM 不支持该动作时回退到应用详情页
            openAppDetails(context)
        }
    }

    /**
     * 跳转各厂商"自启动管理"设置页；无匹配项时回退应用详情页。
     * 按常见国产 ROM 依次探测组件是否可用。
     */
    fun openAutoStartSettings(context: Context) {
        val candidates = listOf(
            // MIUI / HyperOS
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            // 华为 / 荣耀 EMUI
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            // OPPO / 一加 ColorOS
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            // vivo / iQOO OriginOS
            ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            // 三星
            ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
            // 魅族
            ComponentName("com.meizu.safe", "com.meizu.safe.security.SHOW_APPSEC")
        )

        for (component in candidates) {
            val intent = Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) != null) {
                runCatching { context.startActivity(intent) }.onSuccess { return }
            }
        }
        openAppDetails(context)
    }

    /** 引导用户前往系统"精确闹钟"授权页（Android 12+ 定时准点需要） */
    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val intent = Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                openAppDetails(context)
            }
        } else {
            openAppDetails(context)
        }
    }

    /** 回退入口：应用详情页 */
    private fun openAppDetails(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            // 已尽力引导，忽略
        }
    }
}
