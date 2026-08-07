package com.example.wallpaper.ui.log

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.example.wallpaper.data.WallpaperCache
import com.example.wallpaper.domain.WallpaperChanger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 壁纸大图网格（通用组件）。
 *
 * 供设置页「缓存」「记录」两个标签内联展示：
 * - 2 列大图缩略图，**长按任意一张**把原图保存到系统相册；
 * - 顶部一行说明文字（[headerText]）；
 * - 列表为空时展示 [emptyText]。
 *
 * Android 10 以下（API <29）首次保存前会自动申请 WRITE_EXTERNAL_STORAGE 权限。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WallpaperGallery(
    files: List<DocumentFile>,
    cache: WallpaperCache,
    headerText: String,
    emptyText: String,
    modifier: Modifier = Modifier,
) {
    // 保存到相册的运行时权限（API<29）；launcher 必须在组合最外层声明
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingDoc by remember { mutableStateOf<DocumentFile?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        pendingDoc?.let { if (granted) saveWallpaperToGallery(context, cache, it) }
        pendingDoc = null
    }
    val save: (DocumentFile) -> Unit = { doc ->
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingDoc = doc
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            saveWallpaperToGallery(context, cache, doc)
        }
    }

    val onClick: (DocumentFile) -> Unit = { doc ->
        scope.launch {
            Toast.makeText(context, "正在应用…", Toast.LENGTH_SHORT).show()
            val result = WallpaperChanger.changeFromFile(context, doc)
            Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
        }
    }

    if (files.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    // 根据可用宽度自适应列数：
    // - 横屏大屏（>= 900dp）：5 列
    // - 竖屏手机（≥ 600dp）：3 列
    // - 竖屏窄屏（<600dp）：2 列
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val totalWidthDp = maxWidth
        val cols = when {
            totalWidthDp >= 900.dp -> 5
            totalWidthDp >= 600.dp -> 3
            else -> 2
        }

        val contentPadding = 16.dp
        val spacing = 12.dp

        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            contentPadding = PaddingValues(contentPadding),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(span = { GridItemSpan(cols) }) {
                Text(
                    text = headerText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
            }
            items(files) { file ->
                Column {
                    WallpaperThumbnail(
                        cache = cache,
                        doc = file,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(9f / 16f),
                        onClick = { onClick(file) },
                        onLongPress = { save(file) }
                    )
                }
            }
        }
    }
}

/** 单张壁纸缩略图（采样解码防 OOM），长按触发 [onLongPress] */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WallpaperThumbnail(
    cache: WallpaperCache,
    doc: DocumentFile,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val bitmap by produceState<ImageBitmap?>(null, doc.uri) {
        value = withContext(Dispatchers.IO) {
            cache.decodeImage(doc, 512)?.asImageBitmap()
        }
    }
    Box(
        modifier = modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
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
fun saveWallpaperToGallery(context: Context, cache: WallpaperCache, doc: DocumentFile) {
    val appContext = context.applicationContext
    val name = doc.name ?: "wallpaper_${System.currentTimeMillis()}.jpg"
    val mime = if (name.endsWith(".png", ignoreCase = true)) "image/png" else "image/jpeg"
    val resolver = appContext.contentResolver
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
        Toast.makeText(appContext, "保存失败（无法创建媒体项）", Toast.LENGTH_SHORT).show()
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
        Toast.makeText(appContext, "保存失败", Toast.LENGTH_SHORT).show()
    } else {
        Toast.makeText(appContext, "已保存到相册：$name", Toast.LENGTH_SHORT).show()
    }
}