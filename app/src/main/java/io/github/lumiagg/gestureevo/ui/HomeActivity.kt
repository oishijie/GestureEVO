package io.github.lumiagg.gestureevo.ui

import android.content.Intent
import android.os.Bundle
import android.transition.Fade
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.databinding.ActivityHomeBinding
import io.github.lumiagg.gestureevo.settings.SettingsSearchActivity
import io.github.lumiagg.gestureevo.settings.SettingsSearchNavigator
import io.github.lumiagg.gestureevo.ui.home.BaseHomeFragment
import io.github.lumiagg.gestureevo.ui.home.GestureHomeFragment
import io.github.lumiagg.gestureevo.ui.home.ScreenHomeFragment
import io.github.lumiagg.gestureevo.ui.home.SearchHomeFragment
import io.github.lumiagg.gestureevo.ui.home.SettingsHomeFragment
import io.github.lumiagg.gestureevo.utils.DynamicColorUtils

/**
 * 应用主界面：四个一级页面（手势 / 屏幕 / 搜索 / 设置）配悬浮胶囊底栏。
 *
 * 页面使用 show/hide 切换以保留各自的滚动位置与状态；底栏颜色跟随主题 token。
 */
class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private var currentIndex = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupSystemBars()
        applyWindowInsets()
        setupSearchEntry()

        val initialTab = savedInstanceState?.getInt(KEY_TAB_INDEX) ?: 0
        binding.bottomBar.submitTabs(TABS, selected = initialTab)
        binding.bottomBar.onTabSelected = { switchTab(it) }
        switchTab(initialTab)
        handleNavigateIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TAB_INDEX, currentIndex.coerceAtLeast(0))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavigateIntent(intent)
    }

    private fun setupSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // 状态栏与页底同为 colorSurface，避免顶部出现一条比页面更深的色带。
        window.statusBarColor = DynamicColorUtils.surfaceColor
    }

    private fun applyWindowInsets() {
        val initialTop = binding.appBar.paddingTop
        val baseBottomMargin =
            resources.getDimensionPixelSize(R.dimen.home_bottom_bar_margin)
        val contentGap =
            resources.getDimensionPixelSize(R.dimen.home_content_bottom_padding)
        val fallbackBarHeight =
            resources.getDimensionPixelSize(R.dimen.home_bottom_bar_estimated_height)

        ViewCompat.setOnApplyWindowInsetsListener(binding.appBar) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = initialTop + bars.top)
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomBar) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            (view.layoutParams as? CoordinatorLayout.LayoutParams)?.let { params ->
                params.bottomMargin = baseBottomMargin + bars.bottom
                view.layoutParams = params
            }
            insets
        }

        // 内容区底部留白按「底栏实测高度 + 底栏间距 + 导航栏高度」动态计算，
        // 避免三键导航等较高的系统栏让列表最后一项落到底栏之下。
        ViewCompat.setOnApplyWindowInsetsListener(binding.homeContainer) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val barHeight = binding.bottomBar.height.takeIf { it > 0 } ?: fallbackBarHeight
            view.updatePadding(bottom = barHeight + baseBottomMargin + bars.bottom + contentGap)
            insets
        }

        ViewCompat.requestApplyInsets(binding.appBar)
        ViewCompat.requestApplyInsets(binding.bottomBar)
        // 底栏高度要等布局完成才可测量，届时再刷新一次内容区留白。
        binding.bottomBar.doOnLayout {
            ViewCompat.requestApplyInsets(binding.homeContainer)
        }
    }

    private fun setupSearchEntry() {
        window.sharedElementExitTransition =
            SettingsSearchNavigator.buildSharedElementTransition(this)
        window.sharedElementReenterTransition =
            SettingsSearchNavigator.buildSharedElementTransition(this)
        window.exitTransition = Fade().apply { duration = 140L }
        window.reenterTransition = Fade().apply { duration = 140L }

        binding.settingsSearchEntryCard.setOnClickListener {
            val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                this,
                binding.settingsSearchEntryCard,
                getString(R.string.settings_search_box_transition_name),
            )
            startActivity(
                Intent(this, SettingsSearchActivity::class.java),
                options.toBundle(),
            )
        }
    }

    /** 切换一级页面；[scrollKey] 非空时滚动到该设置项（用于设置搜索跳转）。 */
    private fun switchTab(index: Int, scrollKey: String? = null) {
        if (index !in TABS.indices) return
        binding.bottomBar.selectTab(index, notify = false)
        binding.homeTitle.setText(TITLE_RES[index])

        if (index == currentIndex) {
            scrollTo(scrollKey)
            return
        }

        val manager = supportFragmentManager
        if (manager.isStateSaved) return

        val tag = tagOf(index)
        val transaction = manager.beginTransaction()
        if (currentIndex >= 0) {
            manager.findFragmentByTag(tagOf(currentIndex))?.let(transaction::hide)
        }
        val fragment = manager.findFragmentByTag(tag)
            ?: createFragment(index).also { transaction.add(R.id.homeFragmentContainer, it, tag) }
        transaction.show(fragment)
        transaction.commitNow()

        currentIndex = index
        scrollTo(scrollKey)
    }

    private fun scrollTo(scrollKey: String?) {
        if (scrollKey == null || currentIndex < 0) return
        val fragment = supportFragmentManager.findFragmentByTag(tagOf(currentIndex))
        (fragment as? BaseHomeFragment)?.scrollToPreferenceKey(scrollKey)
    }

    private fun handleNavigateIntent(intent: Intent?) {
        val key = intent
            ?.getStringExtra(SettingsSearchNavigator.EXTRA_TARGET_PREFERENCE_KEY)
            ?: return
        intent.removeExtra(SettingsSearchNavigator.EXTRA_TARGET_PREFERENCE_KEY)
        val tab = PREFERENCE_TAB[key] ?: return
        switchTab(tab, scrollKey = key)
    }

    private fun createFragment(index: Int): Fragment = when (index) {
        TAB_GESTURE -> GestureHomeFragment()
        TAB_SCREEN -> ScreenHomeFragment()
        TAB_SEARCH -> SearchHomeFragment()
        else -> SettingsHomeFragment()
    }

    private fun tagOf(index: Int) = "home_tab_$index"

    private companion object {
        const val KEY_TAB_INDEX = "home_tab_index"

        const val TAB_GESTURE = 0
        const val TAB_SCREEN = 1
        const val TAB_SEARCH = 2
        const val TAB_SETTINGS = 3

        val TABS = listOf(
            FloatingBottomBar.Tab(R.drawable.baseline_gesture_24, R.string.home_tab_gesture),
            FloatingBottomBar.Tab(
                R.drawable.outline_center_focus_weak_24,
                R.string.home_tab_screen,
            ),
            FloatingBottomBar.Tab(R.drawable.search_24px, R.string.home_tab_search),
            FloatingBottomBar.Tab(R.drawable.baseline_settings_24, R.string.home_tab_settings),
        )

        val TITLE_RES = intArrayOf(
            R.string.home_title_gesture,
            R.string.home_title_screen,
            R.string.home_title_search,
            R.string.home_title_settings,
        )

        /**
         * 设置项 key 到一级页面的映射，供设置搜索定位使用。
         * 屏幕页/搜索页已改为干净菜单：设置项深链直达原版设置页
         * （InspireSettingsActivity / SearchEngineSettingsActivity）并展开对应分区卡，
         * 不再经过一级页面；这里只保留一级页面自身的行与手势/设置页条目。
         */
        val PREFERENCE_TAB = mapOf(
            // 手势页
            "preferenceService" to TAB_GESTURE,
            "preferenceConfigTrigger" to TAB_GESTURE,
            "preferenceActionView" to TAB_GESTURE,
            "preferenceFreeform" to TAB_GESTURE,
            // 屏幕页（根入口 + 菜单条目）
            "root_inspire" to TAB_SCREEN,
            "menu_screen_recognition" to TAB_SCREEN,
            "menu_screen_share" to TAB_SCREEN,
            "menu_screen_pointer_keyboard" to TAB_SCREEN,
            "menu_screen_notification" to TAB_SCREEN,
            // 搜索页（根入口 + 菜单条目）
            "root_search" to TAB_SEARCH,
            "menu_search_engines" to TAB_SEARCH,
            "menu_search_settings" to TAB_SEARCH,
            "menu_search_import" to TAB_SEARCH,
            "menu_search_export" to TAB_SEARCH,
            // 设置页
            "preferenceAppearance" to TAB_SETTINGS,
            "preferenceData" to TAB_SETTINGS,
            "preferenceAdvanced" to TAB_SETTINGS,
            "preferenceBackgroundStability" to TAB_SETTINGS,
            "preferenceAbout" to TAB_SETTINGS,
        )
    }
}
