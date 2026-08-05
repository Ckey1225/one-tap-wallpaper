package com.example.wallpaper.ui.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.wallpaper.domain.WallpaperTarget
import com.example.wallpaper.permission.PermissionGuide
import com.example.wallpaper.ui.log.LogActivity
import com.example.wallpaper.ui.log.CacheGalleryActivity

/** 定时切换间隔选项（毫秒, 显示文案） */
private val scheduleOptions: List<Pair<Long, String>> = listOf(
    30 * 60 * 1000L to "30 分钟",
    60 * 60 * 1000L to "1 小时",
    2 * 60 * 60 * 1000L to "2 小时",
    4 * 60 * 60 * 1000L to "4 小时",
    6 * 60 * 60 * 1000L to "6 小时",
    12 * 60 * 60 * 1000L to "12 小时",
    24 * 60 * 60 * 1000L to "24 小时"
)

/**
 * 设置界面（UI Layer）：底部导航栏 + 4 个分区，人性化归类。
 *
 * 点击应用图标 = 直接静默换壁纸（不经过本界面）；
 * 长按图标「设置」磁贴 = 进入本界面。
 *
 * - 「换壁纸」：立即换壁纸 + 图片 API + 壁纸模式
 * - 「缓存」：当前缓存状态 / 手动补充 / 查看缓存壁纸
 * - 「定时」：定时切换开关 / 间隔 / 后台保护引导
 * - 「记录」：查看壁纸记录（大图画廊，右上角可设保留条数）
 */
@Composable
fun SettingsScreen(
    context: Context,
    vm: SettingsViewModel,
) {
    val imageUrl by vm.imageUrl.collectAsState()
    val target by vm.target.collectAsState()
    val uiState by vm.uiState.collectAsState()
    val scheduleEnabled by vm.scheduleEnabled.collectAsState()
    val scheduleIntervalMs by vm.scheduleIntervalMs.collectAsState()
    val cacheSize by vm.cacheSize.collectAsState()
    val cacheCount by vm.cacheCount.collectAsState()
    val prefetching by vm.prefetching.collectAsState()

    // 底部导航当前页（横竖屏切换时保持）
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text("换壁纸") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Filled.Star, contentDescription = null) },
                    label = { Text("缓存") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                    label = { Text("定时") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = { Text("记录") }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> WallpaperTab(
                    context = context, vm = vm,
                    imageUrl = imageUrl, target = target, uiState = uiState
                )
                1 -> CacheTab(
                    context = context, vm = vm,
                    cacheSize = cacheSize, cacheCount = cacheCount, prefetching = prefetching
                )
                2 -> ScheduleTab(
                    context = context, vm = vm,
                    scheduleEnabled = scheduleEnabled, scheduleIntervalMs = scheduleIntervalMs
                )
                else -> RecordsTab(context)
            }
        }
    }
}

// ==================== Tab 1：换壁纸 ====================

@Composable
private fun WallpaperTab(
    context: Context,
    vm: SettingsViewModel,
    imageUrl: String,
    target: WallpaperTarget,
    uiState: SettingsUiState,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SectionTitle("换壁纸", "点击应用图标即静默换壁纸；长按图标可回到本设置页")
        Spacer(Modifier.height(20.dp))

        // 立即换壁纸（手动验证配置）+ 状态：居中大按钮
        Button(
            onClick = { vm.changeWallpaper(context) },
            enabled = !uiState.isLoading,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.size(width = 230.dp, height = 60.dp)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Text("立即换壁纸", style = MaterialTheme.typography.titleLarge)
            }
        }
        Spacer(Modifier.height(10.dp))
        val statusColor = if (uiState.isError) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant
        Text(
            text = uiState.statusText.ifEmpty { "换壁纸全程静默，结果记录在壁纸记录中" },
            style = MaterialTheme.typography.bodyMedium,
            color = statusColor
        )
        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.4f))
        Spacer(Modifier.height(20.dp))

        // 图片 API 地址
        OutlinedTextField(
            value = imageUrl,
            onValueChange = vm::updateImageUrl,
            label = { Text("图片 API 地址") },
            placeholder = { Text("http://...") },
            supportingText = { Text("需直接返回图片（如 t.alcy.cc/pc/）") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                keyboardType = KeyboardType.Uri
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(20.dp))

        // 壁纸目标（主屏 / 锁屏 / 主屏+锁屏）
        Text("壁纸设置模式", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WallpaperTarget.entries.forEach { t ->
                val selected = t == target
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                        .clickable { vm.updateTarget(t) }
                ) {
                    RadioButton(selected = selected, onClick = { vm.updateTarget(t) })
                    Text(
                        text = t.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.4f))
        Spacer(Modifier.height(20.dp))
    }
}

// ==================== Tab 2：缓存 ====================

@Composable
private fun CacheTab(
    context: Context,
    vm: SettingsViewModel,
    cacheSize: Int,
    cacheCount: Int,
    prefetching: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SectionTitle("壁纸缓存", "自动预取壁纸，换壁纸时从缓存秒换，不再等待网络")

        // 当前缓存状态卡片
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (cacheCount >= cacheSize) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                    RoundedCornerShape(12.dp)
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.size(10.dp))
            Text(
                text = if (cacheCount >= cacheSize) "缓存已就绪：$cacheCount / $cacheSize 张"
                else "缓存不足：$cacheCount / $cacheSize 张，换壁纸会等待网络",
                style = MaterialTheme.typography.bodyMedium,
                color = if (cacheCount >= cacheSize) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.error
            )
        }
        Spacer(Modifier.height(20.dp))

        // 缓存数量选择
        var showCacheSizeDialog by remember { mutableStateOf(false) }
        val cacheSizeOptions = listOf(3, 5, 10, 20, 50, 100)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { showCacheSizeDialog = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("缓存数量", style = MaterialTheme.typography.bodyLarge)
            Text(
                "$cacheSize 张",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (showCacheSizeDialog) {
            AlertDialog(
                onDismissRequest = { showCacheSizeDialog = false },
                title = { Text("缓存数量") },
                text = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        cacheSizeOptions.forEach { size ->
                            TextButton(
                                onClick = {
                                    vm.setCacheSize(size)
                                    showCacheSizeDialog = false
                                }
                            ) {
                                Text(
                                    "$size",
                                    color = if (size == cacheSize) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showCacheSizeDialog = false }) { Text("取消") }
                }
            )
        }

        Spacer(Modifier.height(20.dp))

        // 查看缓存壁纸（长按保存原图到相册）
        Button(
            onClick = { context.startActivity(Intent(context, CacheGalleryActivity::class.java)) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("查看缓存壁纸")
        }
        Text(
            text = "打开缓存图片列表，长按缩略图可保存原图到相册",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(Modifier.height(16.dp))

        // 手动补充缓存
        OutlinedButton(
            onClick = { vm.prefetch() },
            enabled = !prefetching && cacheCount < cacheSize,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (prefetching) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp))
            } else {
                Text(if (cacheCount < cacheSize) "立即补充缓存" else "缓存已满")
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ==================== Tab 3：定时 ====================

@Composable
private fun ScheduleTab(
    context: Context,
    vm: SettingsViewModel,
    scheduleEnabled: Boolean,
    scheduleIntervalMs: Long,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SectionTitle("定时切换壁纸", "到点自动换壁纸，同样优先走缓存、秒换")

        // 开关状态卡片
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (scheduleEnabled) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(12.dp)
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (scheduleEnabled) "已开启" else "已关闭",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (scheduleEnabled) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (scheduleEnabled) "到点自动换壁纸" else "打开后按间隔自动换壁纸",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (scheduleEnabled) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = scheduleEnabled,
                onCheckedChange = { vm.setScheduleEnabled(context, it) }
            )
        }
        Spacer(Modifier.height(20.dp))

        // 间隔选择
        Text("切换间隔", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            scheduleOptions.forEach { (ms, label) ->
                val selected = ms == scheduleIntervalMs
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { vm.setScheduleInterval(context, ms) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(label, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.4f))
        Spacer(Modifier.height(20.dp))

        // 后台保护 / 自启动引导
        Text("后台保护", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        val batteryExempt = remember { PermissionGuide.isIgnoringBatteryOptimizations(context) }
        Text(
            text = if (batteryExempt) "电池优化：已豁免，后台可正常唤醒"
            else "电池优化：未豁免，定时任务可能被系统延迟或拦截",
            style = MaterialTheme.typography.bodySmall,
            color = if (batteryExempt) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { PermissionGuide.requestBatteryOptimizationExemption(context) },
                modifier = Modifier.weight(1f)
            ) {
                Text("电池优化白名单")
            }
            OutlinedButton(
                onClick = { PermissionGuide.openAutoStartSettings(context) },
                modifier = Modifier.weight(1f)
            ) {
                Text("自启动管理")
            }
        }
        Text(
            text = "国产 ROM（MIUI/EMUI/ColorOS 等）需在自启动管理中将本应用设为允许，否则定时任务可能被杀",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(Modifier.height(24.dp))
    }
}

// ==================== Tab 4：记录 ====================

@Composable
private fun RecordsTab(
    context: Context,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SectionTitle("壁纸记录", "记录每次换壁纸结果与应用过的壁纸，成功失败都保留")

        Spacer(Modifier.height(20.dp))

        // 查看壁纸记录（大图画廊）
        Button(
            onClick = { context.startActivity(Intent(context, LogActivity::class.java)) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("查看壁纸记录")
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "以大图方式查看每次应用过的壁纸，右上角可设置保留条数",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
    }
}

// ==================== 通用组件 ====================

/** 分区标题（导航页顶部） */
@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
