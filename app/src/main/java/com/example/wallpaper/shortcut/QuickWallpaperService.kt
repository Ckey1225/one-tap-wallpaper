package com.example.wallpaper.shortcut

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.example.wallpaper.domain.ChangeEntry
import com.example.wallpaper.domain.WallpaperChanger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 静默换壁纸后台服务（Silent Wallpaper Service）。
 *
 * 所有静默入口（点击图标 / 外链 / 定时任务）统一启动本 Service，
 * 由它调用唯一的换壁纸方法 [WallpaperChanger.change] 真正执行：
 * 下载网络图片 -> 解码 -> 设置为目标壁纸。
 *
 * 特性：
 * - 全程在协程 + IO 线程执行，不阻塞、不 ANR；
 * - 不展示任何窗口、通知、Toast（换壁纸本身静默）；
 * - 换壁纸结果（成功/失败）统一记入"壁纸记录"，可在设置页查看；
 * - 执行完毕后自动 stopSelf，任务不常驻后台。
 */
class QuickWallpaperService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            try {
                // 触发来源（用于日志）：未携带时按点击图标处理
                val entryName = intent?.getStringExtra(EXTRA_ENTRY)
                val entry = ChangeEntry.entries.firstOrNull { it.name == entryName } ?: ChangeEntry.ICON

                // "切换上一张"走专属方法，其余统一走常规换壁纸
                when (entry) {
                    ChangeEntry.PREVIOUS -> WallpaperChanger.changePrevious(applicationContext)
                    else -> WallpaperChanger.change(applicationContext, entry)
                }
                // 结果已写入"壁纸记录"，不弹任何通知 / Toast
            } catch (t: Throwable) {
                // 捕获所有异常与错误（含 OOM），保证静默任务绝不拖垮进程
                Log.w(TAG, "静默换壁纸任务异常：${t.message}")
            } finally {
                stopSelf(startId)
            }
        }
        // 任务式启动：进程被杀后不重建
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        /** 触发来源 Extra（ChangeEntry.name） */
        const val EXTRA_ENTRY = "extra_entry"

        private const val TAG = "QuickWallpaperService"
    }
}
