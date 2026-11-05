package io.github.lumiagg.gestureevo.pin

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.gesture.GestureService
import io.github.lumiagg.gestureevo.inspire.ManagedBitmap
import io.github.lumiagg.gestureevo.inspire.PictureFloating
import io.github.lumiagg.gestureevo.ui.miuix.OverlayLifecycleOwner
import io.github.lumiagg.gestureevo.ui.miuix.bindOverlayLifecycle
import io.github.lumiagg.gestureevo.ui.miuix.miuixOverlayView
import io.github.lumiagg.gestureevo.utils.CurrentPackageProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme

// Owns an attached WindowManager overlay, never an Activity. hide() releases all
// references on gesture service destruction, timeout and every completed drag.
object PinDragCancelOverlay {
    private const val MAX_VISIBLE_MS = 60_000L
    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayView: View? = null
    private var overlayOwner: OverlayLifecycleOwner? = null
    private var windowManager: WindowManager? = null
    private var endCallback: (() -> Unit)? = null
    private val timeoutRunnable = Runnable { hide() }

    // 拖放命中高亮：OnDragListener 驱动，Compose 侧响应式刷新。
    private var cancelZoneActive = mutableStateOf(false)
    private var pinZoneActive = mutableStateOf(false)

    fun show(context: Context, item: PinPoolItem?, onEnded: () -> Unit) {
        hide(notify = false)
        endCallback = onEnded
        val service = CurrentPackageProvider.accessibilityService()
        val overlayContext = service ?: context.applicationContext
        val (view, owner) = overlayContext.miuixOverlayView {
            DragBar(
                cancelActive = cancelZoneActive.value,
                pinActive = pinZoneActive.value,
            )
        }
        view.bindOverlayLifecycle(owner)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(service != null),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM
            title = "GestureEVO:PinDragCancel"
        }

        var finished = false
        fun finishDrag() {
            if (finished) return
            finished = true
            hide()
        }

        // 框架拖放事件派发给根视图；左右半区用 x 坐标命中。
        view.setOnDragListener { v, event ->
            when (event.action) {
                android.view.DragEvent.ACTION_DRAG_STARTED -> true
                android.view.DragEvent.ACTION_DRAG_ENTERED, android.view.DragEvent.ACTION_DRAG_LOCATION -> {
                    val cancelSide = event.x < v.width / 2f
                    cancelZoneActive.value = cancelSide
                    pinZoneActive.value = !cancelSide
                    true
                }
                android.view.DragEvent.ACTION_DRAG_EXITED -> {
                    cancelZoneActive.value = false
                    pinZoneActive.value = false
                    true
                }
                android.view.DragEvent.ACTION_DROP -> {
                    val cancelSide = event.x < v.width / 2f
                    cancelZoneActive.value = false
                    pinZoneActive.value = false
                    if (cancelSide) finishDrag() else repin(item)
                    true
                }
                android.view.DragEvent.ACTION_DRAG_ENDED -> {
                    cancelZoneActive.value = false
                    pinZoneActive.value = false
                    true
                }
                else -> false
            }
        }

        val wm = overlayContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        runCatching { wm.addView(view, params) }.onFailure {
            endCallback = null
            owner.destroy()
            return
        }
        owner.resume()
        windowManager = wm
        overlayView = view
        overlayOwner = owner
        mainHandler.removeCallbacks(timeoutRunnable)
        mainHandler.postDelayed(timeoutRunnable, MAX_VISIBLE_MS)
    }

    fun hide(notify: Boolean = true) {
        mainHandler.removeCallbacks(timeoutRunnable)
        val callback = endCallback
        endCallback = null
        val view = overlayView
        val owner = overlayOwner
        overlayView = null
        overlayOwner = null
        val wm = windowManager
        windowManager = null
        if (view != null) {
            runCatching { wm?.removeViewImmediate(view) }
        }
        owner?.pause()
        owner?.destroy()
        if (notify) {
            callback?.invoke()
        }
    }

    private fun repin(item: PinPoolItem?) {
        val service = CurrentPackageProvider.accessibilityService() as? GestureService ?: return
        val target = item ?: return
        service.launch(Dispatchers.IO) {
            when (target.type) {
                PinType.TEXT -> {
                    val text = target.text?.trim().orEmpty()
                    if (text.isEmpty()) return@launch
                    withContext(Dispatchers.Main) {
                        PictureFloating.build(service, text)
                    }
                }
                PinType.IMAGE -> {
                    val path = target.imagePath ?: return@launch
                    val bitmap = BitmapFactory.decodeFile(path) ?: return@launch
                    val handle = ManagedBitmap.from(bitmap)
                    withContext(Dispatchers.Main) {
                        PictureFloating.build(service, handle)
                    }
                }
            }
        }
    }

    private fun overlayType(fromAccessibility: Boolean): Int {
        if (fromAccessibility) {
            return WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }
}

@Composable
private fun DragBar(cancelActive: Boolean, pinActive: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DragZoneCard(
            label = stringResource(R.string.pin_drag_send_cancel),
            active = cancelActive,
            modifier = Modifier.weight(1f),
        )
        DragZoneCard(
            label = stringResource(R.string.pin_drag_send_repin),
            active = pinActive,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DragZoneCard(label: String, active: Boolean, modifier: Modifier = Modifier) {
    val highlight = if (active) {
        Modifier.border(2.dp, MiuixTheme.colorScheme.primary, RoundedCornerShape(16.dp))
    } else {
        Modifier
    }
    Card(modifier = modifier.then(highlight)) {
        MiuixText(
            text = label,
            fontSize = 15.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            color = MiuixTheme.colorScheme.onBackground,
        )
    }
}
