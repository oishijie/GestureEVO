package io.github.lumiagg.gestureevo.settings.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.withStyledAttributes
import androidx.core.view.isGone
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.utils.DynamicColorUtils
import io.github.lumiagg.gestureevo.utils.dp2px

/**
 * 分组块：外置分组标题 + 扁平分组容器。
 *
 * 新版二级页规范：标题/描述移到卡片上方（不再支持折叠），组内子项为扁平行，
 * 行与行之间用 1dp 分隔线分隔。历史折叠 API（[setExpanded]/[isExpanded]）保留为
 * no-op，旧调用点无需改动即可得到「常驻展开」行为。
 */
class SettingsSectionCard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    private val titleView: TextView
    private val summaryView: TextView
    private val contentContainer: LinearLayout
    private var internalAdd = false

    init {
        orientation = VERTICAL
        var title: CharSequence = ""
        var summary: CharSequence = ""
        context.withStyledAttributes(attrs, R.styleable.SettingsSectionCard) {
            title = getText(R.styleable.SettingsSectionCard_settingsCardTitle) ?: ""
            summary = getText(R.styleable.SettingsSectionCard_settingsCardSummary) ?: ""
        }

        titleView = TextView(context).apply {
            text = title
            // 分区标签用主色区分层级：字号与行标题持平（16sp），靠颜色而非法号
            // 拉开层次，避免整页出现两种不相干的字号。
            textSize = 16f
            setTextColor(DynamicColorUtils.primaryColor)
            setTypeface(typeface, android.graphics.Typeface.NORMAL)
            setPadding(8f.dp2px.toInt(), 0, 8f.dp2px.toInt(), 0)
        }

        summaryView = TextView(context).apply {
            text = summary
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setTextColor(DynamicColorUtils.onSurfaceVariantColor)
            isGone = summary.isBlank()
            setPadding(8f.dp2px.toInt(), 2f.dp2px.toInt(), 8f.dp2px.toInt(), 0)
        }

        // 白卡 + 1dp 细描边：卡底与页底同为 colorSurface，靠描边勾出形状，
        // 不再用 surfaceContainer 染色，避免高饱和配色下整页发彩。
        val surfaceColor = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorSurface,
            0,
        )
        val hairlineColor = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorOutlineVariant,
            0,
        )
        val card = MaterialCardView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = 10f.dp2px.toInt()
            }
            // 圆角与原生卡（@dimen/settings_card_corner_radius）保持一致。
            radius = 16f.dp2px.toFloat()
            strokeWidth = 1f.dp2px.toInt().coerceAtLeast(1)
            strokeColor = hairlineColor
            cardElevation = 0f
            maxCardElevation = 0f
            elevation = 0f
            translationZ = 0f
            stateListAnimator = null
            useCompatPadding = false
            preventCornerOverlap = false
            setCardBackgroundColor(surfaceColor)
        }
        contentContainer = LinearLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            orientation = VERTICAL
            val padH = 20f.dp2px.toInt()
            val padV = 6f.dp2px.toInt()
            setPadding(padH, padV, padH, padV)
        }
        // 结构搭建阶段走 super.addView，避免被自身 override 收进 contentContainer。
        internalAdd = true
        super.addView(titleView)
        super.addView(summaryView)
        card.addView(contentContainer)
        super.addView(card)
        internalAdd = false
    }

    /** @deprecated 折叠已移除，保留兼容旧调用点，恒为展开态。 */
    @Deprecated("折叠已移除，分组常驻展开", ReplaceWith(""))
    fun setExpanded(expanded: Boolean) = Unit

    /** @deprecated 折叠已移除，保留兼容旧调用点，恒为展开态。 */
    @Deprecated("折叠已移除，分组常驻展开", ReplaceWith(""))
    fun setExpanded(expanded: Boolean, animate: Boolean) = Unit

    fun isExpanded(): Boolean = true

    fun setTitle(title: CharSequence) {
        titleView.text = title
        titleView.isGone = title.isBlank()
    }

    fun setSummary(summary: CharSequence?) {
        summaryView.text = summary ?: ""
        summaryView.isGone = summary.isNullOrBlank()
    }

    override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams) {
        if (internalAdd) {
            super.addView(child, index, params)
            return
        }
        if (child is LinearLayout && child.id == View.NO_ID && child.orientation == VERTICAL) {
            // 历史布局里卡内容常被一层「仅做内边距」的 LinearLayout 包裹，
            // 扁平化时把这一层透明化：提升其直接子 view 为内容行，分隔线才能落在行间。
            val kids = (0 until child.childCount).map { child.getChildAt(it) }
            child.removeAllViews()
            kids.forEach { kid ->
                contentContainer.addView(kid, -1, kid.layoutParams ?: LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            }
        } else {
            contentContainer.addView(child, index, params)
        }
        contentContainer.post {
            hideDuplicatedHeaderText(contentContainer)
            relayoutDividers()
        }
    }

    /** 重新排布分隔线：相邻可见行之间 1dp 细线，跳过 GONE 行与控件块。 */
    private fun relayoutDividers() {
        (0 until contentContainer.childCount)
            .map { contentContainer.getChildAt(it) }
            .filter { it.tag == DIVIDER_TAG }
            .forEach { contentContainer.removeView(it) }
        val items = (0 until contentContainer.childCount)
            .map { contentContainer.getChildAt(it) }
            .filter { it.tag != DIVIDER_TAG && it.visibility != GONE }
            .onEach { row ->
                // 扁平行不保留外边距，分隔线才能与行内容贴齐。
                if (row is SettingsNavRow || row is SettingsSwitchRow || row is SettingsDropdownRow) {
                    (row.layoutParams as? MarginLayoutParams)?.let {
                        it.topMargin = 0
                        it.bottomMargin = 0
                        row.layoutParams = it
                    }
                }
            }
        for (i in 1 until items.size) {
            val previous = items[i - 1]
            val current = items[i]
            // 只有扁平行之间才画分隔线：滑块组、输入框等复合块内部不插线，
            // 否则标签和它的滑块之间会凭空多出一条线。
            if (!isDividerRow(previous) || !isDividerRow(current)) continue
            val divider = dividerView()
            contentContainer.addView(divider, contentContainer.indexOfChild(current))
        }
    }

    private fun isDividerRow(view: View): Boolean =
        view is SettingsNavRow || view is SettingsSwitchRow || view is SettingsDropdownRow

    private fun dividerView(): View {
        val lineColor = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorOutlineVariant,
            0,
        )
        val alpha = 0x33 shl 24
        return View(context).apply {
            tag = DIVIDER_TAG
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 1f.dp2px.toInt().coerceAtLeast(1))
            background = GradientDrawable().apply {
                setColor(alpha or (lineColor and 0xFFFFFF))
            }
        }
    }

    private fun hideDuplicatedHeaderText(root: View) {
        val duplicatedTexts = linkedSetOf(
            titleView.text?.toString().orEmpty(),
            summaryView.text?.toString().orEmpty()
        ).filterTo(LinkedHashSet()) { it.isNotBlank() }
        if (duplicatedTexts.isEmpty()) return

        fun visit(view: View) {
            if (duplicatedTexts.isEmpty()) return
            // 只去掉历史遗留的重复小标题，绝不隐藏交互控件内部（导航行/开关行/按钮）的文字。
            if (view is SettingsNavRow || view is SettingsSwitchRow || view is SettingsDropdownRow ||
                view is android.widget.Button || view is android.widget.CompoundButton) return
            if (view is TextView) {
                val text = view.text?.toString().orEmpty()
                if (duplicatedTexts.remove(text)) {
                    view.visibility = GONE
                    return
                }
            }
            if (view is ViewGroup) {
                for (childIndex in 0 until view.childCount) {
                    visit(view.getChildAt(childIndex))
                    if (duplicatedTexts.isEmpty()) return
                }
            }
        }

        visit(root)
    }

    private companion object {
        const val DIVIDER_TAG = "settings_section_divider"
    }
}
