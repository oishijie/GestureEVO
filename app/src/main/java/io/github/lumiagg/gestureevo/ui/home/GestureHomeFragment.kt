package io.github.lumiagg.gestureevo.ui.home

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.lifecycle.lifecycleScope
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.gesture.config.GestureActionDataManager
import io.github.lumiagg.gestureevo.settings.ActionViewSettingsActivity
import io.github.lumiagg.gestureevo.settings.FreeFormSettingsActivity
import io.github.lumiagg.gestureevo.settings.FreeFormSettingsActivity.Companion.KEY_USE_MI_WINDOW
import io.github.lumiagg.gestureevo.settings.FreeTriggerSettingsActivity
import io.github.lumiagg.gestureevo.settings.autoOpenAccessibilityService
import io.github.lumiagg.gestureevo.settings.evoAccessibilityServiceIsEnable
import io.github.lumiagg.gestureevo.utils.Rom
import io.github.lumiagg.gestureevo.utils.readKey
import io.github.lumiagg.gestureevo.utils.settingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 一级页面：手势与触发。
 *
 * 顶部为「功能介绍与引导」横幅（静态，原轮播已取消，引导内容只保留一页），
 * 无障碍服务从轮播下沉为下方入口卡，徽章随 onResume 刷新开关状态；
 * 其余入口卡徽章：轮盘卡显示轮盘个数，小窗卡显示当前小窗模式。
 */
class GestureHomeFragment : BaseHomeFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        setPreferencesFromResource(R.xml.home_gesture_prefs, rootKey)

        bindGuideCard("preferenceService", R.string.home_guide_summary_gesture) {
            FeatureGuideActivity.intent(requireContext(), FeatureGuideActivity.GUIDE_GESTURE)
        }

        // 无障碍服务：原轮播状态页下沉为入口卡，整卡点击进系统无障碍设置。
        bindCustom("preferenceAccessibility") { openAccessibilitySettings() }

        bindNavigation("preferenceConfigTrigger", FreeTriggerSettingsActivity::class.java)
        bindNavigation("preferenceActionView", ActionViewSettingsActivity::class.java)
        bindNavigation("preferenceFreeform", FreeFormSettingsActivity::class.java)

        refreshEntryBadges()
        registerStatusRefresh { refreshEntryBadges() }
    }

    /** 刷新入口卡徽章：无障碍开关 / 轮盘个数 / 小窗模式。 */
    private fun refreshEntryBadges() {
        lifecycleScope.launch {
            val context = requireContext()

            val serviceEnabled = withContext(Dispatchers.IO) {
                autoOpenAccessibilityService(context)
                evoAccessibilityServiceIsEnable(context)
            }
            findPreference<HomeEntryPreference>("preferenceAccessibility")?.setBadge(
                getString(
                    if (serviceEnabled) R.string.home_badge_accessibility_on
                    else R.string.home_badge_accessibility_off
                ),
                warning = !serviceEnabled,
            )

            val wheelCount = withContext(Dispatchers.IO) {
                GestureActionDataManager.readActionListTags(context).size
            }
            findPreference<HomeEntryPreference>("preferenceActionView")?.setBadge(
                getString(R.string.home_badge_wheel_count, wheelCount)
            )

            val modeLabel = withContext(Dispatchers.IO) { freeFormModeLabel(context) }
            findPreference<HomeEntryPreference>("preferenceFreeform")?.setBadge(
                getString(R.string.home_badge_freeform_mode, modeLabel)
            )
        }
    }

    /**
     * 解析当前生效的小窗模式名（与 FreeFormSettingsActivity 下拉框的码表一致）。
     * 小窗设置没有总开关，徽章展示当前模式而非虚构的"已开启/未开启"。
     */
    private suspend fun freeFormModeLabel(context: Context): String {
        val useMiWindow = context.settingsStore.readKey(KEY_USE_MI_WINDOW, false)
        val code = if (useMiWindow) MODE_SUNSHINE else Rom.getFreeFormCode(context)
        val labelRes = when (code) {
            CODE_NUBIA -> R.string.freeform_mode_6_nubia
            CODE_FLYME -> R.string.freeform_mode_5_flyme
            CODE_OPPO -> R.string.freeform_mode_2_oppo
            CODE_EMUI -> R.string.freeform_mode_3_hw
            CODE_VIVO -> R.string.freeform_mode_4_vivo_share
            MODE_SUNSHINE -> R.string.freeform_mode_7_sunshine
            else -> R.string.freeform_mode_1
        }
        return context.getString(labelRes)
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    private companion object {
        /** 与 FreeFormSettingsActivity.setupDropdownMenu 的码表保持一致。 */
        const val MODE_SUNSHINE = -3
        const val CODE_NUBIA = -2
        const val CODE_FLYME = 11
        const val CODE_OPPO = 100
        const val CODE_EMUI = 102
        const val CODE_VIVO = 106
    }
}
