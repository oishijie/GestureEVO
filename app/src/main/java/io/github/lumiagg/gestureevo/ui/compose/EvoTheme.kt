package io.github.lumiagg.gestureevo.ui.compose

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat
import io.github.lumiagg.gestureevo.utils.ThemeManager
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

/**
 * 应用级 Compose 主题：跟随外观设置的动态取色开关。
 *
 * 开启壁纸取色（默认）且系统支持时用 Material You 动态配色；
 * 否则回落到基线 M3 配色，由 View 侧的主题 token 继续承载静态色。
 */
@Composable
fun EvoTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ThemeManager.dynamicColorEnabled) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) darkColorScheme() else lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/**
 * Miuix 主题：与 KernelSU Manager 同一套装配逻辑（ThemeController + 动态取色）。
 *
 * 目标项目没有种子色/配色风格设置，这里固定 TonalSpot + Spec2025，
 * 仅把「明暗模式」与「动态取色开关」映射到 Miuix 的 ColorSchemeMode。
 */
@Composable
fun MiuixEvoTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val dark = when (ThemeManager.themeMode) {
        ThemeManager.MODE_LIGHT -> false
        ThemeManager.MODE_DARK -> true
        else -> systemDark
    }
    val monet = ThemeManager.dynamicColorEnabled && ThemeManager.wallpaperColorsSupported

    val mode = when {
        monet -> when (ThemeManager.themeMode) {
            ThemeManager.MODE_LIGHT -> ColorSchemeMode.MonetLight
            ThemeManager.MODE_DARK -> ColorSchemeMode.MonetDark
            else -> ColorSchemeMode.MonetSystem
        }

        ThemeManager.themeMode == ThemeManager.MODE_LIGHT -> ColorSchemeMode.Light
        ThemeManager.themeMode == ThemeManager.MODE_DARK -> ColorSchemeMode.Dark
        else -> ColorSchemeMode.System
    }

    val keyColor: Color? = if (monet) {
        if (dark) dynamicDarkColorScheme(context).primary else dynamicLightColorScheme(context).primary
    } else {
        null
    }

    val controller = ThemeController(
        mode,
        keyColor = keyColor,
        isDark = dark,
        paletteStyle = ThemePaletteStyle.TonalSpot,
        colorSpec = ThemeColorSpec.Spec2025,
    )

    MiuixTheme(
        controller = controller,
        content = {
            LaunchedEffect(dark) {
                val window = (context as? Activity)?.window ?: return@LaunchedEffect
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            CompositionLocalProvider(
                LocalContentColor provides MiuixTheme.colorScheme.onBackground,
            ) {
                content()
            }
        }
    )
}
