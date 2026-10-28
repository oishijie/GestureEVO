package io.github.lumiagg.gestureevo.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.utils.dp2px

/**
 * 悬浮胶囊底栏：脱离屏幕底边、悬浮于内容之上的 MD3 底部导航。
 *
 * 所有颜色都取自主题 token，因此会随系统动态取色或外观设置中的预设配色方案自动变化。
 */
class FloatingBottomBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : MaterialCardView(context, attrs, defStyleAttr) {

    data class Tab(@DrawableRes val iconRes: Int, @StringRes val labelRes: Int)

    /** 用户点击切换时回调；以代码方式调用 [selectTab] 不会触发。 */
    var onTabSelected: ((Int) -> Unit)? = null

    private val row = LinearLayout(context)
    private val icons = mutableListOf<ImageView>()
    private val labels = mutableListOf<TextView>()
    private var tabCount = 0
    private var selectedIndex = -1

    init {
        radius = CORNER_RADIUS_DP.dp2px
        // 页底已统一为 colorSurface：白胶囊改由 1dp 细描边勾形，
        // 不再依赖「白浮在浅灰底上」的底色差。
        strokeWidth = 1f.dp2px.toInt().coerceAtLeast(1)
        strokeColor = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorOutlineVariant,
            Color.TRANSPARENT,
        )
        cardElevation = ELEVATION_DP.dp2px
        maxCardElevation = ELEVATION_DP.dp2px
        useCompatPadding = false
        // 白胶囊皮肤：底取 colorSurface，与白页底同色，靠描边与投影立形；
        // 选中项用主色高亮，不再画指示器色块。
        setCardBackgroundColor(
            MaterialColors.getColor(
                this,
                com.google.android.material.R.attr.colorSurface,
                Color.WHITE,
            )
        )

        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val padH = 6f.dp2px.toInt()
        row.setPadding(padH, 4f.dp2px.toInt(), padH, 4f.dp2px.toInt())
        addView(
            row,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    /** 提交 tab 定义。重复调用会清空已构建的 tab，因此可以安全地在界面重建时再次调用。 */
    fun submitTabs(tabs: List<Tab>, selected: Int = 0) {
        row.removeAllViews()
        icons.clear()
        labels.clear()
        tabCount = tabs.size
        val inflater = LayoutInflater.from(context)
        tabs.forEachIndexed { index, tab ->
            val item = inflater.inflate(R.layout.item_bottom_bar_tab, row, false)
            val icon = item.findViewById<ImageView>(R.id.tabIcon)
            val label = item.findViewById<TextView>(R.id.tabLabel)
            icon.setImageResource(tab.iconRes)
            label.setText(tab.labelRes)
            // 图标与文字对无障碍隐藏，由整行统一播报 tab 名称。
            item.contentDescription = context.getString(tab.labelRes)
            item.setOnClickListener { selectTab(index, notify = true) }
            row.addView(
                item,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            icons += icon
            labels += label
        }
        selectedIndex = -1
        if (tabs.isNotEmpty()) {
            selectTab(selected.coerceIn(0, tabs.lastIndex), notify = false)
        }
    }

    /** 选中指定 tab。仅当选中项发生变化且 [notify] 为 true 时回调 [onTabSelected]。 */
    fun selectTab(index: Int, notify: Boolean = false) {
        if (index !in 0 until tabCount) return
        val changed = selectedIndex != index
        selectedIndex = index
        for (i in 0 until tabCount) {
            applyTabState(i, i == index)
        }
        if (notify && changed) onTabSelected?.invoke(index)
    }

    val currentTab: Int get() = selectedIndex

    private fun applyTabState(index: Int, selected: Boolean) {
        val icon = icons.getOrNull(index) ?: return
        val label = labels.getOrNull(index) ?: return
        val activeColor = MaterialColors.getColor(
            this,
            android.R.attr.colorPrimary,
            Color.WHITE,
        )
        val inactiveColor = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
            Color.GRAY,
        )

        val tint = if (selected) activeColor else inactiveColor
        icon.imageTintList = ColorStateList.valueOf(tint)
        label.setTextColor(tint)
        // 新版皮肤不画选中指示器色块，仅用主色 + 字重区分选中/未选中。
        icon.background = null
        icon.isSelected = selected
        label.isSelected = selected
        label.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
        // 供无障碍服务播报「已选中」状态。
        row.getChildAt(index)?.isSelected = selected
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        for (i in 0 until row.childCount) {
            row.getChildAt(i).isEnabled = enabled
        }
    }

    private companion object {
        const val CORNER_RADIUS_DP = 28f
        const val ELEVATION_DP = 6f
    }
}
