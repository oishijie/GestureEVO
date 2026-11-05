package io.github.lumiagg.gestureevo.screen_search

import android.content.Context
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.floating.BaseFloatingWindow
import io.github.lumiagg.gestureevo.gesture.GestureService
import io.github.lumiagg.gestureevo.ui.miuix.OverlayLifecycleOwner
import io.github.lumiagg.gestureevo.ui.miuix.bindOverlayLifecycle
import io.github.lumiagg.gestureevo.ui.miuix.miuixOverlayView
import io.github.lumiagg.gestureevo.inspire.cropByRect
import io.github.lumiagg.gestureevo.utils.MlKitOcr
import io.github.lumiagg.gestureevo.utils.ToastHelper
import io.github.lumiagg.gestureevo.utils.dp2px
import io.github.lumiagg.gestureevo.utils.postOnMain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ScreenDirection { UP, DOWN }

class ScreenSearchFloating(private val context: GestureService) :
    BaseFloatingWindow(context, context, "ScreenSearchFloating") {

    private data class CaptureResult(
        val matches: List<Rect>,
        val failed: Boolean
    )

    // Compose 状态
    private var keyword by mutableStateOf("")
    private var useAccessibility by mutableStateOf(true)
    private var direction by mutableStateOf(ScreenDirection.DOWN)
    private var searching by mutableStateOf(false)
    private var indicatorSearching by mutableStateOf(false)

    // 浮层视图
    private var panelView: View? = null
    private var panelOwner: OverlayLifecycleOwner? = null
    private var indicatorView: View? = null
    private var indicatorOwner: OverlayLifecycleOwner? = null

    // 高亮层保持 View 实现（自绘匹配矩形）
    private val highlightView: ScreenSearchHighlightView by lazy {
        ScreenSearchHighlightView(context)
    }

    private val layoutParams: WindowManager.LayoutParams = WindowManager.LayoutParams()
    private val indicatorLayoutParams: WindowManager.LayoutParams = WindowManager.LayoutParams()
    private val highlightLayoutParams: WindowManager.LayoutParams = WindowManager.LayoutParams()
    private var panelAttached = false
    private var indicatorAttached = false
    private var highlightAttached = false

    private var searchJob: Job? = null
    private val maxAttempts = 60
    private val maxCaptureFailures = 3
    private val preStartDelayMs = 250L
    private val scrollSettleDelayMs = 220L
    private val revealScanRatio = 0.65f

    init {
        initPanelWindow()
        initIndicatorWindow()
        initHighlightWindow()
    }

    private fun initPanelWindow() {
        layoutParams.configureAccessibilityOverlay(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            width = WindowManager.LayoutParams.WRAP_CONTENT,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL,
            y = 24.dp2px.toInt(),
            title = "GestureEVO:ScreenSearchFloating:Panel",
        )
        layoutParams.softInputMode =
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
        val (view, owner) = context.miuixOverlayView {
            SearchPanel(
                keyword = keyword,
                onKeywordChange = { keyword = it },
                useAccessibility = useAccessibility,
                onAccessibilityChange = { useAccessibility = it; startSearch() },
                direction = direction,
                onDirectionChange = { direction = it; startSearch() },
                searching = searching,
                onClose = { hide() },
                onSearch = { startSearch() },
            )
        }
        view.bindOverlayLifecycle(owner)
        panelView = view
        panelOwner = owner
    }

    private fun initIndicatorWindow() {
        indicatorLayoutParams.configureAccessibilityOverlay(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            width = 48.dp2px.toInt(),
            height = 48.dp2px.toInt(),
            gravity = Gravity.END or Gravity.CENTER_VERTICAL,
            x = 24,
            title = "GestureEVO:ScreenSearchFloating:Indicator",
        )
        indicatorView?.visibility = View.GONE
        val (view, owner) = context.miuixOverlayView {
            IndicatorContent(searching = indicatorSearching)
        }
        view.bindOverlayLifecycle(owner)
        view.setOnClickListener { stopSearchAndShowPanel() }
        indicatorView = view
        indicatorOwner = owner
    }

    private fun initHighlightWindow() {
        highlightLayoutParams.configureAccessibilityOverlay(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            title = "GestureEVO:ScreenSearchFloating:Highlight",
        )
        highlightView.visibility = View.GONE
    }

    fun show() = context.postOnMain {
        ensureViewsAttached()
        val pv = panelView ?: return@postOnMain
        pv.visibility = View.VISIBLE
        layoutParams.x = 0
        updateViewLayoutSafely(pv, layoutParams)
        indicatorView?.visibility = View.GONE
        updateWindowFocus(true)
        keyword = ""
        indicatorSearching = false
    }

    fun hide() = context.postOnMain {
        stopSearch()
        highlightView.hideRects()
        highlightView.visibility = View.GONE
        panelView?.visibility = View.GONE
        indicatorView?.visibility = View.GONE
        detachViewsIfNeeded()
    }

    fun destroy() = context.postOnMain {
        stopSearch()
        highlightView.hideRects()
        highlightView.visibility = View.GONE
        panelView?.visibility = View.GONE
        indicatorView?.visibility = View.GONE
        detachViewsIfNeeded()
        panelOwner?.pause(); panelOwner?.destroy()
        indicatorOwner?.pause(); indicatorOwner?.destroy()
    }

    fun refreshTheme() {
        // MiuixEvoTheme 自动跟随明暗与动态取色。
    }

    private fun startSearch() {
        val kw = keyword.trim()
        if (kw.isEmpty()) {
            ToastHelper.showInUIThread(context, R.string.screen_search_empty_keyword)
            return
        }
        context.postOnMain {
            panelView?.visibility = View.GONE
            indicatorView?.visibility = View.VISIBLE
            updateWindowFocus(false)
        }
        searching = true
        indicatorSearching = true
        searchJob?.cancel()
        searchJob = context.launch {
            try {
                var currentStep = 0
                var captureFailures = 0
                var forceFullScan = true
                delay(preStartDelayMs)
                while (currentStep < maxAttempts) {
                    currentStep++
                    val result = if (useAccessibility) {
                        captureAndMatchAccessibility(kw, !forceFullScan)
                    } else {
                        captureAndMatch(kw, !forceFullScan)
                    }
                    if (result.failed) {
                        captureFailures++
                        forceFullScan = true
                        if (captureFailures >= maxCaptureFailures) break
                        delay(scrollSettleDelayMs)
                        continue
                    }
                    captureFailures = 0
                    forceFullScan = false
                    if (result.matches.isNotEmpty()) {
                        showFound(result.matches, kw, currentStep)
                        break
                    }
                    if (!performScroll(direction)) break
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                searching = false
                indicatorSearching = false
            }
        }
    }

    private fun stopSearch() {
        searchJob?.cancel()
        searchJob = null
        searching = false
        indicatorSearching = false
    }

    private fun stopSearchAndShowPanel() {
        stopSearch()
        context.postOnMain {
            ensureViewsAttached()
            panelView?.visibility = View.VISIBLE
            indicatorView?.visibility = View.GONE
            updateWindowFocus(true)
        }
    }

    private fun updateWindowFocus(panelVisible: Boolean) {
        val pv = panelView ?: return
        val newFlags = if (panelVisible) {
            layoutParams.flags and
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv() and
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            layoutParams.flags or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        if (newFlags != layoutParams.flags) {
            layoutParams.flags = newFlags
            updateViewLayoutSafely(pv, layoutParams)
        }
    }

    private fun ensureViewsAttached() {
        if (!panelAttached) {
            panelView?.let { addViewSafely(it, layoutParams) { panelAttached = true } }
            panelAttached = true
        }
        if (!indicatorAttached) {
            indicatorView?.let { addViewSafely(it, indicatorLayoutParams) { indicatorAttached = true } }
            indicatorAttached = true
        }
        if (!highlightAttached) {
            addViewSafely(highlightView, highlightLayoutParams) { highlightAttached = true }
            highlightAttached = true
        }
    }

    private fun detachViewsIfNeeded() {
        if (panelAttached) { panelView?.let { safeRemoveView(it) }; panelAttached = false }
        if (indicatorAttached) { indicatorView?.let { safeRemoveView(it) }; indicatorAttached = false }
        if (highlightAttached) { safeRemoveView(highlightView); highlightAttached = false }
    }

    // ---------------------------------------------------------------- 搜索逻辑（不变）

    private suspend fun captureAndMatchAccessibility(keyword: String, reducedScope: Boolean): CaptureResult = withContext(Dispatchers.Main) {
        val root = context.rootInActiveWindow
            ?: return@withContext CaptureResult(matches = emptyList(), failed = true)
        val metrics = context.resources.displayMetrics
        val cropRect = if (reducedScope) {
            buildRevealCropRect(metrics.widthPixels, metrics.heightPixels, direction)
        } else {
            Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
        }
        val matches = mutableListOf<Rect>()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        try {
            while (stack.isNotEmpty()) {
                val node = stack.removeLast()
                try {
                    if (node.isVisibleToUser) {
                        val text = node.text ?: node.contentDescription
                        val content = text?.toString()?.trim().orEmpty()
                        if (content.contains(keyword, ignoreCase = true)) {
                            val rect = Rect()
                            node.getBoundsInScreen(rect)
                            if (Rect.intersects(rect, cropRect)) {
                                matches.add(rect)
                            }
                        }
                    }
                    val childCount = node.childCount
                    for (i in 0 until childCount) {
                        node.getChild(i)?.let { child -> stack.add(child) }
                    }
                } finally {
                    if (node !== root) { node.recycle() }
                }
            }
        } finally {
            root.recycle()
        }
        CaptureResult(matches = matches, failed = false)
    }

    private suspend fun captureAndMatch(keyword: String, reducedScope: Boolean): CaptureResult {
        val bitmapHandle = try {
            context.takeManagedScreenshot() ?: return CaptureResult(matches = emptyList(), failed = true)
        } catch (e: Exception) {
            e.printStackTrace()
            return CaptureResult(matches = emptyList(), failed = true)
        }
        return bitmapHandle.use { fullBitmapHandle ->
            val (ocrBitmapHandle, offsetX, offsetY) = if (reducedScope) {
                val fullBitmap = fullBitmapHandle.requireBitmap()
                val cropRect = buildRevealCropRect(fullBitmap.width, fullBitmap.height, direction)
                val cropped = fullBitmapHandle.cropByRect(cropRect)
                    ?: return CaptureResult(matches = emptyList(), failed = true)
                Triple(cropped, cropRect.left, cropRect.top)
            } else {
                val retained = fullBitmapHandle.acquire()
                    ?: return CaptureResult(matches = emptyList(), failed = true)
                Triple(retained, 0, 0)
            }
            val ocrResult = try {
                ocrBitmapHandle.use { handle -> MlKitOcr.detect(handle.requireBitmap()) }
            } catch (e: Exception) {
                e.printStackTrace()
                return CaptureResult(matches = emptyList(), failed = true)
            }
            val matched = ocrResult.filter { item ->
                item.content?.trim()?.contains(keyword, ignoreCase = true) == true
            }
            val shifted = matched.map { item ->
                val rect = item.bound
                Rect(rect.left + offsetX, rect.top + offsetY, rect.right + offsetX, rect.bottom + offsetY)
            }
            CaptureResult(matches = shifted, failed = false)
        }
    }

    private suspend fun performScroll(direction: ScreenDirection): Boolean {
        val metrics = context.resources.displayMetrics
        val x = metrics.widthPixels * 0.5f
        val startY: Float; val endY: Float
        if (direction == ScreenDirection.DOWN) {
            startY = metrics.heightPixels * 0.80f; endY = metrics.heightPixels * 0.20f
        } else {
            startY = metrics.heightPixels * 0.20f; endY = metrics.heightPixels * 0.80f
        }
        return context.performSwipeAsync(x, startY, x, endY, duration = 200L, holdDuration = 10L, useDecelerationCurve = false)
    }

    private fun buildRevealCropRect(width: Int, height: Int, direction: ScreenDirection): Rect {
        val safeWidth = width.coerceAtLeast(1); val safeHeight = height.coerceAtLeast(1)
        val revealHeight = (safeHeight * revealScanRatio).toInt().coerceIn(32, safeHeight)
        val top = if (direction == ScreenDirection.DOWN) (safeHeight - revealHeight).coerceAtLeast(0) else 0
        val bottom = (top + revealHeight).coerceAtMost(safeHeight)
        return Rect(0, top, safeWidth, bottom)
    }

    private suspend fun showFound(rects: List<Rect>, keyword: String, step: Int) {
        withContext(Dispatchers.Main) {
            highlightView.visibility = View.VISIBLE
            highlightView.showRects(rects)
        }
        ToastHelper.showInUIThread(context, R.string.screen_search_found_toast)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchPanel(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    useAccessibility: Boolean,
    onAccessibilityChange: (Boolean) -> Unit,
    direction: ScreenDirection,
    onDirectionChange: (ScreenDirection) -> Unit,
    searching: Boolean,
    onClose: () -> Unit,
    onSearch: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.screen_search_title),
                fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose) {
                Icon(painterResource(R.drawable.baseline_arrow_back_24), contentDescription = null)
            }
        }
        OutlinedTextField(
            value = keyword,
            onValueChange = onKeywordChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.screen_search_hint)) },
            singleLine = true,
            enabled = !searching,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = useAccessibility, onCheckedChange = onAccessibilityChange, enabled = !searching)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.screen_search_accessibility_mode), fontSize = 14.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = direction == ScreenDirection.UP,
                onClick = { onDirectionChange(ScreenDirection.UP) },
                label = { Text(stringResource(R.string.screen_search_direction_up)) },
                enabled = !searching,
            )
            FilterChip(
                selected = direction == ScreenDirection.DOWN,
                onClick = { onDirectionChange(ScreenDirection.DOWN) },
                label = { Text(stringResource(R.string.screen_search_direction_down)) },
                enabled = !searching,
            )
        }
        TextButton(onClick = onSearch, enabled = !searching) {
            Text(stringResource(R.string.screen_search_hint))
        }
    }
}

@Composable
private fun IndicatorContent(searching: Boolean) {
    Column(
        modifier = Modifier.size(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (searching) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp))
        } else {
            Icon(
                painter = painterResource(R.drawable.baseline_check_24),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
