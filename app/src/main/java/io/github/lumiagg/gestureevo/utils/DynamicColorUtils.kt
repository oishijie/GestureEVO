package io.github.lumiagg.gestureevo.utils

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.widget.FrameLayout
import java.util.WeakHashMap
import androidx.annotation.ColorInt
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.view.ViewCompat
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.google.android.material.shape.MaterialShapeDrawable
import io.github.lumiagg.gestureevo.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.google.android.material.R as MaterialR

/**
 * 全局配色发布与主题应用。
 *
 * 配色只有一个来源：系统壁纸取色（Android 12+ 的 DynamicColors）。
 * 关闭「动态取色」或系统版本不足时，直接使用基础主题自带的中性配色。
 */
object DynamicColorUtils {
    private const val TAG = "DynamicColorUtils"

    private data class PublishedColors(
        val primary: Int = 0,
        val inversePrimary: Int = 0,
        val primaryFixed: Int = 0,
        val onPrimaryContainer: Int = 0,
        val secondaryContainer: Int = 0,
        val inverseOnSurface: Int = 0,
        val surface: Int = 0,
        val surfaceContainer: Int = 0,
        val surfaceContainerHigh: Int = 0,
        val surfaceContainerHighest: Int = 0,
        val onSurface: Int = 0,
        val onSurfaceVariant: Int = 0,
        val outline: Int = 0,
    )

    @Volatile
    private var published = PublishedColors()

    private var overlayInstalled = false

    /**
     * Activity → 创建时对应的主题纪元。
     * 主题偏好变更后已存在的 Activity 必须重建才会重新解析主题属性，
     * 这里在 onActivityResumed 比对纪元，不一致即 recreate
     * （明暗模式另由 AppCompat 自动重建，不重复触发）。
     */
    private val appliedEpochs = WeakHashMap<Activity, Int>()

    val inverseOnSurfaceColor: Int get() = published.inverseOnSurface

    /** 页面底色（白）：页底、卡底、状态栏统一取这一档。 */
    val surfaceColor: Int get() = published.surface
    val secondaryContainerColor: Int get() = published.secondaryContainer
    val surfaceContainerHighestColor: Int get() = published.surfaceContainerHighest
    val onPrimaryContainerColor: Int get() = published.onPrimaryContainer
    val primaryFixedColor: Int get() = published.primaryFixed
    val primaryColor: Int get() = published.primary
    val inversePrimaryColor: Int get() = published.inversePrimary
    val surfaceContainerColor: Int get() = published.surfaceContainer
    val surfaceContainerHighColor: Int get() = published.surfaceContainerHigh
    val onSurfaceColor: Int get() = published.onSurface
    val onSurfaceVariantColor: Int get() = published.onSurfaceVariant
    val outlineColor: Int get() = published.outline

    /** 注册全局 Activity 生命周期回调，按当前主题设置应用配色。 */
    fun applyAppWideColors(application: Application) {
        if (overlayInstalled) return
        overlayInstalled = true
        application.registerActivityLifecycleCallbacks(object :
            Application.ActivityLifecycleCallbacks {
            override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
                activity.setTheme(R.style.Theme_GestureEVO)
                applyActivityTheme(activity)
                ensureTransparentActivitiesStayClear(activity)
                // 记录本次创建所依据的主题纪元，供 onResume 判断是否需要重建。
                appliedEpochs[activity] = ThemeManager.themeEpoch
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {
                val applied = appliedEpochs[activity] ?: return
                if (applied != ThemeManager.themeEpoch) {
                    Log.d(
                        TAG,
                        "Theme epoch $applied -> ${ThemeManager.themeEpoch}, " +
                            "recreating ${activity.javaClass.simpleName}"
                    )
                    activity.recreate()
                }
            }

            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private fun applyActivityTheme(activity: Activity) {
        if (ThemeManager.usesWallpaperColors) {
            DynamicColors.applyToActivityIfAvailable(activity)
        }
    }

    /** 给代码里手动构建的视图（弹窗、悬浮窗内容等）套上与当前页面一致的主题。 */
    fun dynamicThemedContext(base: Context): Context {
        val themed = ContextThemeWrapper(base, R.style.Theme_GestureEVO)
        if (ThemeManager.usesWallpaperColors) {
            val overlay =
                if (themed.isNightMode())
                    MaterialR.style.ThemeOverlay_Material3_DynamicColors_Dark
                else
                    MaterialR.style.ThemeOverlay_Material3_DynamicColors_Light
            themed.theme.applyStyle(overlay, true)
        }
        return themed
    }

    fun captureColor(context: Context, coroutineScope: CoroutineScope) =
        coroutineScope.launch(Dispatchers.Main.immediate) {
            refreshSurfaceColors(context)
        }

    fun captureSystemColorsWithThemedContext(base: Context) {
        captureSystemColors(dynamicThemedContext(base))
    }

    fun captureSystemColors(context: Context) {
        try {
            val prev = published
            published = PublishedColors(
                primary = MaterialColors.getColor(
                    context,
                    android.R.attr.colorPrimary,
                    prev.primary
                ),
                inversePrimary = MaterialColors.getColor(
                    context,
                    MaterialR.attr.colorPrimaryInverse,
                    prev.inversePrimary
                ),
                primaryFixed = MaterialColors.getColor(
                    context,
                    android.R.attr.colorPrimary,
                    prev.primaryFixed
                ),
                onPrimaryContainer = MaterialColors.getColor(
                    context,
                    MaterialR.attr.colorOnPrimaryContainer,
                    prev.onPrimaryContainer
                ),
                secondaryContainer = MaterialColors.getColor(
                    context,
                    MaterialR.attr.colorSecondaryContainer,
                    prev.secondaryContainer
                ),
                inverseOnSurface = MaterialColors.getColor(
                    context,
                    MaterialR.attr.colorOnSurfaceInverse,
                    prev.inverseOnSurface
                ),
                surface = MaterialColors.getColor(
                    context,
                    MaterialR.attr.colorSurface,
                    prev.surface
                ),
                surfaceContainer = MaterialColors.getColor(
                    context,
                    MaterialR.attr.colorSurfaceContainer,
                    prev.surfaceContainer
                ),
                surfaceContainerHigh = MaterialColors.getColor(
                    context,
                    MaterialR.attr.colorSurfaceContainerHigh,
                    prev.surfaceContainerHigh
                ),
                surfaceContainerHighest = MaterialColors.getColor(
                    context,
                    MaterialR.attr.colorSurfaceContainerHighest,
                    prev.surfaceContainerHighest
                ),
                onSurface = MaterialColors.getColor(
                    context,
                    MaterialR.attr.colorOnSurface,
                    prev.onSurface
                ),
                onSurfaceVariant = MaterialColors.getColor(
                    context,
                    MaterialR.attr.colorOnSurfaceVariant,
                    prev.onSurfaceVariant
                ),
                outline = MaterialColors.getColor(
                    context,
                    MaterialR.attr.colorOutline,
                    prev.outline
                ),
            )
            logDynamicColors()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving colors: ${e.message}")
        }
    }

    // Checks whether the context is in night mode.
    fun Context.isNightMode(): Boolean {
        return (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    }

    private fun logDynamicColors() {
        Log.d(
            TAG,
            "colors => " +
                    "primary=${primaryColor.toHex()}, " +
                    "inversePrimary=${inversePrimaryColor.toHex()}, " +
                    "inverseOnSurface=${inverseOnSurfaceColor.toHex()}, " +
                    "secondaryContainer=${secondaryContainerColor.toHex()}, " +
                    "surfaceContainerHighest=${surfaceContainerHighestColor.toHex()}, " +
                    "onPrimaryContainer=${onPrimaryContainerColor.toHex()}, " +
                    "primaryFixed=${primaryFixedColor.toHex()}, " +
                    "surfaceContainerColor=${surfaceContainerColor.toHex()}, " +
                    "surfaceContainerHighColor=${surfaceContainerHighColor.toHex()}, " +
                    "onSurfaceColor=${onSurfaceColor.toHex()}, " +
                    "onSurfaceVariantColor=${onSurfaceVariantColor.toHex()}, " +
                    "outlineColor=${outlineColor.toHex()}"
        )
    }

    // Extension function: converts color integer to hexadecimal representation.
    private fun Int.toHex() = String.format("#%08X", this)

    private fun ensureTransparentActivitiesStayClear(activity: Activity) {
        val attrs = intArrayOf(android.R.attr.windowIsTranslucent)
        val array = activity.theme.obtainStyledAttributes(attrs)
        val isTranslucent = array.getBoolean(0, false)
        array.recycle()
        if (isTranslucent) {
            activity.window.setBackgroundDrawableResource(android.R.color.transparent)
        }
    }

    fun refreshSurfaceColors(context: Context) {
        captureSystemColors(dynamicThemedContext(context))
    }

    fun applyBottomSheetSurface(dialog: com.google.android.material.bottomsheet.BottomSheetDialog) {
        val sheet =
            dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
                ?: return
        val ctx = sheet.context
        val surface = surfaceContainerColor.takeIf { it != 0 }
            ?: MaterialColors.getColor(
                ctx,
                com.google.android.material.R.attr.colorSurface,
                0
            )
        val shape = sheet.background as? MaterialShapeDrawable
        if (shape != null) {
            shape.fillColor = ColorStateList.valueOf(surface)
            shape.tintList = ColorStateList.valueOf(surface)
        } else {
            ViewCompat.setBackgroundTintList(sheet, ColorStateList.valueOf(surface))
        }
    }
}
