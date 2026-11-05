package io.github.lumiagg.gestureevo.inspire.ui

import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.core.view.isVisible
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.ui.miuix.OverlayLifecycleOwner
import io.github.lumiagg.gestureevo.ui.miuix.miuixOverlayView
import io.github.lumiagg.gestureevo.utils.ScreenUtil
import io.github.lumiagg.gestureevo.utils.dp2px
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 取词浮层内的提示卡：宿主是取词浮层的 ConstraintLayout，
 * 通过 [miuixOverlayView] 桥接 Compose 生命周期，MiuixEvoTheme 自动跟随明暗与取色。
 */
internal class InspireOverlayToast(
    private val host: ConstraintLayout,
) {
    private var overlayView: View? = null
    private var overlayOwner: OverlayLifecycleOwner? = null
    private var message = mutableStateOf("")
    private var maxWidthPx = mutableStateOf(0)
    private var topInsetPx = mutableStateOf(0)
    private val hideRunnable = Runnable { hide() }

    fun show(text: CharSequence, durationMs: Long = DURATION_SHORT_MS) {
        val toast = ensureToast()
        message.value = text.toString()
        layoutToast()
        toast.removeCallbacks(hideRunnable)
        toast.bringToFront()
        val alreadyVisible = toast.isVisible && toast.alpha >= 0.95f
        if (!alreadyVisible) {
            toast.animate().cancel()
            toast.alpha = 0f
            toast.translationY = -ENTER_OFFSET_DP.dp2px
            toast.visibility = View.VISIBLE
            toast.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(ANIM_MS)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction(null)
                .start()
        }
        toast.announceForAccessibility(text)
        toast.postDelayed(hideRunnable, durationMs)
    }

    fun hide(immediate: Boolean = false) {
        val toast = overlayView ?: return
        toast.removeCallbacks(hideRunnable)
        toast.animate().cancel()
        if (immediate || toast.visibility != View.VISIBLE) {
            toast.alpha = 0f
            toast.translationY = 0f
            toast.visibility = View.GONE
            return
        }
        toast.animate()
            .alpha(0f)
            .translationY(-ENTER_OFFSET_DP.dp2px)
            .setDuration(ANIM_MS)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                toast.visibility = View.GONE
                toast.translationY = 0f
            }
            .start()
    }

    fun refreshTheme() {
        // MiuixEvoTheme 自动跟随明暗与动态取色。
    }

    private fun ensureToast(): View {
        overlayView?.let { return it }
        val (view, owner) = host.context.miuixOverlayView {
            ToastCard(
                message = message.value,
                maxWidthPx = maxWidthPx.value,
                topInsetPx = topInsetPx.value,
            )
        }
        view.isClickable = false
        view.isLongClickable = false
        view.isFocusable = false
        view.isFocusableInTouchMode = false
        host.addView(
            view,
            ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                constrainedWidth = true
                horizontalBias = 0.5f
            }
        )
        overlayOwner = owner
        overlayView = view
        return view
    }

    private fun layoutToast() {
        val toast = overlayView ?: return
        val horizontal = HORIZONTAL_MARGIN_DP.dp2px.toInt()
        val hostWidth = host.width.takeIf { it > 0 } ?: host.resources.displayMetrics.widthPixels
        val maxWidth = (hostWidth - horizontal * 2).coerceAtLeast(0)
        maxWidthPx.value = maxWidth
        topInsetPx.value = TOP_MARGIN_DP.dp2px.toInt() + statusBarInset()
        val params = toast.layoutParams as? ConstraintLayout.LayoutParams ?: return
        params.marginStart = horizontal
        params.marginEnd = horizontal
        params.topMargin = topInsetPx.value
        params.bottomMargin = 0
        params.matchConstraintMaxWidth = maxWidth
        toast.layoutParams = params
    }

    private fun statusBarInset(): Int {
        val inset = androidx.core.view.ViewCompat.getRootWindowInsets(host)
            ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            ?.top
            ?: 0
        if (inset > 0) return inset
        return ScreenUtil.getStatusBarHeight(host.context).coerceAtLeast(0)
    }

    @Composable
    private fun ToastCard(message: String, maxWidthPx: Int, topInsetPx: Int) {
        val density = LocalDensity.current
        Card(
            modifier = Modifier.padding(top = with(density) { topInsetPx.toDp() }),
        ) {
            MiuixText(
                text = message,
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onBackground,
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .widthIn(max = with(density) { maxWidthPx.toDp() }),
            )
        }
    }

    private companion object {
        const val DURATION_SHORT_MS = 2000L
        private const val ANIM_MS = 180L
        private const val ENTER_OFFSET_DP = 12f
        private const val HORIZONTAL_MARGIN_DP = 24
        private const val TOP_MARGIN_DP = 16
    }
}
