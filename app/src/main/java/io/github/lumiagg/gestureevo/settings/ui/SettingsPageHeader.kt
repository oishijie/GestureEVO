package io.github.lumiagg.gestureevo.settings.ui

import android.graphics.drawable.LayerDrawable
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.CollapsingToolbarLayout
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.utils.DynamicColorUtils

/**
 * 二级页页头统一装配：圆形返回钮 + 展开态大标题，滚动超过一屏后由
 * CollapsingToolbarLayout 原生接管折叠成 Toolbar 小标题。
 *
 * 用法：Activity setSupportActionBar(toolbar) 之后调用
 * `installPageHeader(this, toolbar, collapsingToolbarLayout, R.string.xxx_title)`。
 */
fun installPageHeader(
    activity: androidx.activity.ComponentActivity,
    toolbar: Toolbar,
    collapsingToolbarLayout: CollapsingToolbarLayout,
    titleResId: Int,
) {
    toolbar.setNavigationOnClickListener { activity.onBackPressedDispatcher.onBackPressed() }
    val navIcon = ContextCompat.getDrawable(activity, R.drawable.bg_nav_back_circle)
    if (navIcon is LayerDrawable) {
        // 第 0 层是 oval 圆底（底色走 theme attr），第 1 层是箭头，
        // 只 tint 箭头层，避免把圆底一起染色。
        val arrow = navIcon.getDrawable(1)
        arrow.setTint(DynamicColorUtils.onSurfaceColor)
    }
    toolbar.navigationIcon = navIcon
    collapsingToolbarLayout.setExpandedTitleTextAppearance(
        R.style.TextAppearance_GestureEVO_PageHeader_Expanded
    )
    collapsingToolbarLayout.setCollapsedTitleTextAppearance(
        R.style.TextAppearance_GestureEVO_PageHeader_Collapsed
    )
    collapsingToolbarLayout.title = activity.getString(titleResId)
}
