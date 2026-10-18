package io.github.lumiagg.gestureevo.ui.home

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.core.content.withStyledAttributes
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.button.MaterialButton
import io.github.lumiagg.gestureevo.R

/**
 * 一级页面顶部的主色状态卡：图标 + 标题 + 动态摘要 + 动作按钮。
 *
 * 标题与摘要走标准 Preference 通道（[setTitle] / [setSummary]），
 * 图标走 [setIcon]；动作按钮文字由 XML 的 `statusCardActionLabel` 提供，
 * 点击回调通过 [onActionClick] 设置。
 */
class HomeStatusCardPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : Preference(context, attrs) {

    /** 动作按钮点击回调；整卡点击仍走 [setOnPreferenceClickListener]。 */
    var onActionClick: (() -> Unit)? = null

    private var actionLabel: CharSequence? = null

    init {
        layoutResource = R.layout.item_home_status_card
        isSelectable = true
        context.withStyledAttributes(attrs, R.styleable.HomeStatusCardPreference) {
            actionLabel = getText(R.styleable.HomeStatusCardPreference_statusCardActionLabel)
        }
    }

    /** 更新动作按钮文字；传 null 时隐藏按钮。 */
    fun setActionLabel(label: CharSequence?) {
        actionLabel = label
        notifyChanged()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val button = holder.findViewById(R.id.statusActionButton) as? MaterialButton
        if (actionLabel.isNullOrBlank()) {
            button?.visibility = android.view.View.GONE
        } else {
            button?.visibility = android.view.View.VISIBLE
            button?.text = actionLabel
            button?.setOnClickListener { onActionClick?.invoke() }
        }
        // 摘要允许两行，避免长状态文案被截断。
        (holder.findViewById(android.R.id.summary) as? TextView)?.maxLines = 2
    }
}
