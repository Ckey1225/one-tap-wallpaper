package com.example.wallpaper.ui.log

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.example.wallpaper.data.WallpaperCache
import com.example.wallpaper.ui.theme.WallpaperTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 缓存壁纸画廊。
 *
 * 从设置页「缓存」标签的"查看缓存壁纸"进入：
 * - 大图网格展示缓存队列中的待用壁纸（序号 1 最先被应用）；
 * - **长按任一张**：把该壁纸的原图保存到系统相册，便于再次使用/分享。
 *
 * 列表上限 [WallpaperCache.MAX_APPLIED] 张。
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
private fun CacheGalleryScreen(context: Context) {
    val appContext = context.applicationContext
    val cache = remember { WallpaperCache(appContext) }
    val cacheSize = remember { com.example.wallpaper.data.PreferenceStore(appContext).cacheSize }
    val files = remember { cache.cachedItems().take(WallpaperCache.MAX_APPLIED) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("缓存壁纸") },
                navigationIcon = {
                    IconButton(onClick = { (context as? ComponentActivity)?.onBackPressedDispatcher?.onBackPressed() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (files.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "缓存中没有壁纸，换壁纸后会自动预取",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(span = { GridItemSpan(2) }) {
                Text(
                    text = "缓存中 ${files.size} 张 / 目标 $cacheSize 张 · 长按保存到相册",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
            }
            items(files) { file ->
                Column {
                    val modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(9f / 16f)
                    CacheThumbnail(
                        cache = cache,
                        doc = file,
                        modifier = modifier,
                        onLongPress = { saveToGallery(appContext, cache, file) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CacheThumbnail(
    cache: WallpaperCache,
    doc: DocumentFile,
    modifier: Modifier = Modifier,
    onLongPress: () -> Unit,
) {
    val bitmap by produceState<ImageBitmap?>(null, doc.uri) {
        value = withContext(Dispatchers.IO) {
            cache.decodeImage(doc, 512)?.asImageBitmap()
        }
    }
    Box(
        modifier = modifier
            .combinedClickable(onClick = { /* 点击暂无操作，长按保存 */ }, onLongClick = onLongPress)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = "无图",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 把壁纸原图保存到系统相册（Pictures/Wallpaper）。 */
private fun saveToGallery(context: Context, cache: WallpaperCache, doc: DocumentFile) {
    val name = doc.name ?: "wallpaper_${System.currentTimeMillis()}.jpg"
    val mime = if (name.endsWith(".png", ignoreCase = true)) "image/png" else "image/jpeg"
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, mime)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Wallpaper")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        } else {
            @Suppress("DEPRECATION")
            put(MediaStore.Images.Media.DATA, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath)
        }
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    if (uri == null) {
        Toast.makeText(context, "保存失败（无法创建媒体项）", Toast.LENGTH_SHORT).show()
        return
    }
    val ok = runCatching {
        resolver.openOutputStream(uri)?.use { out ->
            cache.open(doc)?.use { it.copyTo(out) } ?: throw java.io.IOException("无输入流")
        } ?: throw java.io.IOException("无输出流")
    }.isSuccess

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }
    if (!ok) {
        resolver.delete(uri, null, null)
        Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
    } else {
        Toast.makeText(context, "已保存到相册：$name", Toast.LENGTH_SHORT).show()
    }
}