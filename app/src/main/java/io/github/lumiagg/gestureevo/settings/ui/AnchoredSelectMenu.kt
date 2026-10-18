package io.github.lumiagg.gestureevo.settings.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListPopupWindow
import android.widget.TextView
import com.google.android.material.color.MaterialColors
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.utils.dp2px

/**
 * 贴着触发行右下角弹出的单选菜单（下拉气泡）。
 *
 * 形态参照系统主题设置：宽约 200dp、16dp 圆角、surfaceContainer 底 + 1dp 描边、
 * 行高 44dp、选中项主色文字 + 右侧对勾，不加遮罩，页面保持可见。
 */
fun showAnchoredSelectMenu(
    anchor: View,
    items: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    if (items.isEmpty()) return
    val context = anchor.context
    val popup = ListPopupWindow(context)
    val surface = MaterialColors.getColor(
        anchor,
        com.google.android.material.R.attr.colorSurfaceContainer,
        0,
    )
    val hairline = MaterialColors.getColor(
        anchor,
        com.google.android.material.R.attr.colorOutlineVariant,
        0,
    )
    val primary = MaterialColors.getColor(anchor, android.R.attr.colorPrimary, 0)
    val onSurface = MaterialColors.getColor(
        anchor,
        com.google.android.material.R.attr.colorOnSurface,
        0,
    )

    popup.anchorView = anchor
    popup.width = menuWidth(context)
    popup.height = ListPopupWindow.WRAP_CONTENT
    popup.isModal = true
    popup.setDropDownGravity(Gravity.END)
    popup.verticalOffset = 6f.dp2px.toInt()
    popup.setBackgroundDrawable(GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 16f.dp2px
        setColor(surface)
        setStroke(1f.dp2px.toInt().coerceAtLeast(1), hairline)
    })

    val adapter = MenuAdapter(
        context = context,
        items = items,
        selectedIndex = selectedIndex,
        primary = primary,
        onSurface = onSurface,
    ) { index ->
        popup.dismiss()
        onSelected(index)
    }
    popup.setAdapter(adapter)
    popup.show()
}

/** 菜单宽度：约 200dp，窄屏时收窄到屏宽 60%。 */
private fun menuWidth(context: Context): Int {
    val screen = context.resources.displayMetrics.widthPixels
    return minOf(200f.dp2px.toInt(), (screen * 0.6f).toInt())
}

private class MenuAdapter(
    private val context: Context,
    private val items: List<String>,
    private val selectedIndex: Int,
    private val primary: Int,
    private val onSurface: Int,
    private val onPick: (Int) -> Unit,
) : BaseAdapter() {

    private val rowHeight = 44f.dp2px.toInt()
    private val padH = 16f.dp2px.toInt()
    private val insetV = 8f.dp2px.toInt()

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): Any = items[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val row = (convertView as? LinearLayout) ?: LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = rowHeight
            isClickable = true
            isFocusable = true
            setBackgroundResource(rippleRes())
        }
        row.removeAllViews()
        // 首尾留白 8dp，让圆角不被文字顶满。
        row.setPadding(
            padH,
            if (position == 0) insetV else 0,
            padH,
            if (position == getCount() - 1) insetV else 0,
        )

        val label = TextView(context).apply {
            text = items[position]
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setTextColor(if (position == selectedIndex) primary else onSurface)
        }
        row.addView(label)

        if (position == selectedIndex) {
            row.addView(ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    20f.dp2px.toInt(),
                    20f.dp2px.toInt(),
                ).apply { marginStart = 10f.dp2px.toInt() }
                setImageResource(R.drawable.baseline_check_24)
                setColorFilter(primary)
            })
        }

        row.setOnClickListener { onPick(position) }
        return row
    }

    private fun rippleRes(): Int {
        val value = TypedValue()
        return if (context.theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)) {
            value.resourceId
        } else {
            0
        }
    }
}
