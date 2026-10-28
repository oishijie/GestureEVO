package io.github.lumiagg.gestureevo.ui.widget

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import io.github.lumiagg.gestureevo.utils.dp2px

/**
 * 状态 / hero 卡（《UI 设计规范》4.3）。
 *
 * primaryContainer 底、24dp 圆角、无描边；label 14sp + value 26sp/700 +
 * 可选 summary 14sp + 可选 Tonal 按钮。用于「一屏一个主角」的状态展示，
 * 例如外观页的当前主题、体检页的待处理项数。
 *
 * XML 中只声明 id，内容统一通过 [bind] 绑定，避免每页重复搭布局。
 */
class StatusCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : MaterialCardView(context, attrs, defStyleAttr) {

    private val iconView = ImageView(context)
    private val labelView = TextView(context)
    private val valueView = TextView(context)
    private val summaryView = TextView(context)
    private val actionButton = MaterialButton(context)
    private var onActionClick: (() -> Unit)? = null

    private val onContainerColor = MaterialColors.getColor(
        this,
        com.google.android.material.R.attr.colorOnPrimaryContainer,
        0,
    )

    init {
        radius = 24f.dp2px
        strokeWidth = 0
        cardElevation = 0f
        maxCardElevation = 0f
        useCompatPadding = false
        preventCornerOverlap = false
        setCardBackgroundColor(
            MaterialColors.getColor(
                this,
                com.google.android.material.R.attr.colorPrimaryContainer,
                0,
            )
        )

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = 20.dp2px.toInt()
            setPadding(pad, pad, pad, pad)
        }

        iconView.apply {
            layoutParams = LinearLayout.LayoutParams(20.dp2px.toInt(), 20.dp2px.toInt())
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            imageTintList = ColorStateList.valueOf(onContainerColor)
            isVisible = false
        }

        labelView.apply {
            textSize = 14f
            setTextColor(onContainerColor)
            setPadding(8.dp2px.toInt(), 0, 0, 0)
        }

        valueView.apply {
            textSize = 26f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(onContainerColor)
            setPadding(0, 6.dp2px.toInt(), 0, 0)
        }

        summaryView.apply {
            textSize = 14f
            setTextColor(onContainerColor)
            alpha = 0.8f
            setPadding(0, 6.dp2px.toInt(), 0, 0)
            isVisible = false
        }

        actionButton.apply {
            textSize = 14f
            isVisible = false
            setOnClickListener { onActionClick?.invoke() }
        }

        val labelRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        labelRow.addView(iconView)
        labelRow.addView(labelView)

        container.addView(labelRow)
        container.addView(valueView)
        container.addView(summaryView)
        container.addView(
            actionButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = 12.dp2px.toInt() },
        )
        addView(container)
    }

    /** 绑定内容；[iconRes] / [summary] / [actionLabel] 为 null 时不展示对应元素。 */
    fun bind(
        iconRes: Int?,
        label: CharSequence,
        value: CharSequence,
        summary: CharSequence? = null,
        actionLabel: CharSequence? = null,
        onAction: (() -> Unit)? = null,
    ) {
        if (iconRes != null) {
            iconView.setImageResource(iconRes)
            iconView.isVisible = true
        } else {
            iconView.isVisible = false
        }
        labelView.text = label
        valueView.text = value

        summaryView.text = summary?.toString().orEmpty()
        summaryView.isVisible = !summary.isNullOrBlank()

        onActionClick = onAction
        if (!actionLabel.isNullOrBlank()) {
            actionButton.text = actionLabel
            actionButton.isVisible = true
        } else {
            actionButton.isVisible = false
        }
    }

    /** 仅更新主值（例如待处理项数变化时）。 */
    fun setValue(value: CharSequence) {
        valueView.text = value
    }

    fun setSummary(summary: CharSequence?) {
        summaryView.text = summary?.toString().orEmpty()
        summaryView.isVisible = !summary.isNullOrBlank()
    }
}
