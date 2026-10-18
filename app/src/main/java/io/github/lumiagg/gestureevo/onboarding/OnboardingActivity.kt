package io.github.lumiagg.gestureevo.onboarding

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.google.android.material.color.MaterialColors
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.databinding.ActivityOnboardingBinding
import io.github.lumiagg.gestureevo.databinding.ItemOnboardingFeatureBinding
import io.github.lumiagg.gestureevo.databinding.ItemOnboardingPermissionBinding
import io.github.lumiagg.gestureevo.settings.evoAccessibilityServiceIsEnable
import io.github.lumiagg.gestureevo.ui.HomeActivity
import io.github.lumiagg.gestureevo.utils.NotificationPermissionUtils
import io.github.lumiagg.gestureevo.utils.dp2px
import io.github.lumiagg.gestureevo.utils.saveKey
import io.github.lumiagg.gestureevo.utils.settingsStore
import kotlinx.coroutines.launch

/**
 * 首次启动引导：先逐步引导关键权限授权，再介绍核心功能。
 *
 * 每一步的权限状态会在返回应用后自动重新检测；完成或跳过后写入标记，不再自动弹出。
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private var index = 0
    private var permissionBinding: ItemOnboardingPermissionBinding? = null
    private val indicators = mutableListOf<View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        applyInsets()

        binding.skipButton.setOnClickListener { finishOnboarding() }
        binding.nextButton.setOnClickListener { advance() }

        buildIndicator()
        showStep(savedInstanceState?.getInt(KEY_STEP) ?: 0)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_STEP, index)
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStep()
    }

    private fun applyInsets() {
        val topPadding = binding.onboardingTopBar.paddingTop
        val bottomPadding = binding.onboardingBottomBar.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.onboardingRoot) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.onboardingTopBar.updatePadding(top = topPadding + bars.top)
            binding.onboardingBottomBar.updatePadding(bottom = bottomPadding + bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.onboardingRoot)
    }

    private fun showStep(target: Int) {
        index = target.coerceIn(0, steps.lastIndex)
        permissionBinding = null
        binding.onboardingContainer.removeAllViews()

        when (val step = steps[index]) {
            is Step.Permission -> {
                val item = ItemOnboardingPermissionBinding.inflate(
                    layoutInflater,
                    binding.onboardingContainer,
                    false,
                )
                item.permissionIcon.setImageResource(step.icon)
                item.permissionIcon.imageTintList = ColorStateList.valueOf(
                    MaterialColors.getColor(
                        item.root,
                        android.R.attr.colorPrimary,
                        Color.GRAY,
                    )
                )
                item.permissionTitle.setText(step.title)
                item.permissionDesc.setText(step.desc)
                item.permissionAction.setOnClickListener { openSettings(step) }
                binding.onboardingContainer.addView(item.root)
                permissionBinding = item
                refreshPermissionStep()
            }

            is Step.Feature -> {
                val item = ItemOnboardingFeatureBinding.inflate(
                    layoutInflater,
                    binding.onboardingContainer,
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
                binding.onboardingContainer.addView(item.root)
            }
        }

        binding.nextButton.setText(
            if (index == steps.lastIndex) R.string.onboarding_start else R.string.onboarding_next
        )
        updateIndicator()
    }

    private fun refreshPermissionStep() {
        val item = permissionBinding ?: return
        val step = steps.getOrNull(index) as? Step.Permission ?: return
        val granted = runCatching { step.isGranted(this) }.getOrDefault(false)
        val grantedColor = MaterialColors.getColor(
            item.root,
            android.R.attr.colorPrimary,
            Color.GRAY,
        )
        val pendingColor = MaterialColors.getColor(
            item.root,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
            Color.GRAY,
        )
        item.permissionStatus.setText(
            if (granted) R.string.onboarding_permission_granted
            else R.string.onboarding_permission_pending
        )
        item.permissionStatus.setTextColor(if (granted) grantedColor else pendingColor)
        item.permissionAction.setText(
            if (granted) R.string.onboarding_permission_granted_action
            else R.string.onboarding_permission_grant
        )
    }

    private fun openSettings(step: Step.Permission) {
        val intent = runCatching { step.settingsIntent(this) }.getOrNull() ?: return
        runCatching { startActivity(intent) }.onFailure {
            Toast.makeText(this, R.string.onboarding_permission_settings_unavailable, Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun advance() {
        if (index >= steps.lastIndex) {
            finishOnboarding()
        } else {
            showStep(index + 1)
        }
    }

    private fun finishOnboarding() {
        lifecycleScope.launch {
            settingsStore.saveKey(KEY_ONBOARDING_COMPLETED, true)
            if (!fromSettings) {
                startActivity(Intent(this@OnboardingActivity, HomeActivity::class.java))
            }
            finish()
        }
    }

    /** 从设置页重新查看引导时，完成后只关闭引导页。 */
    private val fromSettings: Boolean
        get() = intent?.getBooleanExtra(EXTRA_FROM_SETTINGS, false) == true

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

    private sealed interface Step {
        val icon: Int
        val title: Int
        val desc: Int

        data class Permission(
            @DrawableRes override val icon: Int,
            @StringRes override val title: Int,
            @StringRes override val desc: Int,
            val isGranted: (Context) -> Boolean,
            val settingsIntent: (Context) -> Intent,
        ) : Step

        data class Feature(
            @DrawableRes override val icon: Int,
            @StringRes override val title: Int,
            @StringRes override val desc: Int,
        ) : Step
    }

    private val steps: List<Step> by lazy {
        listOf(
            Step.Permission(
                icon = R.drawable.icon_service,
                title = R.string.onboarding_permission_accessibility_title,
                desc = R.string.onboarding_permission_accessibility_desc,
                isGranted = { evoAccessibilityServiceIsEnable(it) },
                settingsIntent = { Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS) },
            ),
            Step.Permission(
                icon = R.drawable.ic_little_window,
                title = R.string.onboarding_permission_overlay_title,
                desc = R.string.onboarding_permission_overlay_desc,
                isGranted = { Settings.canDrawOverlays(it) },
                settingsIntent = { context ->
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}"),
                    )
                },
            ),
            Step.Permission(
                icon = R.drawable.baseline_circle_notifications_24,
                title = R.string.onboarding_permission_notification_title,
                desc = R.string.onboarding_permission_notification_desc,
                isGranted = { NotificationPermissionUtils.canPostNotifications(it) },
                settingsIntent = { context ->
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                },
            ),
            Step.Permission(
                icon = R.drawable.icon_lock,
                title = R.string.onboarding_permission_background_title,
                desc = R.string.onboarding_permission_background_desc,
                isGranted = { isIgnoringBatteryOptimizations(it) },
                settingsIntent = {
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                },
            ),
            Step.Feature(
                icon = R.drawable.baseline_gesture_24,
                title = R.string.onboarding_feature_gesture_title,
                desc = R.string.onboarding_feature_gesture_desc,
            ),
            Step.Feature(
                icon = R.drawable.outline_center_focus_weak_24,
                title = R.string.onboarding_feature_screen_title,
                desc = R.string.onboarding_feature_screen_desc,
            ),
            Step.Feature(
                icon = R.drawable.search_24px,
                title = R.string.onboarding_feature_search_title,
                desc = R.string.onboarding_feature_search_desc,
            ),
            Step.Feature(
                icon = R.drawable.baseline_push_pin_24,
                title = R.string.onboarding_feature_pin_title,
                desc = R.string.onboarding_feature_pin_desc,
            ),
        )
    }

    private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager =
            context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    companion object {
        /** 引导完成标记；设置页可据此提供"重新查看引导"入口。 */
        const val KEY_ONBOARDING_COMPLETED = "key_onboarding_completed"

        /** 从设置页进入时携带，表示完成后仅关闭引导页。 */
        const val EXTRA_FROM_SETTINGS = "onboarding_from_settings"

        private const val KEY_STEP = "onboarding_step"
    }
}
