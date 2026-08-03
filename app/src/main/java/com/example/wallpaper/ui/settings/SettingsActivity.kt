package com.example.wallpaper.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.example.wallpaper.ui.theme.WallpaperTheme

/**
 * 设置界面（可见 UI）。
 *
 * 入口：长按应用图标 -> 「设置」磁贴。
 * 全部配置集中于此：图片 API 地址、壁纸目标（主屏/锁屏/主屏+锁屏）、
 * 壁纸缓存（预取数量 / 缓存目录 / 手动补充）、定时切换、后台保护、
 * 壁纸记录（保留条数 / 查看）、外链。
 */
class SettingsActivity : ComponentActivity() {

    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            WallpaperTheme {
                // SAF 选择自定义缓存目录（授予持久读/写权限）
                val dirPicker = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocumentTree()
                ) { uri ->
                    if (uri != null) {
                        runCatching {
                            contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            )
                        }
                        viewModel.setCacheDir(uri.toString())
                    }
                }
                SettingsScreen(
                    context = this,
                    vm = viewModel,
                    onPickCacheDir = { dirPicker.launch(null) }
                )
            }
        }
    }
}
