package io.github.lumiagg.gestureevo.ui.home

import android.content.Intent
import android.os.Bundle
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.settings.AppearanceSettingsActivity
import io.github.lumiagg.gestureevo.settings.BackgroundStabilityActivity
import io.github.lumiagg.gestureevo.settings.DataBackActivity
import io.github.lumiagg.gestureevo.settings.AdvancedSettingsActivity
import io.github.lumiagg.gestureevo.settings.SetupChecklist
import io.github.lumiagg.gestureevo.utils.ThemeManager

/**
 * 一级页面：应用设置。
 *
 * 顶部为「功能介绍与引导」横幅（静态，原轮播已取消），
 * 外观主题从轮播下沉为下方入口卡（置顶），摘要动态显示当前配色；
 * 「权限与后台」卡徽章显示待处理项数。
 */
class SettingsHomeFragment : BaseHomeFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        setPreferencesFromResource(R.xml.home_settings_prefs, rootKey)

        bindGuideCard("preferenceAppearance", R.string.home_guide_summary_settings) {
            FeatureGuideActivity.intent(requireContext(), FeatureGuideActivity.GUIDE_SETTINGS)
        }

        // 外观主题：原轮播状态页下沉为入口卡，摘要随 onResume 刷新当前配色。
        bindNavigation("preferenceAppearanceEntry", AppearanceSettingsActivity::class.java)
        refreshThemeSummary()
        registerStatusRefresh { refreshThemeSummary() }

        // 权限与后台管理：原「设置检查」与「后台稳定性」合并后的宿主页。
        bindCustom("preferenceSetupCheck") {
            startActivity(Intent(requireContext(), BackgroundStabilityActivity::class.java))
        }
        // 权限可能在系统设置里被改动，返回本页时刷新待完成数量。
        refreshSetupCheckSummary()
        registerStatusRefresh { refreshSetupCheckSummary() }

        bindNavigation("preferenceData", DataBackActivity::class.java)
        bindNavigation("preferenceAdvanced", AdvancedSettingsActivity::class.java)
        bindAboutSheet("preferenceAbout")
    }

    private fun refreshSetupCheckSummary() {
        val pending = SetupChecklist.pendingCount(requireContext())
        val card = findPreference<HomeEntryPreference>("preferenceSetupCheck")
        card?.summary =
            if (pending == 0) {
                getString(R.string.setup_check_entry_summary_done)
            } else {
                getString(R.string.setup_check_entry_summary_pending, pending)
            }
        card?.setBadge(
            if (pending == 0) {
                getString(R.string.home_badge_setup_done)
            } else {
                getString(R.string.home_badge_setup_pending, pending)
            },
            warning = pending > 0,
        )
    }

    /** 刷新「外观主题」入口卡摘要：当前取色方式（壁纸取色 / 固定配色）。 */
    private fun refreshThemeSummary() {
        val detail = if (ThemeManager.usesWallpaperColors) {
            getString(R.string.theme_summary_wallpaper)
        } else {
            getString(R.string.theme_summary_default)
        }
        findPreference<HomeEntryPreference>("preferenceAppearanceEntry")?.summary =
            getString(R.string.home_summary_theme_entry, detail)
    }
}
