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
 * 缓存壁纸画廊（独立页，保留兼容入口）。
 *
 * 与设置页「缓存」标签内联列表同一套组件 [WallpaperGallery]：
 * - 大图网格展示缓存队列中的待用壁纸；
 * - **长按任一张**：把该壁纸的原图保存到系统相册。
 */
class CacheGalleryActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WallpaperTheme {
                CacheGalleryScreen(context = this)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CacheGalleryScreen(context: ComponentActivity) {
    val appContext = context.applicationContext
    val cache = remember { WallpaperCache(appContext) }
    val cacheSize = remember { PreferenceStore(appContext).cacheSize }
    val files = remember { cache.cachedItems().take(WallpaperCache.MAX_APPLIED) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("缓存壁纸") },
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
                headerText = "缓存中 ${files.size} 张 / 目标 $cacheSize 张 · 长按保存到相册",
                emptyText = "缓存中没有壁纸，换壁纸后会自动预取",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}