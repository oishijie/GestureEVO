package io.github.lumiagg.gestureevo.notification

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.floating.BaseFloatingWindow
import io.github.lumiagg.gestureevo.gesture.GestureService
import io.github.lumiagg.gestureevo.notification.NotificationService.Companion.produceNotificationEvent
import io.github.lumiagg.gestureevo.notification.NotificationService.NotificationInfo
import io.github.lumiagg.gestureevo.settings.InspireSettingsActivity.Companion.DEFAULT_NOTIFICATION_IMAGE_DISPLAY_SECONDS
import io.github.lumiagg.gestureevo.settings.InspireSettingsActivity.Companion.KEY_NOTIFICATION_IMAGE_DISPLAY_SECONDS
import io.github.lumiagg.gestureevo.ui.miuix.OverlayLifecycleOwner
import io.github.lumiagg.gestureevo.ui.miuix.bindOverlayLifecycle
import io.github.lumiagg.gestureevo.ui.miuix.miuixOverlayView
import androidx.core.view.isVisible
import io.github.lumiagg.gestureevo.utils.VibratorUtils
import io.github.lumiagg.gestureevo.utils.ScreenUtil
import io.github.lumiagg.gestureevo.utils.dp2px
import io.github.lumiagg.gestureevo.utils.icon.IconHelper
import io.github.lumiagg.gestureevo.utils.postOnMain
import io.github.lumiagg.gestureevo.utils.readKey
import io.github.lumiagg.gestureevo.utils.saveKey
import io.github.lumiagg.gestureevo.utils.settingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

class NotificationFloating(
    private val context: GestureService,
) : BaseFloatingWindow(context, context, TAG) {
    companion object {
        private const val TAG = "NotificationFloating"
        const val KEY_NOTIFICATION_FLOATING_LOC_X = "notification_floating_loc_x"
        const val KEY_NOTIFICATION_FLOATING_LOC_Y = "notification_floating_loc_y"
        private const val MAX_NOTIFICATIONS = 3
    }

    // Compose 侧状态：图标条内容 + 钉住态（pinnedState 供 Compose 响应式）。
    private val iconPackages = mutableStateListOf<String>()
    private val iconDrawables = mutableMapOf<String, Drawable>()
    private var isPinned = false
    private var pinnedState by mutableStateOf(false)

    private var stripHost: android.widget.FrameLayout? = null
    private var overlayOwner: OverlayLifecycleOwner? = null

    private var notificationJob: Job? = null
    private val layoutParams = WindowManager.LayoutParams()
    private var isAttached = false
    private var visibilityAnimator: ObjectAnimator? = null
    private var isDockedToRight = false
    private var dockedYRatio: Float = 0.5f

    private val componentCallbacks = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            this@NotificationFloating.onConfigurationChanged(newConfig)
        }
        override fun onLowMemory() {}
    }

    var enable = true
        set(value) {
            if (value != field) {
                if (!value) {
                    hide()
                }
                field = value
            }
        }

    private val screenWidth: Int get() = ScreenUtil.getScreenWidth(context)
    private val screenHeight: Int get() = ScreenUtil.getScreenHeight(context)

    init {
        initAll()
    }

    private fun initAll() {
        val frame = android.widget.FrameLayout(context)
        val (composeView, owner) = context.miuixOverlayView {
            NotificationStrip(iconPackages, iconDrawables, pinnedState)
        }
        composeView.bindOverlayLifecycle(owner)
        frame.addView(
            composeView,
            android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        stripHost = frame
        overlayOwner = owner
        initFloatingWindow()
        initTouchListener()
        context.registerComponentCallbacks(componentCallbacks)
    }

    private fun initFloatingWindow() {
        layoutParams.apply {
            configureAccessibilityOverlay(
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                width = WindowManager.LayoutParams.WRAP_CONTENT,
                height = WindowManager.LayoutParams.WRAP_CONTENT,
                y = screenHeight / 2,
                allowDisplayCutout = false,
                title = "GestureEVO:NotificationFloating",
            )
        }
        stripHost?.visibility = View.GONE
        stripHost?.alpha = 0f
        context.launch(Dispatchers.IO) {
            val savedX = context.settingsStore.readKey(KEY_NOTIFICATION_FLOATING_LOC_X, 0)
            val savedY =
                context.settingsStore.readKey(KEY_NOTIFICATION_FLOATING_LOC_Y, screenHeight / 2)
            context.postOnMain {
                val currentScreenWidth = screenWidth
                val currentScreenHeight = screenHeight
                isDockedToRight = savedX > currentScreenWidth / 2 || savedX > 0
                dockedYRatio = (savedY.toFloat() / currentScreenHeight.coerceAtLeast(1)).coerceIn(0.05f, 0.95f)

                val root = stripHost ?: return@postOnMain
                val rootWidth = root.width.takeIf { it > 0 } ?: 56.dp2px.toInt()
                val rootHeight = root.height.takeIf { it > 0 } ?: 56.dp2px.toInt()

                layoutParams.x = if (isDockedToRight) (currentScreenWidth - rootWidth).coerceAtLeast(0) else 0
                layoutParams.y = (dockedYRatio * currentScreenHeight - rootHeight / 2f).roundToInt()
                    .coerceIn(0, (currentScreenHeight - rootHeight).coerceAtLeast(0))
                if (isAttached && root.isAttachedToWindow) {
                    updateViewLayoutSafely(root, layoutParams)
                }
            }
        }
    }

    private fun rootWidthOf(root: View) =
        root.width.takeIf { it > 0 } ?: 56.dp2px.toInt()

    private fun rootHeightOf(root: View) =
        root.height.takeIf { it > 0 } ?: 56.dp2px.toInt()

    private fun ensureAttached(root: View) {
        if (root.isAttachedToWindow) {
            isAttached = true
            return
        }
        addViewSafely(root, layoutParams) { isAttached = true }
        isAttached = root.isAttachedToWindow
    }

    private fun detachIfNeeded(root: View, cancelAnimator: Boolean = true) {
        if (cancelAnimator) {
            visibilityAnimator?.cancel()
            visibilityAnimator = null
        }
        detachViewSafely(root) { isAttached = false }
    }

    private val dragHandler = DragHandler()
    private val selectionHandler = SelectionHandler()

    @SuppressLint("ClickableViewAccessibility")
    private fun initTouchListener() {
        val root = stripHost ?: return
        root.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragHandler.onTouchDown(event, view)
                    selectionHandler.onTouchDown(event, view)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (dragHandler.longPressStarted) {
                        dragHandler.onTouchMove(event, view, windowManager)
                    } else {
                        selectionHandler.onTouchMove(event)
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (dragHandler.longPressStarted) {
                        dragHandler.onTouchUp(event, view, windowManager)
                    } else {
                        selectionHandler.onTouchUp(event, view, dragHandler)
                    }
                    false
                }

                else -> false
            }
        }
        root.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (dragHandler.isMoving) return@addOnLayoutChangeListener
            val width = right - left
            val height = bottom - top
            if (width <= 0 || height <= 0) return@addOnLayoutChangeListener
            val oldWidth = oldRight - oldLeft
            val oldHeight = oldBottom - oldTop
            if (width != oldWidth || height != oldHeight) {
                val currentScreenWidth = screenWidth
                val currentScreenHeight = screenHeight
                val targetX = if (isDockedToRight) (currentScreenWidth - width).coerceAtLeast(0) else 0
                val targetY = layoutParams.y.coerceIn(0, (currentScreenHeight - height).coerceAtLeast(0))
                if (layoutParams.x != targetX || layoutParams.y != targetY) {
                    layoutParams.x = targetX
                    layoutParams.y = targetY
                    if (isAttached && root.isAttachedToWindow) {
                        updateViewLayoutSafely(root, layoutParams)
                    }
                }
            }
        }
    }

    private fun iconDrawable(pkg: String): Drawable =
        iconDrawables.getOrPut(pkg) {
            IconHelper.getIcon(context, pkg) ?: android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
        }

    private fun handleClick(pkg: String) = context.launch {
        produceNotificationEvent(NotificationEvent(pkg, freeForm = true))
        hide()
    }

    private fun handleLongPress(pkg: String) = context.launch {
        produceNotificationEvent(NotificationEvent(pkg, freeForm = false))
        hide()
    }

    private fun handleSwipe() = context.launch {
        hide()
    }

    private fun updateViews(notificationInfoList: List<NotificationInfo>) {
        val newPackages = notificationInfoList.take(MAX_NOTIFICATIONS).map { it.packageName }
        newPackages.forEach { iconDrawable(it) }
        iconPackages.clear()
        iconPackages.addAll(newPackages)
    }

    fun show(notificationInfoList: List<NotificationInfo>) = context.postOnMain {
        if (!enable) return@postOnMain
        val root = stripHost ?: return@postOnMain
        val currentScreenWidth = screenWidth
        val currentScreenHeight = screenHeight
        val rootWidth = rootWidthOf(root)
        val rootHeight = rootHeightOf(root)
        layoutParams.x = if (isDockedToRight) (currentScreenWidth - rootWidth).coerceAtLeast(0) else 0
        layoutParams.y = (dockedYRatio * currentScreenHeight - rootHeight / 2f).roundToInt()
            .coerceIn(0, (currentScreenHeight - rootHeight).coerceAtLeast(0))
        if (isAttached && root.isAttachedToWindow) {
            updateViewLayoutSafely(root, layoutParams)
        }
        ensureAttached(root)
        visibilityAnimator?.cancel()

        if (notificationInfoList != currentNotifications) {
            currentNotifications = notificationInfoList
            updateViews(notificationInfoList)
        }
        restartAutoHideTimer()

        if (root.visibility != View.VISIBLE) {
            root.alpha = 0f
            root.visibility = View.VISIBLE
            visibilityAnimator = ObjectAnimator.ofFloat(root, View.ALPHA, root.alpha, 1f).apply {
                duration = 200
                start()
            }
        }
    }

    fun hide() = context.postOnMain {
        notificationJob?.cancel()
        notificationJob = null
        clearPinnedState()
        val root = stripHost ?: return@postOnMain
        if (!root.isAttachedToWindow) {
            cleanup()
            detachIfNeeded(root)
            return@postOnMain
        }
        visibilityAnimator?.cancel()
        if (root.isVisible) {
            val hideAnimator = ObjectAnimator.ofFloat(root, View.ALPHA, root.alpha, 0.0f)
            visibilityAnimator = hideAnimator.apply {
                duration = 200
                addListener(object : android.animation.Animator.AnimatorListener {
                    override fun onAnimationStart(p0: android.animation.Animator) {}
                    override fun onAnimationEnd(p0: android.animation.Animator) {
                        if (visibilityAnimator !== hideAnimator) return
                        visibilityAnimator = null
                        root.visibility = View.GONE
                        cleanup()
                        detachIfNeeded(root, cancelAnimator = false)
                    }

                    override fun onAnimationCancel(p0: android.animation.Animator) {
                        visibilityAnimator = null
                        if (!isAttachRequested(root)) {
                            cleanup()
                            detachIfNeeded(root, cancelAnimator = false)
                        }
                    }
                    override fun onAnimationRepeat(p0: android.animation.Animator) {}
                })
                start()
            }
        } else {
            cleanup()
            detachIfNeeded(root)
        }
    }

    fun update(notificationInfoList: List<NotificationInfo>) = context.postOnMain {
        if (!enable) return@postOnMain
        val root = stripHost ?: return@postOnMain
        if (root.isAttachedToWindow && root.isVisible) {
            if (notificationInfoList.isEmpty()) {
                hide()
            } else if (notificationInfoList != currentNotifications) {
                currentNotifications = notificationInfoList
                updateViews(notificationInfoList)
                restartAutoHideTimer()
            } else {
                restartAutoHideTimer()
            }
        }
    }

    fun refreshTheme() {
        // MiuixEvoTheme 自动跟随明暗与动态取色，无需手动刷色。
    }

    fun cleanup() {
        iconPackages.clear()
        currentNotifications = emptyList()
    }

    fun destroy() = context.postOnMain {
        runCatching { context.unregisterComponentCallbacks(componentCallbacks) }
        notificationJob?.cancel()
        notificationJob = null
        visibilityAnimator?.cancel()
        visibilityAnimator = null
        clearPinnedState()
        stripHost?.animate()?.cancel()
        stripHost?.visibility = View.GONE
        cleanup()
        stripHost?.let { detachIfNeeded(it, cancelAnimator = false) }
        overlayOwner?.pause()
        overlayOwner?.destroy()
    }

    fun pinnedMode() {
        notificationJob?.cancel()
        notificationJob = null
        if (isPinned) return
        isPinned = true
        context.postOnMain {
            VibratorUtils.vibrator(context, true)
            stripHost?.animate()
                ?.scaleX(1.1f)
                ?.scaleY(1.1f)
                ?.setDuration(150)
                ?.withEndAction {
                    stripHost?.animate()
                        ?.scaleX(1.0f)
                        ?.scaleY(1.0f)
                        ?.setDuration(150)
                        ?.start()
                }
                ?.start()
        }
    }

    fun clearPinnedState() {
        isPinned = false
    }

    fun onConfigurationChanged(newConfig: Configuration? = null) {
        repositionAfterOrientationChange()
    }

    fun repositionAfterOrientationChange() {
        context.postOnMain {
            val root = stripHost ?: return@postOnMain
            val currentScreenWidth = screenWidth
            val currentScreenHeight = screenHeight
            val rootWidth = rootWidthOf(root)
            val rootHeight = rootHeightOf(root)

            val targetX = if (isDockedToRight) (currentScreenWidth - rootWidth).coerceAtLeast(0) else 0
            val targetY = (dockedYRatio * currentScreenHeight - rootHeight / 2f).roundToInt()
                .coerceIn(0, (currentScreenHeight - rootHeight).coerceAtLeast(0))

            layoutParams.x = targetX
            layoutParams.y = targetY
            if (isAttached && root.isAttachedToWindow) {
                updateViewLayoutSafely(root, layoutParams)
            }
        }
        stripHost?.postDelayed({
            val root = stripHost ?: return@postDelayed
            val currentScreenWidth = screenWidth
            val currentScreenHeight = screenHeight
            val rootWidth = rootWidthOf(root)
            val rootHeight = rootHeightOf(root)

            val targetX = if (isDockedToRight) (currentScreenWidth - rootWidth).coerceAtLeast(0) else 0
            val targetY = (dockedYRatio * currentScreenHeight - rootHeight / 2f).roundToInt()
                .coerceIn(0, (currentScreenHeight - rootHeight).coerceAtLeast(0))

            layoutParams.x = targetX
            layoutParams.y = targetY
            if (isAttached && root.isAttachedToWindow) {
                updateViewLayoutSafely(root, layoutParams)
            }
        }, 150L)
    }

    private suspend fun restartAutoHideTimer() {
        notificationJob?.cancel()
        notificationJob = null
        if (isPinned || currentNotifications.isEmpty()) return

        val displaySeconds = context.settingsStore.readKey(
            KEY_NOTIFICATION_IMAGE_DISPLAY_SECONDS,
            DEFAULT_NOTIFICATION_IMAGE_DISPLAY_SECONDS
        ).coerceAtLeast(0)
        if (displaySeconds == 0) return

        notificationJob = context.launch {
            delay(displaySeconds * 1000L)
            hide()
        }
    }

    private fun hitTestIcon(event: MotionEvent): String? {
        // 图标卡等宽相邻排列（每张 55dp，根内边距 10dp），按 x 坐标求槽位。
        val slot = 55.dp2px
        val start = 10.dp2px
        if (event.x < start) return null
        val index = ((event.x - start) / slot).toInt()
        if (index < 0 || index >= iconPackages.size) return null
        return iconPackages.getOrNull(index)
    }

    private fun drawableToBitmap(drawable: Drawable, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return bitmap
    }

    @Composable
    private fun NotificationStrip(
        packages: List<String>,
        drawables: Map<String, Drawable>,
        pinned: Boolean,
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            packages.forEach { pkg ->
                val drawable = drawables[pkg]
                val bmp = remember(pkg) {
                    drawable?.let { drawableToBitmap(it, 55.dp2px.toInt()) }
                } ?: return
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = pkg,
                    modifier = Modifier
                        .size(55.dp)
                        .then(
                            if (pinned) Modifier.border(
                                4.dp,
                                top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.primary,
                                androidx.compose.foundation.shape.RoundedCornerShape(25.dp),
                            ) else Modifier
                        ),
                )
            }
        }
    }

    private inner class DragHandler {
        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f
        var isMoving = false
            private set
        var longPressStarted = false
            private set
        private val longPressTimeout = 500L
        private var longPressRunnable: Runnable? = null

        fun onTouchDown(event: MotionEvent, view: View) {
            val layoutParams = view.layoutParams as WindowManager.LayoutParams
            initialX = layoutParams.x
            initialY = layoutParams.y
            initialTouchX = event.rawX
            initialTouchY = event.rawY
            isMoving = false
            longPressStarted = false

            longPressRunnable = Runnable {
                VibratorUtils.vibrator(context, true)
                longPressStarted = true
            }.also {
                view.postDelayed(it, longPressTimeout)
            }
        }

        fun onTouchMove(event: MotionEvent, view: View, windowManager: WindowManager) {
            val deltaX = (event.rawX - initialTouchX)
            val deltaY = (event.rawY - initialTouchY)
            val distance = hypot(deltaX, deltaY)

            if (longPressStarted && distance > 10f.dp2px) {
                isMoving = true
                pinnedMode()

                val layoutParams = view.layoutParams as WindowManager.LayoutParams
                layoutParams.x = initialX + deltaX.toInt()
                layoutParams.y = initialY + deltaY.toInt()
                windowManager.updateViewLayout(view, layoutParams)
            }
        }

        fun onTouchUp(event: MotionEvent, view: View, windowManager: WindowManager) {
            longPressStarted = false
            longPressRunnable?.let { view.removeCallbacks(it) }

            if (isMoving) {
                val layoutParams = view.layoutParams as WindowManager.LayoutParams
                val currentScreenWidth = screenWidth
                val currentScreenHeight = screenHeight
                isDockedToRight = (layoutParams.x + view.width / 2) >= currentScreenWidth / 2
                val targetX = if (isDockedToRight) (currentScreenWidth - view.width).coerceAtLeast(0) else 0
                dockedYRatio = ((layoutParams.y + view.height / 2f) / currentScreenHeight.coerceAtLeast(1)).coerceIn(0.05f, 0.95f)

                animateToEdge(layoutParams, view, windowManager, targetX)
                context.launch {
                    context.settingsStore.saveKey(KEY_NOTIFICATION_FLOATING_LOC_X, targetX)
                    context.settingsStore.saveKey(KEY_NOTIFICATION_FLOATING_LOC_Y, layoutParams.y)
                }
            } else {
                selectionHandler.selectedPackage?.let { handleLongPress(it) }
            }
        }

        private fun animateToEdge(
            layoutParams: WindowManager.LayoutParams,
            view: View,
            windowManager: WindowManager,
            targetX: Int
        ) {
            ObjectAnimator.ofInt(layoutParams.x, targetX).apply {
                addUpdateListener { animator ->
                    layoutParams.x = animator.animatedValue as Int
                    updateViewLayoutSafely(view, layoutParams)
                }
                duration = 200
                start()
            }
        }

        fun isLongPressed() = longPressStarted && !isMoving
    }

    private inner class SelectionHandler {
        private var startTime = 0L
        private var startX = 0f
        private var startY = 0f
        var selectedPackage: String? = null

        fun onTouchDown(event: MotionEvent, view: View) {
            startTime = System.currentTimeMillis()
            startX = event.rawX
            startY = event.rawY
            selectedPackage = hitTestIcon(event)
        }

        fun onTouchMove(event: MotionEvent) {
        }

        fun onTouchUp(event: MotionEvent, view: View, dragHandler: DragHandler) {
            val dx = event.rawX - startX
            val dy = event.rawY - startY
            val overDis = hypot(dx, dy) > 50.dp2px
            if (overDis && abs(dx) > 40.dp2px) {
                pinnedMode()
            } else if (overDis) {
                if (selectedPackage != null) {
                    selectedPackage = null
                }
                handleSwipe()
            } else {
                selectedPackage?.let {
                    if (System.currentTimeMillis() - startTime < 200) handleClick(it)
                }
            }
        }
    }

    private var currentNotifications: List<NotificationInfo> = emptyList()
}
