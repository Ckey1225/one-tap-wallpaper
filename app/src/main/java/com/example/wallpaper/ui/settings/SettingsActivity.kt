package com.example.wallpaper.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.wallpaper.ui.theme.WallpaperTheme

/**
 * 设置界面（可见 UI）。
 *
 * 入口：长按应用图标 -> 「设置」磁贴。
 * 全部配置集中于此：图片 API 地址、壁纸目标（主屏/锁屏/主屏+锁屏）、
 * 壁纸缓存（数量状态 / 手动补充 / 查看缓存壁纸）、定时切换、后台保护、
 * 壁纸记录（查看 / 保留条数）。
 */
class SettingsActivity : ComponentActivity() {

    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            WallpaperTheme {
                SettingsScreen(
                    context = this,
                    vm = viewModel
                )
            }
        }
    }
}
