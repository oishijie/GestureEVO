package io.github.lumiagg.gestureevo.ui.home

import android.os.Bundle
import androidx.preference.Preference
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.settings.InspireSettingsActivity
import io.github.lumiagg.gestureevo.settings.SettingsSearchNavigator

/**
 * 一级页面：屏幕内容。
 *
 * 以手势页为模板——文字分组标题 + 分组下条目行。每个条目打开原版
 * InspireSettingsActivity 并深链展开对应分区卡，详细设置（开关/弹窗/底部弹窗）
 * 全部留在原版页面，不在这里做第二套实现。
 */
class ScreenHomeFragment : BaseHomeFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        setPreferencesFromResource(R.xml.home_screen_prefs, rootKey)

        bindGuideCard("card_screen_guide", R.string.home_guide_summary_screen) {
            FeatureGuideActivity.intent(requireContext(), FeatureGuideActivity.GUIDE_SCREEN)
        }

        // 菜单入口 → 原版设置页对应分区卡（深链展开并滚动定位）。
        openSection("menu_screen_recognition", R.id.screenContentSwitchCard)

        // 分享 → 原版功能设置页，深链展开「分享设置」与「分享形式」两张分区卡。
        findPreference<Preference>("menu_screen_share")?.setOnPreferenceClickListener {
            startActivity(
                SettingsSearchNavigator.deepLinkIntent(
                    requireContext(),
                    InspireSettingsActivity::class.java,
                    R.id.defaultShareBehaviorCard,
                    R.id.appSelectionCard,
                )
            )
            true
        }

        // 指针与键盘 → 原版功能设置页，深链展开指针/Pin池/键盘三张分区卡。
        findPreference<Preference>("menu_screen_pointer_keyboard")?.setOnPreferenceClickListener {
            startActivity(
                SettingsSearchNavigator.deepLinkIntent(
                    requireContext(),
                    InspireSettingsActivity::class.java,
                    R.id.PointerCard,
                    R.id.pinPoolCard,
                    R.id.keyboardTypeCard,
                )
            )
            true
        }

        openSection("menu_screen_notification", R.id.notificationCard)
    }

    private fun openSection(key: String, cardId: Int) {
        findPreference<Preference>(key)?.setOnPreferenceClickListener {
            startActivity(
                SettingsSearchNavigator.deepLinkIntent(
                    requireContext(),
                    InspireSettingsActivity::class.java,
                    cardId,
                )
            )
            true
        }
    }
}
