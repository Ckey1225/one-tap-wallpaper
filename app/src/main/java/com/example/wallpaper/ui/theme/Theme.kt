package com.example.wallpaper.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** 兜底配色（Android 12 以下无莫奈取色时使用） */
private val LightColors = lightColorScheme(
    primary = Color(0xFF3F51B5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8EAF6),
    secondary = Color(0xFF5C6BC0),
    surface = Color.White,
    background = Color(0xFFF7F8FC)
)

/**
 * 全局主题。
 *
 * Android 12+（API 31+）：使用系统莫奈（Material You）动态取色，
 * 跟随壁纸与系统主题自动配色；低版本回退默认配色。
 */
@Composable
fun WallpaperTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isSystemInDarkTheme()) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
