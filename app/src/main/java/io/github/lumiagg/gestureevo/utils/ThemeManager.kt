package io.github.lumiagg.gestureevo.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 主题设置（已精简）。
 *
 * 只有两个维度：
 * 1. 明暗模式：跟随系统 / 浅色 / 深色；
 * 2. 动态取色开关：开启后由系统壁纸取色（Android 12+ 的 DynamicColors）生成配色，
 *    关闭则使用应用基础主题自带的中性配色。
 *
 * 不提供种子色、配色风格、自定义调色板等能力——配色来源只有壁纸一个。
 */
object ThemeManager {

    const val KEY_THEME_MODE = "key_theme_mode"

    /** 动态取色开关。沿用旧 Monet 开关的 key，保留用户已有设置，无需迁移。 */
    const val KEY_DYNAMIC_COLOR = "key_monet_enabled"

    const val MODE_SYSTEM = 0
    const val MODE_LIGHT = 1
    const val MODE_DARK = 2

    @Volatile
    var themeMode: Int = MODE_SYSTEM
        private set

    @Volatile
    var dynamicColorEnabled: Boolean = true
        private set

    /**
     * 主题纪元：主题偏好每次变更 +1。
     * 已存在的 Activity 靠 [DynamicColorUtils] 在 onResume 比对纪元自重建，
     * 服务悬浮窗等非 Activity 消费者走 [themeChangeListeners] 立即刷新。
     */
    @Volatile
    var themeEpoch: Int = 0
        private set

    private val themeChangeListeners = CopyOnWriteArrayList<() -> Unit>()

    /** 壁纸取色依赖 Android 12+ 的 DynamicColors。 */
    val wallpaperColorsSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /** 当前是否真的走壁纸取色。 */
    val usesWallpaperColors: Boolean
        get() = dynamicColorEnabled && wallpaperColorsSupported

    fun addThemeChangeListener(listener: () -> Unit) {
        themeChangeListeners.addIfAbsent(listener)
    }

    fun removeThemeChangeListener(listener: () -> Unit) {
        themeChangeListeners.remove(listener)
    }

    suspend fun load(context: Context) {
        val store = context.settingsStore
        themeMode = store.readKey(KEY_THEME_MODE, MODE_SYSTEM)
        dynamicColorEnabled = store.readKey(KEY_DYNAMIC_COLOR, true)
        applyNightMode()
    }

    suspend fun saveThemeMode(context: Context, mode: Int) {
        themeMode = mode
        context.settingsStore.saveKey(KEY_THEME_MODE, mode)
        applyNightMode()
    }

    suspend fun saveDynamicColor(context: Context, enabled: Boolean) {
        dynamicColorEnabled = enabled
        context.settingsStore.saveKey(KEY_DYNAMIC_COLOR, enabled)
    }

    /** 同步 AppCompatDelegate 的夜间模式；切换后由调用方重建当前页。 */
    fun applyNightMode() {
        val delegateMode = when (themeMode) {
            MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(delegateMode)
    }

    fun isDark(context: Context): Boolean {
        if (themeMode == MODE_LIGHT) return false
        if (themeMode == MODE_DARK) return true
        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return night == Configuration.UI_MODE_NIGHT_YES
    }

    /**
     * 主题设置变更后调用：推进主题纪元（各 Activity onResume 据此自重建）、
     * 刷新夜间模式，并通知非 Activity 消费者（服务悬浮窗）立即刷新。
     */
    fun onThemePreferenceChanged() {
        themeEpoch++
        applyNightMode()
        themeChangeListeners.forEach { runCatching { it() } }
    }
}
