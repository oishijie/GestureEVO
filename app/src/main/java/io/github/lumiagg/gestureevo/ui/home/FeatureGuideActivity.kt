package io.github.lumiagg.gestureevo.ui.home

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.color.MaterialColors
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.databinding.ActivityFeatureGuideBinding
import io.github.lumiagg.gestureevo.databinding.ItemOnboardingFeatureBinding
import io.github.lumiagg.gestureevo.utils.dp2px

/**
 * 分页功能引导：屏幕页与搜索页各自的玩法介绍。
 *
 * 步骤形式与首次启动引导一致（图标 + 标题 + 说明 + 步骤点），
 * 但只讲当前页涉及的功能，不掺杂权限引导，可随时返回。
 */
class FeatureGuideActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFeatureGuideBinding
    private var index = 0
    private val indicators = mutableListOf<View>()

    private val page: String
        get() = intent?.getStringExtra(EXTRA_GUIDE_PAGE) ?: GUIDE_SCREEN

    /** 引导步骤：图标 + 标题 + 说明。 */
    private data class GuideStep(
        @DrawableRes val icon: Int,
        @StringRes val title: Int,
        @StringRes val desc: Int,
    )

    private val steps: List<GuideStep> by lazy {
        when (page) {
            GUIDE_SETTINGS -> listOf(
                GuideStep(
                    icon = R.drawable.baseline_auto_awesome_24,
                    title = R.string.guide_settings_theme_title,
                    desc = R.string.guide_settings_theme_desc,
                ),
                GuideStep(
                    icon = R.drawable.baseline_save_24,
                    title = R.string.guide_settings_backup_title,
                    desc = R.string.guide_settings_backup_desc,
                ),
                GuideStep(
                    icon = R.drawable.ic_baseline_insert_link_24,
                    title = R.string.guide_settings_external_title,
                    desc = R.string.guide_settings_external_desc,
                ),
                GuideStep(
                    icon = R.drawable.icon_lock,
                    title = R.string.guide_settings_background_title,
                    desc = R.string.guide_settings_background_desc,
                ),
            )

            GUIDE_GESTURE -> listOf(
                GuideStep(
                    icon = R.drawable.icon_service,
                    title = R.string.guide_gesture_service_title,
                    desc = R.string.guide_gesture_service_desc,
                ),
                GuideStep(
                    icon = R.drawable.baseline_gesture_24,
                    title = R.string.guide_gesture_trigger_title,
                    desc = R.string.guide_gesture_trigger_desc,
                ),
                GuideStep(
                    icon = R.drawable.baseline_pie_chart_24,
                    title = R.string.guide_gesture_action_view_title,
                    desc = R.string.guide_gesture_action_view_desc,
                ),
                GuideStep(
                    icon = R.drawable.ic_little_window,
                    title = R.string.guide_gesture_freeform_title,
                    desc = R.string.guide_gesture_freeform_desc,
                ),
            )

            GUIDE_SEARCH -> listOf(
                GuideStep(
                    icon = R.drawable.baseline_search_24,
                    title = R.string.guide_search_engines_title,
                    desc = R.string.guide_search_engines_desc,
                ),
                GuideStep(
                    icon = R.drawable.baseline_pie_chart_24,
                    title = R.string.guide_search_display_title,
                    desc = R.string.guide_search_display_desc,
                ),
                GuideStep(
                    icon = R.drawable.baseline_miscellaneous_services_24,
                    title = R.string.guide_search_function_title,
                    desc = R.string.guide_search_function_desc,
                ),
                GuideStep(
                    icon = R.drawable.baseline_home_24,
                    title = R.string.guide_search_startup_title,
                    desc = R.string.guide_search_startup_desc,
                ),
                GuideStep(
                    icon = R.drawable.baseline_save_24,
                    title = R.string.guide_search_transfer_title,
                    desc = R.string.guide_search_transfer_desc,
                ),
                GuideStep(
                    icon = R.drawable.baseline_unfold_more_24,
                    title = R.string.guide_search_preview_title,
                    desc = R.string.guide_search_preview_desc,
                ),
            )

            else -> listOf(
                GuideStep(
                    icon = R.drawable.baseline_qr_code_scanner_24,
                    title = R.string.guide_screen_recognition_title,
                    desc = R.string.guide_screen_recognition_desc,
                ),
                GuideStep(
                    icon = R.drawable.ic_baseline_share_24,
                    title = R.string.guide_screen_share_title,
                    desc = R.string.guide_screen_share_desc,
                ),
                GuideStep(
                    icon = R.drawable.outline_center_focus_weak_24,
                    title = R.string.guide_screen_pointer_title,
                    desc = R.string.guide_screen_pointer_desc,
                ),
                GuideStep(
                    icon = R.drawable.baseline_push_pin_24,
                    title = R.string.guide_screen_pinpool_title,
                    desc = R.string.guide_screen_pinpool_desc,
                ),
                GuideStep(
                    icon = R.drawable.baseline_circle_notifications_24,
                    title = R.string.guide_screen_notification_title,
                    desc = R.string.guide_screen_notification_desc,
                ),
                GuideStep(
                    icon = R.drawable.baseline_keyboard_24,
                    title = R.string.guide_screen_keyboard_title,
                    desc = R.string.guide_screen_keyboard_desc,
                ),
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFeatureGuideBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        applyInsets()

        binding.guideTitle.setText(
            when (page) {
                GUIDE_SETTINGS -> R.string.guide_title_settings
                GUIDE_GESTURE -> R.string.guide_title_gesture
                GUIDE_SEARCH -> R.string.guide_title_search
                else -> R.string.guide_title_screen
            }
        )
        binding.guideBackButton.setOnClickListener { finish() }
        binding.nextButton.setOnClickListener {
            if (index >= steps.lastIndex) {
                finish()
            } else {
                showStep(index + 1)
            }
        }

        buildIndicator()
        showStep(savedInstanceState?.getInt(KEY_STEP) ?: 0)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_STEP, index)
    }

    private fun applyInsets() {
        val topPadding = binding.guideTopBar.paddingTop
        val bottomPadding = binding.guideBottomBar.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.guideRoot) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.guideTopBar.updatePadding(top = topPadding + bars.top)
            binding.guideBottomBar.updatePadding(bottom = bottomPadding + bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.guideRoot)
    }

    private fun showStep(target: Int) {
        index = target.coerceIn(0, steps.lastIndex)
        binding.guideContainer.removeAllViews()

        val step = steps[index]
        val item = ItemOnboardingFeatureBinding.inflate(
            layoutInflater,
            binding.guideContainer,
            false,
        )
        item.featureIcon.setImageResource(step.icon)
        item.featureIcon.imageTintList = ColorStateList.valueOf(
            MaterialColors.getColor(
                item.root,
                android.R.attr.colorPrimary,
                Color.GRAY,
            )
        )
        item.featureTitle.setText(step.title)
        item.featureDesc.setText(step.desc)
        binding.guideContainer.addView(item.root)

        binding.nextButton.setText(
            if (index == steps.lastIndex) R.string.guide_done else R.string.onboarding_next
        )
        updateIndicator()
    }

    private fun buildIndicator() {
        indicators.clear()
        binding.stepIndicator.removeAllViews()
        repeat(steps.size) {
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dotSizePx, dotSizePx).apply {
                    marginEnd = 6f.dp2px.toInt()
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dotSizePx / 2f
                    setColor(Color.GRAY)
                }
            }
            binding.stepIndicator.addView(dot)
            indicators += dot
        }
    }

    private fun updateIndicator() {
        val activeColor = MaterialColors.getColor(
            binding.stepIndicator,
            android.R.attr.colorPrimary,
            Color.GRAY,
        )
        val inactiveColor = MaterialColors.getColor(
            binding.stepIndicator,
            com.google.android.material.R.attr.colorOutlineVariant,
            Color.LTGRAY,
        )
        indicators.forEachIndexed { position, dot ->
            (dot.background as? GradientDrawable)?.setColor(
                if (position == index) activeColor else inactiveColor
            )
            (dot.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
                params.width = if (position == index) activeDotWidthPx else dotSizePx
                dot.layoutParams = params
            }
        }
    }

    private val dotSizePx: Int get() = 8f.dp2px.toInt()
    private val activeDotWidthPx: Int get() = 22f.dp2px.toInt()

    companion object {
        /** 屏幕页引导。 */
        const val GUIDE_SCREEN = "screen"

        /** 搜索页引导。 */
        const val GUIDE_SEARCH = "search"

        /** 手势页引导。 */
        const val GUIDE_GESTURE = "gesture"

        /** 设置页引导。 */
        const val GUIDE_SETTINGS = "settings"

        private const val EXTRA_GUIDE_PAGE = "feature_guide_page"
        private const val KEY_STEP = "feature_guide_step"

        fun intent(context: Context, page: String): Intent {
            return Intent(context, FeatureGuideActivity::class.java)
                .putExtra(EXTRA_GUIDE_PAGE, page)
        }
    }
}
