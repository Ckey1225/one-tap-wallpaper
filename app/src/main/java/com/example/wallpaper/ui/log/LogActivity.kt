package com.example.wallpaper.ui.log

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.wallpaper.data.PreferenceStore
import com.example.wallpaper.data.WallpaperCache
import com.example.wallpaper.ui.theme.WallpaperTheme

/**
 * 壁纸记录（独立页，保留兼容入口）。
 *
 * 与设置页「记录」标签内联列表同一套组件 [WallpaperGallery]：
 * - 大图画廊展示已应用过的壁纸（最新在前）；
 * - **长按任一张**：把原图保存到系统相册。
 * - 保留条数由设置页「设置」标签统一管理。
 */
class LogActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WallpaperTheme {
                RecordScreen(context = this)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordScreen(context: ComponentActivity) {
    val appContext = context.applicationContext
    val cache = remember { WallpaperCache(appContext) }
    val prefs = remember { PreferenceStore(appContext) }
    val maxCount = prefs.logMaxCount
    val files = remember(maxCount) { cache.appliedItems().take(maxCount) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("壁纸记录") },
                navigationIcon = {
                    IconButton(onClick = { context.onBackPressedDispatcher.onBackPressed() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            WallpaperGallery(
                files = files,
                cache = cache,
                headerText = "共 ${files.size} 张已用过的壁纸 · 长按保存到相册",
                emptyText = "还没有换过壁纸",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}