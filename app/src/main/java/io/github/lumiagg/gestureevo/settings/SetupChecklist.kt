package io.github.lumiagg.gestureevo.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.utils.NotificationPermissionUtils

/**
 * 设置检查清单的一项。
 *
 * 状态在每次读取时实时检测（权限可能在系统设置里被改动），
 * 点击后直达对应的系统设置页，返回应用时由页面 onResume 重新检测。
 *
 * [isGranted] 返回 null 表示「无法自动检测」（如各厂商私有的自启动管理页，
 * 只能跳转、没有通用读取 API）：这类项不计入待完成数量，徽章显示为手动设置。
 */
data class SetupCheckItem(
    @DrawableRes val icon: Int,
    @StringRes val title: Int,
    @StringRes val description: Int,
    val isGranted: (Context) -> Boolean?,
    val settingsIntent: (Context) -> Intent,
)

/**
 * 关键配置清单：手势识别、悬浮窗、通知使用权与后台保活。
 *
 * 检查逻辑与首次启动引导（OnboardingActivity）保持一致，
 * 这里是常驻清单，任何时候进入都能看到当前缺什么。
 */
object SetupChecklist {

    val items: List<SetupCheckItem> = listOf(
        SetupCheckItem(
            icon = R.drawable.icon_service,
            title = R.string.onboarding_permission_accessibility_title,
            description = R.string.onboarding_permission_accessibility_desc,
            isGranted = { evoAccessibilityServiceIsEnable(it) },
            settingsIntent = { Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS) },
        ),
        SetupCheckItem(
            icon = R.drawable.ic_little_window,
            title = R.string.onboarding_permission_overlay_title,
            description = R.string.onboarding_permission_overlay_desc,
            isGranted = { Settings.canDrawOverlays(it) },
            settingsIntent = { context ->
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                )
            },
        ),
        SetupCheckItem(
            icon = R.drawable.baseline_circle_notifications_24,
            title = R.string.onboarding_permission_notification_title,
            description = R.string.onboarding_permission_notification_desc,
            isGranted = { NotificationPermissionUtils.canPostNotifications(it) },
            settingsIntent = { context ->
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            },
        ),
        SetupCheckItem(
            icon = R.drawable.icon_lock,
            title = R.string.onboarding_permission_background_title,
            description = R.string.onboarding_permission_background_desc,
            isGranted = { context ->
                val powerManager =
                    context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
            },
            settingsIntent = { Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) },
        ),
        // 自启动：厂商私有管理页，没有通用读取 API，isGranted 返回 null（无法检测），
        // 点击后按厂商跳转对应的自启动管理页。
        SetupCheckItem(
            icon = R.drawable.baseline_refresh_24,
            title = R.string.background_stability_autostart_title,
            description = R.string.background_stability_autostart_description,
            isGranted = { null },
            settingsIntent = { context -> buildAutoStartIntent(context) },
        ),
    )

    /** 尚未完成的关键配置数量，用于设置页入口摘要。无法检测的项不计入。 */
    fun pendingCount(context: Context): Int =
        items.count { it.isGranted(context) == false }

    /** 厂商自启动管理页组件；未覆盖的厂商退回应用详情页。 */
    private fun buildAutoStartIntent(context: Context): Intent {
        val intent = Intent()
        val manufacturer = Build.MANUFACTURER.lowercase()
        when {
            manufacturer.contains("xiaomi") -> {
                intent.component = android.content.ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            }

            manufacturer.contains("oppo") -> {
                intent.component = android.content.ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                )
            }

            manufacturer.contains("vivo") -> {
                intent.component = android.content.ComponentName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                )
            }

            else -> {
                intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                intent.data = Uri.fromParts("package", context.packageName, null)
            }
        }
        return intent
    }
}
