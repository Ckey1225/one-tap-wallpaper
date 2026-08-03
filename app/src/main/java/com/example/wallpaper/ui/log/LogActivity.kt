package com.example.wallpaper.ui.log

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.example.wallpaper.data.PreferenceStore
import com.example.wallpaper.data.WallpaperCache
import com.example.wallpaper.data.WallpaperLog
import com.example.wallpaper.domain.ChangeEntry
import com.example.wallpaper.ui.theme.WallpaperTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 壁纸记录界面（双 Tab）。
 *
 * - 「历史记录」：最近换壁纸结果列表，每条展示当时应用的那张壁纸缩略图（成功时）、
 *   时间、触发入口、成功/失败与附加信息（失败原因）；
 * - 「缓存壁纸」：缓存队列中待用的壁纸缩略图（序号 1 最先被应用），以及当前数量。
 *
 * 入口：设置页「查看壁纸记录」。
 */
class LogActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            WallpaperTheme {
                WallpaperRecordScreen(context = this)
            }
        }
    }
}

@Composable
private fun WallpaperRecordScreen(context: Context) {
    val appContext = context.applicationContext
    // 进入页面一次性读取（本页停留期间数据不变）
    val cache = remember { WallpaperCache(appContext) }
    val logs = remember { PreferenceStore(appContext).logs() }
    val cacheFiles = remember { cache.cachedItems() }
    val cacheSize = remember { PreferenceStore(appContext).cacheSize }

    var selectedTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ===== 标题 =====
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp)) {
            Text(
                text = "壁纸记录",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "最近换过的壁纸与缓存中待用的壁纸，一目了然",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ===== Tab =====
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("历史记录") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("缓存壁纸") }
            )
        }

        when (selectedTab) {
            0 -> HistoryTab(logs, appContext)
            else -> CacheTab(cache, cacheFiles, cacheSize)
        }
    }
}

// ==================== Tab 1：历史记录 ====================

@Composable
private fun HistoryTab(logs: List<WallpaperLog>, appContext: Context) {
    if (logs.isEmpty()) {
        EmptyHint("暂无换壁纸记录")
        return
    }
    val cache = remember { WallpaperCache(appContext) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(logs, key = { it.time }) { log ->
            HistoryItem(cache, log, file = cache.appliedItem(log.wallpaper))
        }
    }
}

@Composable
private fun HistoryItem(cache: WallpaperCache, log: WallpaperLog, file: DocumentFile?) {
    val timeText = remember(log.time) {
        SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(log.time))
    }
    val entryLabel = remember(log.entry) {
        ChangeEntry.entries.firstOrNull { it.name == log.entry }?.label ?: log.entry
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (log.success) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 应用成功的壁纸缩略图（失败 / 无图时显示占位）
        WallpaperThumbnail(cache, file, Modifier.size(76.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (log.success) "成功" else "失败",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (log.success) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = "入口：$entryLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (log.message.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = log.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (log.success) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// ==================== Tab 2：缓存壁纸 ====================

@Composable
private fun CacheTab(cache: WallpaperCache, cacheFiles: List<DocumentFile>, cacheSize: Int) {
    if (cacheFiles.isEmpty()) {
        EmptyHint("缓存中没有壁纸，稍后会自动预取")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
            Text(
                text = "缓存中 ${cacheFiles.size} 张 / 目标 $cacheSize 张 · 序号 1 最先被应用",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
        }
        items(cacheFiles) { file ->
            val index = cacheFiles.indexOf(file) + 1
            Column {
                WallpaperThumbnail(cache, file, Modifier.fillMaxWidth().aspectRatio(9f / 16f))
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "序号 $index",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ==================== 通用组件 ====================

/** 壁纸缩略图：IO 线程采样解码（512px），避免大图 OOM 与主线程卡顿 */
@Composable
private fun WallpaperThumbnail(cache: WallpaperCache, doc: DocumentFile?, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(null, doc?.uri) {
        value = withContext(Dispatchers.IO) {
            doc?.let { cache.decodeImage(it, 512) }?.asImageBitmap()
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = null,
            modifier = modifier.clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "无图",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
