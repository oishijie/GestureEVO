package io.github.lumiagg.gestureevo.inspire.ui

import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import io.github.lumiagg.gestureevo.inspire.InspireDisplayMode

/** 取词浮层显示模式控制器——视图由宿主 [InspireContentView] 直接传入（不再依赖 ViewBinding）。 */
internal class InspireDisplayModeController(
    private val screenshotPreviewContainer: View?,
    private val actionButtons: View?,
    private val imageActionButtons: View?,
    private val textActionButtons: View?,
    private val bigBangLayoutNestedScroll: View?,
    private val ocrImageTextOverlay: View?,
    private val hostView: View,
    private val previewHeightProvider: (InspireDisplayMode) -> Int,
    private val onPreviewLayoutChanged: () -> Unit,
) {
    fun apply(displayMode: InspireDisplayMode) {
        val isImageTextSelection = displayMode.isImageTextSelection
        screenshotPreviewContainer?.visibility = VISIBLE
        actionButtons?.visibility = VISIBLE
        imageActionButtons?.visibility = VISIBLE
        textActionButtons?.visibility = if (isImageTextSelection) GONE else VISIBLE
        bigBangLayoutNestedScroll?.visibility = if (isImageTextSelection) GONE else VISIBLE
        bigBangLayoutNestedScroll?.let { applyHeight(it, displayMode.bigBangHeight) }
        screenshotPreviewContainer?.let {
            applyHeight(it, previewHeightProvider(displayMode))
            it.requestLayout()
        }
        bigBangLayoutNestedScroll?.requestLayout()
        refreshAfterMeasure(displayMode)
    }

    fun hideAll() {
        screenshotPreviewContainer?.visibility = GONE
        actionButtons?.visibility = GONE
        imageActionButtons?.visibility = GONE
        textActionButtons?.visibility = GONE
        bigBangLayoutNestedScroll?.visibility = GONE
        ocrImageTextOverlay?.visibility = GONE
    }

    private fun refreshAfterMeasure(displayMode: InspireDisplayMode) {
        hostView.post {
            screenshotPreviewContainer?.let {
                applyHeight(it, previewHeightProvider(displayMode))
                it.requestLayout()
                onPreviewLayoutChanged()
            }
        }
    }

    private fun applyHeight(view: View, height: Int) {
        val params = view.layoutParams ?: return
        if (params.height == height) return
        params.height = height
        view.layoutParams = params
    }

    private val InspireDisplayMode.bigBangHeight: Int
        get() = if (isImageTextSelection) 0 else InspireContentLayoutMetrics.BIG_BANG_CONTENT_HEIGHT_PX
}
