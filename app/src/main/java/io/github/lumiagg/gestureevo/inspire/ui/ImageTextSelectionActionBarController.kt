package io.github.lumiagg.gestureevo.inspire.ui

import android.graphics.RectF
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.View.MeasureSpec
import android.widget.ImageView
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.inspire.InspireDisplayMode
import io.github.lumiagg.gestureevo.utils.UrlDetector
import io.github.lumiagg.gestureevo.utils.dp2px
import kotlin.math.min

internal class ImageTextSelectionActionBarController(
    private val imageSelectionActionBarContainer: android.view.ViewGroup?,
    private val screenshotPreviewContainer: View?,
    private val actionProvider: () -> BigBangActionWrapper?,
) {
    private var actionBarRoot: View? = null

    // 子视图引用（inflate 后通过 findViewById 获取）
    private var editIcon: ImageView? = null
    private var shareIcon: ImageView? = null
    private var pinIcon: ImageView? = null
    private var transitionIcon: ImageView? = null
    private var copySelectedIcon: ImageView? = null
    private var openUrlIcon: ImageView? = null
    private var sendToEVOIcon: ImageView? = null

    init {
        imageSelectionActionBarContainer?.let { container -> setupActions(container) }
    }

    fun update(
        displayMode: InspireDisplayMode,
        selectedText: String,
        selectionBounds: RectF?,
        isInteracting: Boolean = false,
    ) {
        val actionBar = actionBarRoot ?: return
        val showActionBar = displayMode.isImageTextSelection && selectedText.isNotBlank() && !isInteracting
        openUrlIcon?.visibility = if (UrlDetector.hasUrl(selectedText)) VISIBLE else GONE
        if (!showActionBar) {
            setVisibility(GONE)
            return
        }
        setVisibility(INVISIBLE)
        position(selectionBounds) {
            setVisibility(VISIBLE)
            actionProvider()?.handleSelected()
        }
    }

    fun hide() {
        setVisibility(GONE)
    }

    private fun setupActions(container: android.view.ViewGroup) {
        val root = LayoutInflater.from(container.context).inflate(
            R.layout.bigbang_function_item, container, true
        )
        actionBarRoot = root
        root.elevation = 4f
        root.visibility = GONE
        editIcon = root.findViewById<ImageView>(R.id.edit)?.apply { visibility = GONE }
        shareIcon = root.findViewById<ImageView>(R.id.share)?.apply {
            setOnClickListener { actionProvider()?.handleShare() }
        }
        pinIcon = root.findViewById<ImageView>(R.id.pin)?.apply {
            setOnClickListener { actionProvider()?.handlePin() }
        }
        transitionIcon = root.findViewById<ImageView>(R.id.transition)?.apply {
            setOnClickListener { actionProvider()?.handleTranslation() }
        }
        copySelectedIcon = root.findViewById<ImageView>(R.id.copySelected)?.apply {
            setOnClickListener { actionProvider()?.handleCopy(false) }
            setOnLongClickListener {
                actionProvider()?.handleCopy(true)
                true
            }
        }
        openUrlIcon = root.findViewById<ImageView>(R.id.openUrl)?.apply {
            setOnClickListener { actionProvider()?.handleBigBangUrlClick() }
        }
        sendToEVOIcon = root.findViewById<ImageView>(R.id.sendToEVO)?.apply {
            setOnClickListener { actionProvider()?.handleSendToEVO(false) }
            setOnLongClickListener {
                actionProvider()?.handleSendToEVO(true)
                true
            }
        }
    }

    private fun setVisibility(visibility: Int) {
        imageSelectionActionBarContainer?.visibility = visibility
        actionBarRoot?.visibility = visibility
    }

    private fun position(selectionBounds: RectF?, onPositioned: () -> Unit) {
        val container = imageSelectionActionBarContainer ?: return
        val previewContainer = screenshotPreviewContainer ?: return
        val bounds = selectionBounds ?: return
        val positionActionBar = {
            val margin = 8.dp2px
            val maxActionBarWidth = (previewContainer.width - margin * 2).coerceAtLeast(0f).toInt()
            container.measure(
                MeasureSpec.makeMeasureSpec(maxActionBarWidth, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(previewContainer.height, MeasureSpec.AT_MOST),
            )
            val actionBarWidth = min(container.measuredWidth.toFloat(), maxActionBarWidth.toFloat())
            val actionBarHeight = container.measuredHeight.toFloat()
            val desiredX = bounds.centerX() - actionBarWidth / 2f
            val aboveY = bounds.top - actionBarHeight - margin
            val belowY = bounds.bottom + margin
            val desiredY = if (aboveY >= margin) aboveY else belowY
            val maxX = (previewContainer.width - actionBarWidth - margin).coerceAtLeast(margin)
            val maxY = (previewContainer.height - actionBarHeight - margin).coerceAtLeast(margin)
            container.x = desiredX.coerceIn(margin, maxX)
            container.y = desiredY.coerceIn(margin, maxY)
            onPositioned()
        }
        if (previewContainer.width > 0 && previewContainer.height > 0) {
            positionActionBar()
        } else {
            container.post { positionActionBar() }
        }
    }
}
