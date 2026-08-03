package com.example.wallpaper.ui.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.wallpaper.data.PreferenceStore
import com.example.wallpaper.data.WallpaperCache
import com.example.wallpaper.domain.ChangeEntry
import com.example.wallpaper.domain.WallpaperChanger
import com.example.wallpaper.domain.WallpaperTarget
import com.example.wallpaper.schedule.WallpaperScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 设置页换壁纸状态（加载中 / 成功 / 失败） */
data class SettingsUiState(
    val isLoading: Boolean = false,
    val isError: Boolean = false,
    val statusText: String = ""
)

/**
 * 设置界面 ViewModel（逻辑层）。
 *
 * 负责：
 * - 图片 API 地址 / 壁纸目标（主屏、锁屏、主屏+锁屏）的读写与持久化
 * - 定时切换开关与间隔（开启即调度，关闭即取消）
 * - 缓存预取数量（2~5 张）与壁纸记录保留条数
 * - "立即换壁纸"：同样调用唯一的换壁纸方法 [WallpaperChanger.change]
 *   （触发来源 MANUAL），仅把结果状态展示在本界面
 * - 手动补充缓存（"立即预取"）
 */
class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = PreferenceStore(app)

    private val _imageUrl = MutableStateFlow(prefs.imageUrl)
    val imageUrl: StateFlow<String> = _imageUrl.asStateFlow()

    private val _target = MutableStateFlow(prefs.target)
    val target: StateFlow<WallpaperTarget> = _target.asStateFlow()

    private val _scheduleEnabled = MutableStateFlow(prefs.scheduleEnabled)
    val scheduleEnabled: StateFlow<Boolean> = _scheduleEnabled.asStateFlow()

    private val _scheduleIntervalMs = MutableStateFlow(prefs.scheduleIntervalMs)
    val scheduleIntervalMs: StateFlow<Long> = _scheduleIntervalMs.asStateFlow()

    private val _cacheSize = MutableStateFlow(prefs.cacheSize)
    val cacheSize: StateFlow<Int> = _cacheSize.asStateFlow()

    private val _cacheCount = MutableStateFlow(WallpaperCache(app).cachedCount())
    val cacheCount: StateFlow<Int> = _cacheCount.asStateFlow()

    private val _cacheDirUri = MutableStateFlow(prefs.cacheDirUri)
    val cacheDirUri: StateFlow<String> = _cacheDirUri.asStateFlow()

    private val _prefetching = MutableStateFlow(false)
    val prefetching: StateFlow<Boolean> = _prefetching.asStateFlow()

    private val _logMaxCount = MutableStateFlow(prefs.logMaxCount)
    val logMaxCount: StateFlow<Int> = _logMaxCount.asStateFlow()

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /** 更新图片 API 地址（持久化，供所有换壁纸入口复用） */
    fun updateImageUrl(url: String) {
        _imageUrl.value = url.trim()
        prefs.imageUrl = url.trim()
    }

    /** 切换壁纸目标（持久化，供所有换壁纸入口复用） */
    fun updateTarget(t: WallpaperTarget) {
        _target.value = t
        prefs.target = t
    }

    /** 开关定时切换：开启时立即按当前间隔调度，关闭时取消 */
    fun setScheduleEnabled(context: Context, enabled: Boolean) {
        _scheduleEnabled.value = enabled
        prefs.scheduleEnabled = enabled
        val appContext = context.applicationContext
        if (enabled) {
            WallpaperScheduler.schedule(appContext, _scheduleIntervalMs.value)
        } else {
            WallpaperScheduler.cancel(appContext)
        }
    }

    /** 修改定时间隔：持久化；若定时已开启则按新间隔重新调度 */
    fun setScheduleInterval(context: Context, intervalMs: Long) {
        _scheduleIntervalMs.value = intervalMs
        prefs.scheduleIntervalMs = intervalMs
        if (_scheduleEnabled.value) {
            WallpaperScheduler.schedule(context.applicationContext, intervalMs)
        }
    }

    /** 修改缓存预取数量：持久化，并立即补充到新数量 */
    fun setCacheSize(size: Int) {
        _cacheSize.value = size
        prefs.cacheSize = size
        if (size > _cacheCount.value) prefetch()
        else refreshCacheCount()
    }

    /**
     * 设置自定义缓存目录（SAF 授权后由 Activity 传入 content:// URI 并持久化）。
     * 传空字符串 = 恢复默认目录（Android/data/<包名>/files/wallpapers）。
     */
    fun setCacheDir(uri: String) {
        _cacheDirUri.value = uri
        prefs.cacheDirUri = uri
        refreshCacheCount()
    }

    /** 恢复默认缓存目录 */
    fun resetCacheDir() = setCacheDir("")

    /** 手动补充缓存到目标数量 */
    fun prefetch() {
        if (_prefetching.value) return
        viewModelScope.launch {
            _prefetching.value = true
            withContext(Dispatchers.IO) {
                WallpaperCache(getApplication()).ensureFull()
            }
            _prefetching.value = false
            refreshCacheCount()
        }
    }

    /** 刷新当前缓存数量 */
    fun refreshCacheCount() {
        _cacheCount.value = WallpaperCache(getApplication()).cachedCount()
    }

    /** 修改壁纸记录保留条数：持久化 */
    fun setLogMaxCount(count: Int) {
        _logMaxCount.value = count
        prefs.logMaxCount = count
    }

    /**
     * 立即换壁纸（设置页手动触发）：
     * 调用唯一的换壁纸方法（缓存优先），结果状态展示在本界面。
     */
    fun changeWallpaper(context: Context) {
        if (_uiState.value.isLoading) return // 防止重复点击
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isError = false, statusText = "加载中…") }
            val result = withContext(Dispatchers.IO) {
                WallpaperChanger.change(context.applicationContext, ChangeEntry.MANUAL)
            }
            _uiState.update {
                it.copy(isLoading = false, isError = !result.success, statusText = result.message)
            }
            refreshCacheCount()
        }
    }
}
