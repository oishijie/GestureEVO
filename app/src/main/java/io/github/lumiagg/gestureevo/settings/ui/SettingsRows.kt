package io.github.lumiagg.gestureevo.settings.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.withStyledAttributes
import androidx.core.view.isGone
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.color.MaterialColors
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.utils.DynamicColorUtils
import io.github.lumiagg.gestureevo.utils.dp2px

class SettingsNavRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val valueSlotIndex: Int
    private val swatchView: View?
    private val chevronView: ImageView
    private val titleView: TextView
    private var flat: Boolean = false
    private var building = true

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = 64f.dp2px.toInt()
        val padH = 14f.dp2px.toInt()
        val padV = 10f.dp2px.toInt()
        setPadding(padH, padV, padH, padV)
        setBackgroundResource(R.drawable.bg_settings_tile)
        isClickable = true
        isFocusable = true

        var title = ""
        var summary = ""
        var leadingIcon = 0
        var trailingIcon = R.drawable.ic_chevron_right
        var showSwatch = false
        var showChevron = true
        context.withStyledAttributes(attrs, R.styleable.SettingsNavRow) {
            title = getText(R.styleable.SettingsNavRow_settingsRowTitle)?.toString().orEmpty()
            summary = getText(R.styleable.SettingsNavRow_settingsRowSummary)?.toString().orEmpty()
            leadingIcon = getResourceId(R.styleable.SettingsNavRow_settingsRowIcon, 0)
            trailingIcon = getResourceId(
                R.styleable.SettingsNavRow_settingsRowTrailingIcon,
                R.drawable.ic_chevron_right,
            )
            showSwatch = getBoolean(R.styleable.SettingsNavRow_settingsRowSwatch, false)
            showChevron = getBoolean(R.styleable.SettingsNavRow_settingsRowShowChevron, true)
            flat = getBoolean(R.styleable.SettingsNavRow_settingsRowFlat, false)
        }

        // 扁平模式（新版二级页规范）：无瓷砖背景、行内分隔线由分组容器绘制，
        // 标题加大加粗、圆钮放大为 42dp。
        if (flat) {
            background = null
            minimumHeight = 64f.dp2px.toInt()
            // 水平留白由分组容器统一给（20dp），行内不再叠加，
            // 否则文字会离卡边 40dp，整行显得又窄又飘。
            val flatPadH = 0
            val flatPadV = 12f.dp2px.toInt()
            setPadding(flatPadH, flatPadV, flatPadH, flatPadV)
            foreground = context.obtainStyledAttributes(
                intArrayOf(android.R.attr.selectableItemBackground)
            ).let {
                val drawable = it.getDrawable(0)
                it.recycle()
                drawable
            }
        }

        if (flat && leadingIcon != 0) {
            // 前导图标（规范 4.2，对齐 KernelSU 设置页）：24dp 裸图标，onSurface 着色。
            addView(
                ImageView(context).apply {
                    layoutParams = LayoutParams(24.dp2px.toInt(), 24.dp2px.toInt()).apply {
                        marginEnd = 16.dp2px.toInt()
                    }
                    setImageResource(leadingIcon)
                    imageTintList = ColorStateList.valueOf(DynamicColorUtils.onSurfaceColor)
                    importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
                }
            )
        }

        val textColumn = LinearLayout(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            orientation = VERTICAL
        }
        titleView = labelView(title, isTitle = true)
        textColumn.addView(titleView)
        textColumn.addView(labelView(summary, isTitle = false).apply {
            isGone = summary.isBlank()
        })
        addView(textColumn)

        swatchView = if (showSwatch) {
            View(context).apply {
                layoutParams = LayoutParams(28f.dp2px.toInt(), 28f.dp2px.toInt()).apply {
                    marginStart = 12f.dp2px.toInt()
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(DynamicColorUtils.surfaceContainerHighColor)
                    setStroke(
                        1f.dp2px.toInt().coerceAtLeast(1),
                        DynamicColorUtils.outlineColor,
                    )
                }
            }.also { addView(it) }
        } else {
            null
        }
        valueSlotIndex = childCount

        chevronView = ImageView(context).apply {
            if (flat) {
                // 扁平行：裸箭头（灰色细线），不再套染色圆钮。
                val size = 20f.dp2px.toInt()
                layoutParams = LayoutParams(size, size).apply {
                    marginStart = 12f.dp2px.toInt()
                }
                background = null
                imageTintList = ColorStateList.valueOf(DynamicColorUtils.onSurfaceVariantColor)
            } else {
                val size = 32f.dp2px.toInt()
                layoutParams = LayoutParams(size, size).apply {
                    marginStart = 12f.dp2px.toInt()
                }
                val inset = 6f.dp2px.toInt()
                setPadding(inset, inset, inset, inset)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(DynamicColorUtils.secondaryContainerColor)
                }
                imageTintList = ColorStateList.valueOf(
                    MaterialColors.getColor(
                        this,
                        com.google.android.material.R.attr.colorOnSecondaryContainer,
                        0,
                    )
                )
            }
            setImageResource(trailingIcon)
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        addView(chevronView)
        building = false
        // 步进器等自带尾部控件的行不需要导航箭头。
        chevronView.isGone = !showChevron
    }

    fun setTitle(text: CharSequence?) {
        titleView.text = text
    }

    fun setSwatchColor(color: Int) {
        val swatch = swatchView ?: return
        (swatch.background as? GradientDrawable)?.setColor(color)
    }

    override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams) {
        if (building) {
            super.addView(child, index, params)
            return
        }
        val chevronIndex = indexOfChild(chevronView).takeIf { it >= 0 } ?: childCount
        if (child is TextView) {
            child.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = 12f.dp2px.toInt()
            }
            child.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            // 值走中性深色常规字重，彩色只留给开关、滑块等交互控件。
            child.setTextColor(DynamicColorUtils.onSurfaceColor)
            child.maxLines = 2
            child.ellipsize = TextUtils.TruncateAt.END
            child.gravity = Gravity.END
            child.maxWidth = 168f.dp2px.toInt()
            val refresh = { child.isGone = child.text.isNullOrBlank() }
            child.doAfterTextChanged { refresh() }
            refresh()
            super.addView(child, valueSlotIndex.coerceAtMost(chevronIndex), child.layoutParams)
            return
        }
        child.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            marginStart = 4f.dp2px.toInt()
        }
        super.addView(child, chevronIndex, child.layoutParams)
    }

    private fun labelView(text: String, isTitle: Boolean): TextView {
        return TextView(context).apply {
            this.text = text
            if (flat && isTitle) {
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.NORMAL)
            } else {
                setTextAppearance(
                    if (isTitle) {
                        com.google.android.material.R.style.TextAppearance_Material3_TitleSmall
                    } else {
                        com.google.android.material.R.style.TextAppearance_Material3_BodyMedium
                    }
                )
            }
            setTextColor(
                if (isTitle) DynamicColorUtils.onSurfaceColor
                else DynamicColorUtils.onSurfaceVariantColor
            )
            if (!isTitle) {
                val top = 2f.dp2px.toInt()
                setPadding(0, top, 0, 0)
            }
        }
    }
}

/**
 * 下拉选择行：标题 + 摘要在左，当前值 + 箭头在右（复刻主题设置页的子项形态）。
 * 点击通过 [onRowClick] 回调弹出选择面板；[setSwatchColor] 可在值前显示色点。
 */
class SettingsDropdownRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val valueView: TextView
    private val swatchView: View?
    private val chevronView: ImageView
    private var flat: Boolean = false
    private var building = true

    /** 行点击回调（弹出选择面板）。 */
    var onRowClick: (() -> Unit)? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = 56f.dp2px.toInt()
        val padH = 14f.dp2px.toInt()
        val padV = 10f.dp2px.toInt()
        setPadding(padH, padV, padH, padV)
        setBackgroundResource(R.drawable.bg_settings_tile)
        isClickable = true
        isFocusable = true

        var title = ""
        var summary = ""
        var leadingIcon = 0
        var showSwatch = false
        context.withStyledAttributes(attrs, R.styleable.SettingsDropdownRow) {
            title = getText(R.styleable.SettingsDropdownRow_settingsRowTitle)?.toString().orEmpty()
            summary = getText(R.styleable.SettingsDropdownRow_settingsRowSummary)?.toString().orEmpty()
            leadingIcon = getResourceId(R.styleable.SettingsDropdownRow_settingsRowIcon, 0)
            showSwatch = getBoolean(R.styleable.SettingsDropdownRow_settingsRowSwatch, false)
            flat = getBoolean(R.styleable.SettingsDropdownRow_settingsRowFlat, false)
        }

        if (flat) {
            background = null
            minimumHeight = 64f.dp2px.toInt()
            // 水平留白由分组容器统一给（20dp），行内不再叠加，
            // 否则文字会离卡边 40dp，整行显得又窄又飘。
            val flatPadH = 0
            val flatPadV = 12f.dp2px.toInt()
            setPadding(flatPadH, flatPadV, flatPadH, flatPadV)
            foreground = context.obtainStyledAttributes(
                intArrayOf(android.R.attr.selectableItemBackground)
            ).let {
                val drawable = it.getDrawable(0)
                it.recycle()
                drawable
            }
        }

        if (flat && leadingIcon != 0) {
            addView(
                ImageView(context).apply {
                    layoutParams = LayoutParams(24.dp2px.toInt(), 24.dp2px.toInt()).apply {
                        marginEnd = 16.dp2px.toInt()
                    }
                    setImageResource(leadingIcon)
                    imageTintList = ColorStateList.valueOf(DynamicColorUtils.onSurfaceColor)
                    importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
                }
            )
        }

        val textColumn = LinearLayout(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            orientation = VERTICAL
        }
        textColumn.addView(TextView(context).apply {
            text = title
            if (flat) {
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.NORMAL)
            } else {
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
            }
            setTextColor(DynamicColorUtils.onSurfaceColor)
        })
        textColumn.addView(TextView(context).apply {
            text = summary
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setTextColor(DynamicColorUtils.onSurfaceVariantColor)
            isGone = summary.isBlank()
            if (!isGone) setPadding(0, 2f.dp2px.toInt(), 0, 0)
        })
        addView(textColumn)

        swatchView = if (showSwatch) {
            View(context).apply {
                layoutParams = LayoutParams(18f.dp2px.toInt(), 18f.dp2px.toInt()).apply {
                    marginStart = 12f.dp2px.toInt()
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(DynamicColorUtils.primaryColor)
                    setStroke(
                        1f.dp2px.toInt().coerceAtLeast(1),
                        DynamicColorUtils.outlineColor,
                    )
                }
            }.also { addView(it) }
        } else {
            null
        }

        valueView = TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = 12f.dp2px.toInt()
            }
            if (flat) {
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.NORMAL)
                setTextColor(DynamicColorUtils.onSurfaceColor)
            } else {
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge)
                setTextColor(DynamicColorUtils.primaryColor)
            }
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.END
            maxWidth = 180f.dp2px.toInt()
        }
        addView(valueView)

        chevronView = ImageView(context).apply {
            if (flat) {
                val size = 20f.dp2px.toInt()
                layoutParams = LayoutParams(size, size).apply {
                    marginStart = 12f.dp2px.toInt()
                }
                background = null
                imageTintList = ColorStateList.valueOf(DynamicColorUtils.onSurfaceVariantColor)
            } else {
                layoutParams = LayoutParams(24f.dp2px.toInt(), 24f.dp2px.toInt()).apply {
                    marginStart = 8f.dp2px.toInt()
                }
                imageTintList = ColorStateList.valueOf(DynamicColorUtils.onSurfaceVariantColor)
            }
            setImageResource(R.drawable.ic_chevron_right)
            rotation = 90f
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        addView(chevronView)

        building = false
        setOnClickListener { onRowClick?.invoke() }
    }

    fun setValue(text: CharSequence?) {
        valueView.text = text ?: ""
    }

    fun setSwatchColor(color: Int) {
        val swatch = swatchView ?: return
        (swatch.background as? GradientDrawable)?.setColor(color)
    }
}

class SettingsSwitchRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private var switchView: CompoundButton? = null
    private var flat: Boolean = false
    private var building = true

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = 64f.dp2px.toInt()
        val padH = 14f.dp2px.toInt()
        val padV = 10f.dp2px.toInt()
        setPadding(padH, padV, padH, padV)
        setBackgroundResource(R.drawable.bg_settings_tile)
        isClickable = true
        isFocusable = true

        var title = ""
        var summary = ""
        var leadingIcon = 0
        context.withStyledAttributes(attrs, R.styleable.SettingsSwitchRow) {
            title = getText(R.styleable.SettingsSwitchRow_settingsRowTitle)?.toString().orEmpty()
            summary = getText(R.styleable.SettingsSwitchRow_settingsRowSummary)?.toString().orEmpty()
            leadingIcon = getResourceId(R.styleable.SettingsSwitchRow_settingsRowIcon, 0)
            flat = getBoolean(R.styleable.SettingsSwitchRow_settingsRowFlat, false)
        }

        if (flat) {
            background = null
            minimumHeight = 64f.dp2px.toInt()
            // 水平留白由分组容器统一给（20dp），行内不再叠加，
            // 否则文字会离卡边 40dp，整行显得又窄又飘。
            val flatPadH = 0
            val flatPadV = 12f.dp2px.toInt()
            setPadding(flatPadH, flatPadV, flatPadH, flatPadV)
            foreground = context.obtainStyledAttributes(
                intArrayOf(android.R.attr.selectableItemBackground)
            ).let {
                val drawable = it.getDrawable(0)
                it.recycle()
                drawable
            }
        }

        if (flat && leadingIcon != 0) {
            addView(
                ImageView(context).apply {
                    layoutParams = LayoutParams(24.dp2px.toInt(), 24.dp2px.toInt()).apply {
                        marginEnd = 16.dp2px.toInt()
                    }
                    setImageResource(leadingIcon)
                    imageTintList = ColorStateList.valueOf(DynamicColorUtils.onSurfaceColor)
                    importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
                }
            )
        }

        val textColumn = LinearLayout(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            orientation = VERTICAL
        }
        textColumn.addView(styledLabel(title, isTitle = true))
        if (summary.isNotBlank()) {
            textColumn.addView(styledLabel(summary, isTitle = false))
        }
        addView(textColumn)
        setOnClickListener { switchView?.performClick() }
        building = false
    }

    override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams) {
        if (building || child !is CompoundButton) {
            super.addView(child, index, params)
            return
        }
        switchView = child
        val switchParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            marginStart = 12f.dp2px.toInt()
        }
        super.addView(child, index, switchParams)
        // 行被禁用时子开关要跟着禁用，否则「整行置灰但开关还能点」。
        child.isEnabled = isEnabled
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        // 子开关同步禁用，并用透明度表达整体失效（母开关关闭时整组置灰）。
        switchView?.isEnabled = enabled
        alpha = if (enabled) 1f else 0.38f
    }

    private fun styledLabel(text: String, isTitle: Boolean): TextView {
        return TextView(context).apply {
            this.text = text
            if (flat && isTitle) {
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.NORMAL)
            } else {
                setTextAppearance(
                    if (isTitle) {
                        com.google.android.material.R.style.TextAppearance_Material3_TitleSmall
                    } else {
                        com.google.android.material.R.style.TextAppearance_Material3_BodySmall
                    }
                )
            }
            setTextColor(
                if (isTitle) DynamicColorUtils.onSurfaceColor
                else DynamicColorUtils.onSurfaceVariantColor
            )
            if (!isTitle) setPadding(0, 2f.dp2px.toInt(), 0, 0)
        }
    }
}
