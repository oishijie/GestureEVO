package io.github.lumiagg.gestureevo.ui.miuix

import android.content.Context
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.github.lumiagg.gestureevo.ui.compose.MiuixEvoTheme
import io.github.lumiagg.gestureevo.utils.DynamicColorUtils

/**
 * 浮层（WindowManager overlay，无 Activity）里的 Compose 生命周期桥。
 *
 * ComposeView 依赖 ViewTree 上的 LifecycleOwner / SavedStateRegistryOwner 才能工作，
 * 而浮层挂载在 Window 上没有宿主——这里造一个可手动驱动生命周期的 owner，
 * 与浮层的 attach / detach 对齐：
 *
 * ```
 * val (view, owner) = context.miuixOverlayView { XxxScreen(...) }
 * wm.addView(view, params)   // owner 已处于 RESUMED
 * ...
 * owner.stop(); owner.destroy()   // removeView 前后调用
 * ```
 *
 * 注意：浮层销毁时必须调用 [stop] + [destroy]，否则 Compose 不会释放。
 */
class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    /** 首次创建时调用一次（等价 ON_CREATE）。 */
    fun create() {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    /** 浮层 addView 后调用（ON_START + ON_RESUME）。 */
    fun resume() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    /** 浮层 removeView 前调用（ON_PAUSE + ON_STOP）。 */
    fun pause() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    /** 浮层彻底销毁时调用（ON_DESTROY，之后不可复用）。 */
    fun destroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}

/**
 * 创建一个可直接 addView 到 WindowManager 的 Miuix Compose 浮层视图。
 * 主题走 [MiuixEvoTheme]（明暗 + 动态取色），上下文先经 [DynamicColorUtils.dynamicThemedContext]。
 */
fun Context.miuixOverlayView(
    content: @Composable () -> Unit,
): Pair<ComposeView, OverlayLifecycleOwner> {
    val themed = DynamicColorUtils.dynamicThemedContext(this)
    val owner = OverlayLifecycleOwner().apply { create() }
    val view = ComposeView(themed).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
        setViewTreeLifecycleOwner(owner)
        setViewTreeSavedStateRegistryOwner(owner)
        setContent { MiuixEvoTheme { content() } }
    }
    return view to owner
}

/** 便捷扩展：把 owner 的生命周期挂到浮层 View 的 attach/detach 回调上。 */
fun ComposeView.bindOverlayLifecycle(owner: OverlayLifecycleOwner) {
    addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = owner.resume()
        override fun onViewDetachedFromWindow(v: View) = owner.pause()
    })
}
