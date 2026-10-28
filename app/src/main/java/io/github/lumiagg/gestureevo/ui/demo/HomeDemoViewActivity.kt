package io.github.lumiagg.gestureevo.ui.demo

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.databinding.ActivityHomeDemoViewBinding
import io.github.lumiagg.gestureevo.databinding.ItemDemoRowBinding
import io.github.lumiagg.gestureevo.gesture.config.GestureActionDataManager
import io.github.lumiagg.gestureevo.settings.ActionViewSettingsActivity
import io.github.lumiagg.gestureevo.settings.AdvancedSettingsActivity
import io.github.lumiagg.gestureevo.settings.AppearanceSettingsActivity
import io.github.lumiagg.gestureevo.settings.BackgroundStabilityActivity
import io.github.lumiagg.gestureevo.settings.DataBackActivity
import io.github.lumiagg.gestureevo.settings.FreeFormSettingsActivity
import io.github.lumiagg.gestureevo.settings.FreeTriggerSettingsActivity
import io.github.lumiagg.gestureevo.settings.InspireSettingsActivity
import io.github.lumiagg.gestureevo.settings.SearchEngineSettingsActivity
import io.github.lumiagg.gestureevo.settings.SettingsSearchNavigator
import io.github.lumiagg.gestureevo.settings.SetupChecklist
import io.github.lumiagg.gestureevo.settings.evoAccessibilityServiceIsEnable
import io.github.lumiagg.gestureevo.settings.ui.AboutActivity
import io.github.lumiagg.gestureevo.utils.dp2px
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 首页重构 Demo A（View 实现）。
 *
 * 结构：折叠大标题 + 状态 hero 卡 + 四组「组标题在外、行在卡内」的列表 + M3 底栏。
 * 底栏点击滚动到对应分组，用于与 Compose 版（[HomeDemoComposeActivity]）对比观感与手感。
 * 仅作方案验证，不属于正式信息架构。
 */
class HomeDemoViewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeDemoViewBinding
    private val sectionAnchors = mutableListOf<View>()

    private data class DemoRow(
        val titleRes: Int,
        val subtitle: String?,
        val iconRes: Int,
        val onClick: () -> Unit,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeDemoViewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setupInsets()
        setSupportActionBar(binding.toolbar)
        binding.collapsingToolbar.title = getString(R.string.app_name)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.heroCard.setOnClickListener {
            startActivity(Intent(this, BackgroundStabilityActivity::class.java))
        }
        binding.heroAction.setOnClickListener {
            startActivity(Intent(this, BackgroundStabilityActivity::class.java))
        }

        buildSections()
        setupBottomNav()
        loadStatus()
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBar) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNav) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = bars.bottom)
            insets
        }
    }

    private fun loadStatus() {
        lifecycleScope.launch {
            val pending = withContext(Dispatchers.IO) { SetupChecklist.pendingCount(this@HomeDemoViewActivity) }
            val accessibility = withContext(Dispatchers.IO) { evoAccessibilityServiceIsEnable(this@HomeDemoViewActivity) }
            val wheels = withContext(Dispatchers.IO) {
                runCatching {
                    GestureActionDataManager.readActionListTags(this@HomeDemoViewActivity).size
                }.getOrDefault(0)
            }
            binding.heroValue.text = if (pending > 0) {
                getString(R.string.demo_hero_pending, pending)
            } else {
                getString(R.string.demo_hero_done)
            }
            binding.heroAction.visibility = if (pending > 0) View.VISIBLE else View.GONE
            val accessText = getString(
                if (accessibility) R.string.demo_hero_accessibility_on
                else R.string.demo_hero_accessibility_off
            )
            binding.heroSummary.text = getString(R.string.home_badge_wheel_count, wheels) + " · " + accessText
        }
    }

    private fun buildSections() {
        val accessibility = runCatching { evoAccessibilityServiceIsEnable(this) }.getOrDefault(false)
        val pending = runCatching { SetupChecklist.pendingCount(this) }.getOrDefault(0)

        // 手势
        addSection(
            R.string.home_tab_gesture,
            listOf(
                DemoRow(
                    R.string.home_status_service_title,
                    getString(
                        if (accessibility) R.string.home_badge_accessibility_on
                        else R.string.home_badge_accessibility_off
                    ),
                    R.drawable.icon_service,
                ) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                DemoRow(R.string.settings_gesture_title, getString(R.string.home_summary_gesture), R.drawable.baseline_gesture_24) {
                    startActivity(Intent(this, FreeTriggerSettingsActivity::class.java))
                },
                DemoRow(R.string.action_view_settings_title, getString(R.string.home_summary_action_view), R.drawable.baseline_pie_chart_24) {
                    startActivity(Intent(this, ActionViewSettingsActivity::class.java))
                },
                DemoRow(R.string.freeform_settings_title, getString(R.string.home_summary_freeform), R.drawable.ic_little_window) {
                    startActivity(Intent(this, FreeFormSettingsActivity::class.java))
                },
            ),
        )

        // 工具
        addSection(
            R.string.home_tab_screen,
            listOf(
                DemoRow(R.string.home_menu_recognition_title, getString(R.string.home_menu_recognition_summary), R.drawable.baseline_qr_code_scanner_24) {
                    openInspire(R.id.screenContentSwitchCard)
                },
                DemoRow(R.string.home_menu_share_title, getString(R.string.home_menu_share_summary), R.drawable.ic_baseline_share_24) {
                    openInspire(R.id.defaultShareBehaviorCard, R.id.appSelectionCard)
                },
                DemoRow(R.string.home_menu_pointer_keyboard_title, getString(R.string.home_menu_pointer_keyboard_summary), R.drawable.baseline_keyboard_24) {
                    openInspire(R.id.PointerCard, R.id.pinPoolCard, R.id.keyboardTypeCard)
                },
                DemoRow(R.string.home_menu_notification_title, getString(R.string.home_menu_notification_summary), R.drawable.baseline_circle_notifications_24) {
                    openInspire(R.id.notificationCard)
                },
            ),
        )

        // 搜索
        addSection(
            R.string.home_tab_search,
            listOf(
                DemoRow(R.string.home_menu_engines_title, getString(R.string.home_menu_engines_summary), R.drawable.baseline_search_24) {
                    startActivity(
                        SettingsSearchNavigator.deepLinkIntent(
                            this,
                            SearchEngineSettingsActivity::class.java,
                            R.id.engineListCard,
                        )
                    )
                },
                DemoRow(R.string.home_menu_search_settings_title, getString(R.string.home_menu_search_settings_summary), R.drawable.baseline_settings_24) {
                    startActivity(
                        SettingsSearchNavigator.deepLinkIntent(
                            this,
                            SearchEngineSettingsActivity::class.java,
                            R.id.displayCard,
                            R.id.functionCard,
                            R.id.startupCard,
                        )
                    )
                },
                DemoRow(R.string.demo_row_search_io, getString(R.string.search_engine_settings_import_summary), R.drawable.ic_baseline_content_copy_24) {
                    startActivity(Intent(this, SearchEngineSettingsActivity::class.java))
                },
            ),
        )

        // 设置
        addSection(
            R.string.home_tab_settings,
            listOf(
                DemoRow(R.string.home_status_theme_title, null, R.drawable.baseline_auto_awesome_24) {
                    startActivity(Intent(this, AppearanceSettingsActivity::class.java))
                },
                DemoRow(
                    R.string.setup_check_entry_title,
                    if (pending > 0) getString(R.string.setup_check_entry_summary_pending, pending)
                    else getString(R.string.setup_check_entry_summary_done),
                    R.drawable.icon_lock,
                ) { startActivity(Intent(this, BackgroundStabilityActivity::class.java)) },
                DemoRow(R.string.data_backup_title, getString(R.string.home_summary_data), R.drawable.baseline_save_24) {
                    startActivity(Intent(this, DataBackActivity::class.java))
                },
                DemoRow(R.string.advanced_settings_title, getString(R.string.home_summary_advanced), R.drawable.baseline_settings_24) {
                    startActivity(Intent(this, AdvancedSettingsActivity::class.java))
                },
                DemoRow(R.string.settings_about_title, getString(R.string.home_summary_about), R.drawable.baseline_info_24) {
                    startActivity(Intent(this, AboutActivity::class.java))
                },
            ),
        )
    }

    private fun openInspire(vararg cardIds: Int) {
        startActivity(
            SettingsSearchNavigator.deepLinkIntent(
                this,
                InspireSettingsActivity::class.java,
                *cardIds,
            )
        )
    }

    private fun addSection(titleRes: Int, rows: List<DemoRow>) {
        val content = binding.demoContent
        val onSurfaceVariant = MaterialColors.getColor(
            content,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
        )

        val header = TextView(this).apply {
            setText(titleRes)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.evo_text_body))
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(onSurfaceVariant)
            setPadding(4.dp2px.toInt(), 20.dp2px.toInt(), 0, 8.dp2px.toInt())
        }
        content.addView(header)
        sectionAnchors += header

        val card = MaterialCardView(this).apply {
            radius = resources.getDimension(R.dimen.shape_corner_card)
            setCardBackgroundColor(
                MaterialColors.getColor(
                    content,
                    com.google.android.material.R.attr.colorSurfaceContainerLow,
                )
            )
            strokeWidth = 0
            cardElevation = 0f
            setContentPadding(0, 0, 0, 0)
        }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        rows.forEachIndexed { index, row ->
            if (index > 0) {
                val divider = View(this).apply {
                    setBackgroundColor(
                        MaterialColors.getColor(
                            content,
                            com.google.android.material.R.attr.colorOutlineVariant,
                        )
                    )
                }
                column.addView(
                    divider,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        1.dp2px.toInt(),
                    ).apply { marginStart = 66.dp2px.toInt() },
                )
            }
            column.addView(buildRow(row))
        }
        card.addView(column)
        content.addView(
            card,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    private fun buildRow(row: DemoRow): View {
        val rowBinding = ItemDemoRowBinding.inflate(LayoutInflater.from(this))
        rowBinding.rowTitle.setText(row.titleRes)
        rowBinding.rowSubtitle.text = row.subtitle.orEmpty()
        rowBinding.rowSubtitle.visibility = if (row.subtitle.isNullOrBlank()) View.GONE else View.VISIBLE

        val container = binding.demoContent
        val tint = MaterialColors.getColor(
            container,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
        )
        rowBinding.rowIcon.setImageResource(row.iconRes)
        rowBinding.rowIcon.imageTintList = ColorStateList.valueOf(tint)
        rowBinding.rowIcon.background = GradientDrawable().apply {
            cornerRadius = 10.dp2px
            setColor(
                MaterialColors.getColor(
                    container,
                    com.google.android.material.R.attr.colorSurfaceContainerHighest,
                )
            )
        }
        rowBinding.root.setOnClickListener { row.onClick() }
        return rowBinding.root
    }

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            val index = when (item.itemId) {
                R.id.tab_gesture -> 0
                R.id.tab_tools -> 1
                R.id.tab_search -> 2
                R.id.tab_settings -> 3
                else -> 0
            }
            sectionAnchors.getOrNull(index)?.let { anchor ->
                binding.demoScroll.smoothScrollTo(0, anchor.top)
            }
            true
        }
    }
}
