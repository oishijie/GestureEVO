package io.github.lumiagg.gestureevo.ui.home

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.color.MaterialColors
import com.google.android.material.R as MaterialR
import io.github.lumiagg.gestureevo.R

/**
 * 一级页入口卡：标题 + 摘要 + 可选状态徽章。
 *
 * 徽章文字通过 [setBadge] 设置，背景与文字色按主题 token 程序化上色：
 * 正常态（计数/已开启）取 colorSecondaryContainer / colorOnSecondaryContainer，
 * 待处理态取 colorErrorContainer / colorOnErrorContainer，因此会随壁纸取色变化。
 * 徽章为空时隐藏，不影响普通条目的观感。
 */
class HomeEntryPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : Preference(context, attrs) {

    private var badgeText: CharSequence? = null
    private var badgeWarning: Boolean = false

    init {
        layoutResource = R.layout.item_home_entry_card
        isSelectable = true
    }

    /** 设置徽章文字；传空表示隐藏徽章。[warning] 为 true 时使用错误色（待处理态）。 */
    fun setBadge(text: CharSequence?, warning: Boolean = false) {
        val changed = badgeText != text || badgeWarning != warning
        badgeText = text
        badgeWarning = warning
        if (changed) notifyChanged()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val badge = holder.findViewById(R.id.entryBadge) as? TextView ?: return
        val text = badgeText
        if (text.isNullOrBlank()) {
            badge.visibility = TextView.GONE
            return
        }
        badge.visibility = TextView.VISIBLE
        badge.text = text
        badge.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 999f
            setColor(
                MaterialColors.getColor(
                    badge,
                    if (badgeWarning) MaterialR.attr.colorErrorContainer
                    else MaterialR.attr.colorSecondaryContainer,
                    android.graphics.Color.TRANSPARENT,
                )
            )
        }
        badge.setTextColor(
            MaterialColors.getColor(
                badge,
                if (badgeWarning) MaterialR.attr.colorOnErrorContainer
                else MaterialR.attr.colorOnSecondaryContainer,
                android.graphics.Color.GRAY,
            )
        )
        // 徽章左右各留 10dp、上下 3dp 内边距，与参考的饱满 pill 比例一致。
        val padH = (10 * badge.resources.displayMetrics.density).toInt()
        val padV = (3 * badge.resources.displayMetrics.density).toInt()
        badge.setPadding(padH, padV, padH, padV)
    }
}
