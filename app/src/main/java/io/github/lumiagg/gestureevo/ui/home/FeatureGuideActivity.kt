package io.github.lumiagg.gestureevo.ui.home

import android.content.Context
import android.content.Intent
import android.os.Bundle
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.ui.compose.MiuixEvoTheme
import io.github.lumiagg.gestureevo.ui.miuix.MiuixCard
import io.github.lumiagg.gestureevo.ui.miuix.MiuixScreen
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 分页功能引导：屏幕页与搜索页各自的玩法介绍。
 *
 * 步骤形式与首次启动引导一致（图标 + 标题 + 说明 + 步骤点），
 * 但只讲当前页涉及的功能，不掺杂权限引导，可随时返回。
 */
class FeatureGuideActivity : AppCompatActivity() {

    private var index = 0

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

            GUIDE_OVERVIEW -> listOf(
                GuideStep(R.drawable.baseline_home_24, R.string.guide_ia_overview_welcome_title, R.string.guide_ia_overview_welcome_desc),
                GuideStep(R.drawable.icon_service, R.string.guide_ia_overview_service_title, R.string.guide_ia_overview_service_desc),
                GuideStep(R.drawable.baseline_auto_awesome_24, R.string.guide_ia_overview_personalize_title, R.string.guide_ia_overview_personalize_desc),
            )

            GUIDE_CONTROL -> listOf(
                GuideStep(R.drawable.baseline_gesture_24, R.string.guide_ia_control_gesture_title, R.string.guide_ia_control_gesture_desc),
                GuideStep(R.drawable.baseline_pie_chart_24, R.string.guide_ia_control_wheel_title, R.string.guide_ia_control_wheel_desc),
                GuideStep(R.drawable.ic_little_window, R.string.guide_ia_control_window_title, R.string.guide_ia_control_window_desc),
            )

            GUIDE_CONTENT -> listOf(
                GuideStep(R.drawable.baseline_qr_code_scanner_24, R.string.guide_ia_content_recognition_title, R.string.guide_ia_content_recognition_desc),
                GuideStep(R.drawable.baseline_push_pin_24, R.string.guide_ia_content_pin_title, R.string.guide_ia_content_pin_desc),
                GuideStep(R.drawable.ic_baseline_share_24, R.string.guide_ia_content_share_title, R.string.guide_ia_content_share_desc),
            )

            GUIDE_MANAGEMENT -> listOf(
                GuideStep(R.drawable.baseline_search_24, R.string.guide_ia_management_search_title, R.string.guide_ia_management_search_desc),
                GuideStep(R.drawable.baseline_save_24, R.string.guide_ia_management_backup_title, R.string.guide_ia_management_backup_desc),
                GuideStep(R.drawable.baseline_miscellaneous_services_24, R.string.guide_ia_management_maintenance_title, R.string.guide_ia_management_maintenance_desc),
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
        index = savedInstanceState?.getInt(KEY_STEP) ?: 0
        setContent {
            MiuixEvoTheme {
                GuideScreen(
                    initialStep = index,
                    onStepChange = { index = it },
                    onClose = { finish() },
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_STEP, index)
    }

    @Composable
    private fun GuideScreen(
        initialStep: Int,
        onStepChange: (Int) -> Unit,
        onClose: () -> Unit,
    ) {
        var stepIndex by remember { mutableIntStateOf(initialStep.coerceIn(0, steps.lastIndex)) }
        val step = steps[stepIndex]

        MiuixScreen(
            title = stringResource(
                when (page) {
                    GUIDE_SETTINGS -> R.string.guide_title_settings
                    GUIDE_GESTURE -> R.string.guide_title_gesture
                    GUIDE_SEARCH -> R.string.guide_title_search
                    GUIDE_OVERVIEW -> R.string.guide_title_ia_overview
                    GUIDE_CONTROL -> R.string.guide_title_ia_control
                    GUIDE_CONTENT -> R.string.guide_title_ia_content
                    GUIDE_MANAGEMENT -> R.string.guide_title_ia_management
                    else -> R.string.guide_title_screen
                }
            ),
            onBack = onClose,
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
                            onClose()
                        } else {
                            stepIndex++
                            onStepChange(stepIndex)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(
                            if (stepIndex == steps.lastIndex) R.string.guide_done
                            else R.string.onboarding_next
                        ),
                    )
                }
            }
        }
    }

    companion object {
        /** 屏幕页引导。 */
        const val GUIDE_SCREEN = "screen"

        /** 搜索页引导。 */
        const val GUIDE_SEARCH = "search"

        /** 手势页引导。 */
        const val GUIDE_GESTURE = "gesture"

        /** 设置页引导。 */
        const val GUIDE_SETTINGS = "settings"

        /** 新 IA 总览页引导。 */
        const val GUIDE_OVERVIEW = "overview"

        /** 新 IA 操控页引导。 */
        const val GUIDE_CONTROL = "control"

        /** 新 IA 内容页引导。 */
        const val GUIDE_CONTENT = "content"

        /** 新 IA 管理页引导。 */
        const val GUIDE_MANAGEMENT = "management"

        private const val EXTRA_GUIDE_PAGE = "feature_guide_page"
        private const val KEY_STEP = "feature_guide_step"

        fun intent(context: Context, page: String): Intent {
            return Intent(context, FeatureGuideActivity::class.java)
                .putExtra(EXTRA_GUIDE_PAGE, page)
        }
    }
}
