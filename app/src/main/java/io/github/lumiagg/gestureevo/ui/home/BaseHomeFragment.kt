package io.github.lumiagg.gestureevo.ui.home

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.settings.ui.AboutActivity
import io.github.lumiagg.gestureevo.utils.PreferenceDataStoreAdapter
import io.github.lumiagg.gestureevo.utils.settingsStore

/**
 * 一级页面基类：统一数据存储适配、二级页跳转绑定、顶部状态卡刷新，
 * 并支持来自设置搜索的"滚动到指定设置项"。
 *
 * 状态卡是每个一级页面顶部的信息卡（图标 + 标题 + 动态摘要 + 动作按钮），
 * 通过 [bindStatusCard] 注册刷新逻辑，页面创建与每次 onResume 时都会重新执行，
 * 使用户从系统设置等页面返回时能看到最新状态。
 */
abstract class BaseHomeFragment : PreferenceFragmentCompat() {

    private val statusRefreshers = mutableListOf<() -> Unit>()
    private var pendingScrollKey: String? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore =
            PreferenceDataStoreAdapter(requireContext().settingsStore, lifecycleScope)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 卡片式条目自带圆角与间距，去掉列表分割线。
        // 注意：setDivider/setDividerHeight 必须在 RecyclerView 创建之后调用，
        // 在 onCreatePreferences 阶段 mList 仍为 null，会触发 NPE。
        setDivider(ColorDrawable(Color.TRANSPARENT))
        setDividerHeight(0)
        // 列表背景透明，露出 HomeActivity 内容区的 colorSurfaceContainerLow 底色，
        // 使白卡片浮在浅色页底上（新版一级页视觉）。
        listView?.background = null
        applyPendingScroll()
    }

    override fun onResume() {
        super.onResume()
        statusRefreshers.forEach { it() }
        applyPendingScroll()
    }

    /** 请求滚动到指定设置项；若页面尚未构建完成会延迟到视图就绪后执行。 */
    open fun scrollToPreferenceKey(key: String) {
        pendingScrollKey = key
        applyPendingScroll()
    }

    private fun applyPendingScroll() {
        val key = pendingScrollKey ?: return
        val list = listView ?: return
        pendingScrollKey = null
        list.post { runCatching { scrollToPreference(key) } }
    }

    // ---------------------------------------------------------------
    // 状态卡与跳转
    // ---------------------------------------------------------------

    /** 绑定状态卡；[refresh] 在页面创建与每次 onResume 时执行，用于刷新动态状态。 */
    protected fun bindStatusCard(key: String, refresh: (HomeStatusCardPreference) -> Unit) {
        val card = findPreference<HomeStatusCardPreference>(key) ?: return
        statusRefreshers += { refresh(card) }
        refresh(card)
    }

    /**
     * 注册一个额外的状态刷新回调（页面创建与每次 onResume 时执行）。
     * 用于顶部轮播这类不是单个 Preference 的可刷新区域。
     */
    protected fun registerStatusRefresh(refresh: () -> Unit) {
        statusRefreshers += refresh
    }

    /**
     * 绑定功能引导状态卡：标题与摘要固定，整卡点击与动作按钮都打开
     * [openIntent] 返回的分页引导（屏幕页/搜索页各自的内容）。
     */
    protected fun bindGuideCard(key: String, summaryRes: Int, openIntent: () -> Intent) {
        bindStatusCard(key) { card ->
            card.title = getString(R.string.home_guide_title)
            card.summary = getString(summaryRes)
            card.onActionClick = { startActivity(openIntent()) }
            card.setOnPreferenceClickListener {
                startActivity(openIntent())
                true
            }
        }
    }

    protected fun bindNavigation(key: String, target: Class<out Activity>) {
        findPreference<Preference>(key)?.setOnPreferenceClickListener {
            startActivity(Intent(requireActivity(), target))
            true
        }
    }

    /** 打开关于页（独立全屏页，样式与功能引导页一致）。 */
    protected fun bindAboutSheet(key: String) {
        findPreference<Preference>(key)?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), AboutActivity::class.java))
            true
        }
    }

    protected fun bindCustom(key: String, action: () -> Unit) {
        findPreference<Preference>(key)?.setOnPreferenceClickListener {
            action()
            true
        }
    }
}
