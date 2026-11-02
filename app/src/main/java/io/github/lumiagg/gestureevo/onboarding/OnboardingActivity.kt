package io.github.lumiagg.gestureevo.onboarding

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.settings.evoAccessibilityServiceIsEnable
import io.github.lumiagg.gestureevo.ui.HomeActivity
import io.github.lumiagg.gestureevo.ui.compose.MiuixEvoTheme
import io.github.lumiagg.gestureevo.ui.miuix.MiuixCard
import io.github.lumiagg.gestureevo.ui.miuix.MiuixScreen
import io.github.lumiagg.gestureevo.utils.NotificationPermissionUtils
import io.github.lumiagg.gestureevo.utils.saveKey
import io.github.lumiagg.gestureevo.utils.settingsStore
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 首次启动引导：先逐步引导关键权限授权，再介绍核心功能。
 *
 * 每一步的权限状态会在返回应用后自动重新检测；完成或跳过后写入标记，不再自动弹出。
 */
class OnboardingActivity : AppCompatActivity() {

    private var index = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        index = savedInstanceState?.getInt(KEY_STEP) ?: 0
        setContent {
            MiuixEvoTheme {
                OnboardingScreen(
                    initialStep = index,
                    onStepChange = { index = it },
                    onFinish = { finishOnboarding() },
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_STEP, index)
    }

    private fun openSettings(step: Step.Permission) {
        val intent = runCatching { step.settingsIntent(this) }.getOrNull() ?: return
        runCatching { startActivity(intent) }.onFailure {
            Toast.makeText(this, R.string.onboarding_permission_settings_unavailable, Toast.LENGTH_SHORT)
                .show()
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

    @Composable
    private fun OnboardingScreen(
        initialStep: Int,
        onStepChange: (Int) -> Unit,
        onFinish: () -> Unit,
    ) {
        val context = LocalContext.current
        var stepIndex by remember { mutableIntStateOf(initialStep.coerceIn(0, steps.lastIndex)) }
        val step = steps[stepIndex]
        var granted by remember(stepIndex) {
            mutableStateOf(
                (step as? Step.Permission)?.let {
                    runCatching { it.isGranted(context) }.getOrDefault(false)
                } ?: false
            )
        }

        MiuixScreen(
            title = stringResource(R.string.app_name),
            actions = {
                Text(
                    text = stringResource(R.string.onboarding_skip),
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 12.dp),
                )
            },
        ) {
            item {
                MiuixCard {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
                        Icon(
                            imageVector = ImageVector.vectorResource(step.icon),
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(step.title),
                            fontSize = 22.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(step.desc),
                            fontSize = 15.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        if (step is Step.Permission) {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = stringResource(
                                    if (granted) R.string.onboarding_permission_granted
                                    else R.string.onboarding_permission_pending
                                ),
                                fontSize = 14.sp,
                                color = if (granted) MiuixTheme.colorScheme.primary
                                else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    openSettings(step)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = stringResource(
                                        if (granted) R.string.onboarding_permission_granted_action
                                        else R.string.onboarding_permission_grant
                                    ),
                                )
                            }
                        }
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    steps.indices.forEach { i ->
                        val active = i == stepIndex
                        Spacer(
                            modifier = Modifier
                                .height(8.dp)
                                .width(if (active) 22.dp else 8.dp)
                                .clip(CircleShape)
                                .then(
                                    Modifier.background(
                                        if (active) MiuixTheme.colorScheme.primary
                                        else MiuixTheme.colorScheme.outline.copy(alpha = 0.4f)
                                    )
                                ),
                        )
                        if (i != steps.lastIndex) Spacer(Modifier.width(6.dp))
                    }
                }
            }
            item {
                Button(
                    onClick = {
                        if (stepIndex >= steps.lastIndex) {
                            onFinish()
                        } else {
                            stepIndex++
                            onStepChange(stepIndex)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(
                            if (stepIndex == steps.lastIndex) R.string.onboarding_start
                            else R.string.onboarding_next
                        ),
                    )
                }
            }
        }

        // 返回应用后重新检测权限状态。
        androidx.compose.runtime.LaunchedEffect(stepIndex) {
            val p = steps[stepIndex] as? Step.Permission ?: return@LaunchedEffect
            granted = runCatching { p.isGranted(context) }.getOrDefault(false)
        }
    }

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
                icon = R.drawable.baseline_search_24,
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
