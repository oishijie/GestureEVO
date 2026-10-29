package io.github.lumiagg.gestureevo.ui.compose

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import io.github.lumiagg.gestureevo.utils.ThemeManager

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
