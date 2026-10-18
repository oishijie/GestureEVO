package io.github.lumiagg.gestureevo.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.transition.ChangeBounds
import android.transition.ChangeClipBounds
import android.transition.ChangeTransform
import android.transition.Transition
import android.transition.TransitionSet
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import io.github.lumiagg.gestureevo.settings.ui.SettingsSectionCard
import io.github.lumiagg.gestureevo.ui.HomeActivity

object SettingsSearchNavigator {
    private const val TAG = "DeepLinkDebug"

    const val EXTRA_TARGET_VIEW_ID = "settings_search_target_view_id"
    const val EXTRA_TARGET_VIEW_IDS = "settings_search_target_view_ids"
    const val EXTRA_TARGET_PREFERENCE_KEY = "settings_search_target_preference_key"

    fun buildIntent(context: Context, entry: SettingsSearchEntry): Intent {
        // 一级菜单入口深链：屏幕/搜索设置项直达原版设置页（InspireSettingsActivity /
        // SearchEngineSettingsActivity），由 focusAnchorIfNeeded 展开对应分区卡；
        // 其余条目仍按各自 targetActivity 跳转，SettingsActivity 重定向到 HomeActivity。
        val target: Class<out Activity> =
            if (entry.targetActivity == SettingsActivity::class.java) {
                HomeActivity::class.java
            } else {
                entry.targetActivity
            }
        return Intent(context, target).apply {
            if (entry.targetViewId != View.NO_ID) {
                putExtra(EXTRA_TARGET_VIEW_ID, entry.targetViewId)
            }
            if (entry.targetViewIds.isNotEmpty()) {
                putExtra(EXTRA_TARGET_VIEW_IDS, entry.targetViewIds.toIntArray())
            }
            entry.targetPreferenceKey?.let {
                putExtra(EXTRA_TARGET_PREFERENCE_KEY, it)
            }
            if (target == HomeActivity::class.java) {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        }
    }

    /**
     * 一级菜单入口的深链 Intent：打开原版设置页并展开指定分区卡。
     * 不传卡片时按页面默认状态展示（仅引擎列表展开）。
     */
    fun deepLinkIntent(
        context: Context,
        target: Class<out Activity>,
        vararg cardIds: Int,
    ): Intent {
        return Intent(context, target).apply {
            if (cardIds.isNotEmpty()) {
                putExtra(EXTRA_TARGET_VIEW_IDS, cardIds)
            }
        }
    }

    /**
     * 收敛视图到目标分区。
     *
     * 目标由 EXTRA_TARGET_VIEW_ID（单张）或 EXTRA_TARGET_VIEW_IDS（多张，一级菜单
     * 「设置」入口用）指定；深链进入时**只呈现目标分区**——其余分区卡整体隐藏，
     * 目标卡展开。不带目标时页面按默认状态展示。
     *
     * 显隐与展开是**同步**完成的（不依赖任何读库、不排在协程里）：否则首帧会按
     * 布局默认状态渲染出全部卡片与加载态，等 IO 跑完才收敛，用户会看到
     * 「全部展示 + 转圈 → 突然消失成目标分区」的中间态。只有滚动定位与高亮脉冲
     * 需要布局完成，留在下一帧执行。
     *
     * @return 本次深链的目标卡片 id 列表；空列表表示非深链进入（页面按默认展示）。
     *   调用方可据此跳过无关初始化（如引擎列表加载）并收敛常驻控件（如添加引擎 FAB）。
     */
    fun focusAnchorIfNeeded(root: View, intent: Intent?): List<Int> {
        val targetIds = buildList {
            (intent?.getIntExtra(EXTRA_TARGET_VIEW_ID, View.NO_ID) ?: View.NO_ID)
                .takeIf { it != View.NO_ID }?.let(::add)
            intent?.getIntArrayExtra(EXTRA_TARGET_VIEW_IDS)?.let { addAll(it.toList()) }
        }
        if (targetIds.isEmpty()) return emptyList()

        intent?.removeExtra(EXTRA_TARGET_VIEW_ID)
        intent?.removeExtra(EXTRA_TARGET_VIEW_IDS)

        val targets = targetIds.mapNotNull { root.findViewById<View>(it) }
        if (targets.isEmpty()) {
            android.util.Log.e(TAG, "targets EMPTY, targetIds=$targetIds")
            return emptyList()
        }
        // 锚点可能是分区卡本身，也可能是卡内的某个控件（外观页把 6 张同构卡合并成
        // 一张「界面外观」后，设置搜索的 16 条注册项仍指向原控件 id）。这里统一把
        // 锚点向上归一到最近的分区卡，收敛与展开都以卡为单位，滚动定位仍用原控件。
        val targetCards = targets.mapNotNull { findEnclosingSectionCard(it) }.toSet()
        if (targetCards.isEmpty()) {
            android.util.Log.e(TAG, "no enclosing card for targets=$targetIds")
            return emptyList()
        }
        val cards = collectSectionCards(root)

        // 先终止页面上可能残留的布局过渡，避免它与本次收敛的 visibility/展开
        // 状态变化交叉，把卡片尺寸/透明度留在中间态（状态正确但整页空白）。
        cards.mapNotNull { it.parent as? ViewGroup }.distinct().forEach { parent ->
            android.transition.TransitionManager.endTransitions(parent)
        }

        // 同步收敛：隐藏非目标卡、目标卡无动画展开。
        cards.forEach { card ->
            card.animate().cancel()
            card.alpha = 1f
            if (targetCards.contains(card)) {
                card.isVisible = true
                card.setExpanded(true, animate = false)
            } else {
                card.isVisible = false
            }
        }

        // 滚动定位与高亮脉冲需要布局完成，放到下一帧。
        root.post {
            val scrollView = findNestedScrollView(root)
            if (scrollView != null) {
                val rect = Rect()
                targets.first().getDrawingRect(rect)
                scrollView.offsetDescendantRectToMyCoords(targets.first(), rect)
                val topInset = (root.resources.displayMetrics.density * 16).toInt()
                scrollView.smoothScrollTo(0, (rect.top - topInset).coerceAtLeast(0))
            }
            targets.first().requestFocus()
            pulseHighlight(targets.first())

            // 布局落定后再核对一次实际渲染状态（宽高 / 透明度）。
            root.postDelayed({
                android.util.Log.e(
                    TAG,
                    "rendered: " + collectSectionCards(root).map {
                        "${Integer.toHexString(it.id)} vis=${it.visibility} a=${it.alpha} " +
                            "w=${it.width} h=${it.height}"
                    }
                )
            }, 400)
        }
        return targetIds
    }

    private fun collectSectionCards(view: View): List<SettingsSectionCard> {
        if (view is SettingsSectionCard) return listOf(view)
        if (view is ViewGroup) {
            return view.children.flatMap { collectSectionCards(it) }.toList()
        }
        return emptyList()
    }

    /** 自 [view] 起沿父链向上找最近的 [SettingsSectionCard]；自身是卡则直接返回。 */
    private fun findEnclosingSectionCard(view: View): SettingsSectionCard? {
        var current: View? = view
        while (current != null) {
            if (current is SettingsSectionCard) return current
            current = current.parent as? View
        }
        return null
    }

    fun buildSharedElementTransition(activity: Activity): Transition? {
        return TransitionSet().apply {
            ordering = TransitionSet.ORDERING_TOGETHER
            duration = 260L
            interpolator = AccelerateDecelerateInterpolator()
            addTransition(ChangeBounds())
            addTransition(ChangeTransform())
            addTransition(ChangeClipBounds())
        }
    }

    private fun pulseHighlight(targetView: View) {
        targetView.animate().cancel()
        targetView.alpha = 0.68f
        targetView.animate()
            .alpha(1f)
            .setDuration(650L)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    private fun findNestedScrollView(root: View): NestedScrollView? {
        if (root is NestedScrollView) return root
        if (root is ViewGroup) {
            root.children.forEach { child ->
                val nestedScrollView = findNestedScrollView(child)
                if (nestedScrollView != null) return nestedScrollView
            }
        }
        return null
    }
}
