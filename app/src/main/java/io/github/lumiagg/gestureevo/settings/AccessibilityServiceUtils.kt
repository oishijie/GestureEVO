package io.github.lumiagg.gestureevo.settings

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** 无障碍服务状态与快捷开启。原定义在已退役的 SettingsActivity 中，页面删除后独立成文件。 */

fun evoAccessibilityServiceIsEnable(context: Context): Boolean {
    val accessibilityManager =
        (context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager)
    val accessibilityServices = accessibilityManager.getEnabledAccessibilityServiceList(
        AccessibilityServiceInfo.FEEDBACK_ALL_MASK
    )
    for (info in accessibilityServices) {
        if (info.id.contains(context.packageName)) {
            return true
        }
    }
    return false
}

suspend fun autoOpenAccessibilityService(context: Context): Boolean =
    withContext(Dispatchers.Default) {
        if (evoAccessibilityServiceIsEnable(context)) {
            return@withContext true
        } else {
            try {
                Log.d("AccessibilityServiceUtils", "autoOpenAccessibilityService: ")
                val enabledService = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
                Settings.Secure.putString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    "$enabledService:${context.packageName}/${context.packageName}.gesture.GestureService"
                )
                Settings.Secure.putInt(
                    context.contentResolver,
                    Settings.Secure.ACCESSIBILITY_ENABLED, 1
                )
                delay(300)
                return@withContext true
            } catch (e: SecurityException) {
                return@withContext false
            }
        }
    }
