package io.github.lumiagg.gestureevo.settings.ui

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.toColorInt
import androidx.lifecycle.LifecycleCoroutineScope
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.gesture.config.FreeTriggerConfig
import io.github.lumiagg.gestureevo.gesture.ui.GestureType
import io.github.lumiagg.gestureevo.utils.DynamicColorUtils
import com.squareup.moshi.JsonClass
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@JsonClass(generateAdapter = true)
data class GestureAngleStorge(val s: Float, val e: Float)

// Gesture angle configuration data
data class GestureAngleConfig(
    val gestureType: GestureType,
    val displayName: String,
    val color: Int,
    val role: GestureAngleRole,
    var startAngle: Float, // Start angle in degrees
    var endAngle: Float,   // End angle in degrees
    var isEnabled: Boolean = true
)

enum class GestureAngleRole {
    RIGHT,
    OBLIQUE_UP_RIGHT,
    UP,
    OBLIQUE_UP_LEFT,
    LEFT,
    OBLIQUE_DOWN_LEFT,
    DOWN,
    OBLIQUE_DOWN_RIGHT
}

interface GestureAngleConfigCallback {
    fun onAngleConfigChanged(configs: List<GestureAngleConfig>)
}

class GestureAngleConfigBottomSheet(
    private val activity: Activity,
    private val coroutineScope: LifecycleCoroutineScope,
    private val triggerConfig: FreeTriggerConfig,
    private val callback: GestureAngleConfigCallback
) {
    private val gestureConfigs = mutableListOf<GestureAngleConfig>()

    // Gesture color definitions
    private val gestureColors = mapOf(
        GestureType.SWIPE_RIGHT to "#FF5722".toColorInt(),
        GestureType.SWIPE_UP to "#4CAF50".toColorInt(),
        GestureType.SWIPE_LEFT to "#2196F3".toColorInt(),
        GestureType.SWIPE_DOWN to "#FF9800".toColorInt(),
        GestureType.SWIPE_OBLIQUE_UP to "#9C27B0".toColorInt(),
        GestureType.SWIPE_OBLIQUE_DOWN to "#607D8B".toColorInt()
    )

    fun show() {
        initGestureConfigs()
        restoreGestureAngle()
        // A fresh, detached draft per opening; only Save notifies the caller.
        val initialDraft = gestureConfigs.map { it.copy() }
        showMiuixSheet(activity, dismissOnOutside = false) { dismiss ->
            var draft by remember { mutableStateOf(initialDraft) }
            val dialColor = MiuixTheme.colorScheme.primary.toArgb()
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.gesture_angle_config_title_text),
                    modifier = Modifier.padding(top = 24.dp),
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Column(
                    modifier = Modifier.weight(1f, fill = false)
                        .heightIn(max = 500.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.gesture_angle_section_title),
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.gesture_angle_section_desc),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    // Only the interactive drawing stays a View, not the XML page.
                    // The dial edits these draft objects in place and keeps its original
                    // mirrored boundaries, 360-degree coverage and minimum sweep rules.
                    AndroidView(
                        modifier = Modifier.fillMaxWidth().height(340.dp),
                        factory = { context ->
                            GestureAngleDialView(context).apply {
                                contentDescription = context.getString(
                                    R.string.gesture_angle_section_content_desc
                                )
                            }
                        },
                        update = { view ->
                            view.setGestureConfigs(draft)
                            view.setDialColor(dialColor)
                        }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    TextButton(
                        text = stringResource(R.string.gesture_angle_config_reset),
                        modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                        onClick = {
                            initGestureConfigs()
                            draft = gestureConfigs.map { it.copy() }
                        }
                    )
                    TextButton(
                        text = stringResource(R.string.gesture_angle_config_save),
                        modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        onClick = {
                            callback.onAngleConfigChanged(draft.map { it.copy() })
                            dismiss()
                        }
                    )
                }
            }
        }
    }

    private fun initGestureConfigs() {
        gestureConfigs.clear()

        // Initialize default angle ranges (full 360° coverage, no overlap/gap)
        gestureConfigs.add(
            GestureAngleConfig(
                GestureType.SWIPE_RIGHT,
                activity.getString(R.string.gesture_angle_swipe_right),
                gestureColors[GestureType.SWIPE_RIGHT]!!,
                GestureAngleRole.RIGHT,
                330f,
                30f
            )
        )

        gestureConfigs.add(
            GestureAngleConfig(
                GestureType.SWIPE_OBLIQUE_UP,
                activity.getString(R.string.gesture_angle_swipe_oblique_up_right),
                gestureColors[GestureType.SWIPE_OBLIQUE_UP]!!,
                GestureAngleRole.OBLIQUE_UP_RIGHT,
                30f,
                60f
            )
        )

        gestureConfigs.add(
            GestureAngleConfig(
                GestureType.SWIPE_OBLIQUE_UP,
                activity.getString(R.string.gesture_angle_swipe_oblique_up_left),
                gestureColors[GestureType.SWIPE_OBLIQUE_UP]!!,
                GestureAngleRole.OBLIQUE_UP_LEFT,
                120f,
                150f
            )
        )

        gestureConfigs.add(
            GestureAngleConfig(
                GestureType.SWIPE_UP,
                activity.getString(R.string.gesture_angle_swipe_up),
                gestureColors[GestureType.SWIPE_UP]!!,
                GestureAngleRole.UP,
                60f,
                120f
            )
        )

        gestureConfigs.add(
            GestureAngleConfig(
                GestureType.SWIPE_LEFT,
                activity.getString(R.string.gesture_angle_swipe_left),
                gestureColors[GestureType.SWIPE_LEFT]!!,
                GestureAngleRole.LEFT,
                150f,
                210f
            )
        )

        gestureConfigs.add(
            GestureAngleConfig(
                GestureType.SWIPE_OBLIQUE_DOWN,
                activity.getString(R.string.gesture_angle_swipe_oblique_down_left),
                gestureColors[GestureType.SWIPE_OBLIQUE_DOWN]!!,
                GestureAngleRole.OBLIQUE_DOWN_LEFT,
                210f,
                240f
            )
        )
        gestureConfigs.add(
            GestureAngleConfig(
                GestureType.SWIPE_OBLIQUE_DOWN,
                activity.getString(R.string.gesture_angle_swipe_oblique_down_right),
                gestureColors[GestureType.SWIPE_OBLIQUE_DOWN]!!,
                GestureAngleRole.OBLIQUE_DOWN_RIGHT,
                300f,
                330f
            )
        )

        gestureConfigs.add(
            GestureAngleConfig(
                GestureType.SWIPE_DOWN,
                activity.getString(R.string.gesture_angle_swipe_down),
                gestureColors[GestureType.SWIPE_DOWN]!!,
                GestureAngleRole.DOWN,
                240f,
                300f
            )
        )
    }

    private fun restoreGestureAngle() {
        gestureConfigs.forEach {
            val angles = triggerConfig.gestureAngleMap[it.gestureType]
            if (angles != null) {
                it.startAngle = angles.s
                it.endAngle = angles.e
            }
        }
    }

}

class GestureAngleDialView @JvmOverloads constructor(
    context: android.content.Context,
    attrs: android.util.AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPath = Path()
    private val gestureConfigs = mutableListOf<GestureAngleConfig>()
    private val orderedConfigs = mutableListOf<GestureAngleConfig>()
    private val boundaries = mutableListOf<Float>()
    private var angleA = 30f
    private var angleB = 60f

    private var centerX = 0f
    private var centerY = 0f
    private var outerRadius = 0f
    private var circleRadius = 0f
    private var handleRadius = 0f
    private var selectedIndex: Int? = null
    private var activeBoundaryIndex: Int? = null
    private var onAngleChangeListener: ((List<GestureAngleConfig>) -> Unit)? = null

    private val colorPrimary: Int
        get() = DynamicColorUtils.primaryColor
    private val ringHitSlop by lazy { dp(14f) }
    private val edgeHitSlop by lazy { dp(12f) }
    private val minSweep = 8f
    private val roleOrder = listOf(
        GestureAngleRole.RIGHT,
        GestureAngleRole.OBLIQUE_UP_RIGHT,
        GestureAngleRole.UP,
        GestureAngleRole.OBLIQUE_UP_LEFT,
        GestureAngleRole.LEFT,
        GestureAngleRole.OBLIQUE_DOWN_LEFT,
        GestureAngleRole.DOWN,
        GestureAngleRole.OBLIQUE_DOWN_RIGHT
    )
    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        circlePaint.color = colorPrimary
        circlePaint.style = Paint.Style.FILL
        dividerPaint.apply {
            color = colorPrimary
            style = Paint.Style.STROKE
            strokeWidth = dp(2f)
            alpha = 200
        }
        labelPaint.apply {
            color = colorPrimary
            textAlign = Paint.Align.CENTER
            textSize = dp(13f)
        }
        highlightPaint.apply {
            color = colorPrimary
            style = Paint.Style.FILL
            alpha = 40
        }
        handlePaint.apply {
            color = colorPrimary
            style = Paint.Style.FILL
        }
    }

    // The Compose host supplies the current semantic primary color; XML users
    // retain the original DynamicColorUtils fallback from initialization.
    fun setDialColor(color: Int) {
        circlePaint.color = color
        dividerPaint.color = color
        dividerPaint.alpha = 200
        labelPaint.color = color
        highlightPaint.color = color
        highlightPaint.alpha = 40
        handlePaint.color = color
        invalidate()
    }

    fun setGestureConfigs(configs: List<GestureAngleConfig>) {
        gestureConfigs.clear()
        gestureConfigs.addAll(configs)
        rebuildOrder()
        rebuildAnglesFromConfigs()
        rebuildBoundariesFromAngles()
        invalidate()
    }

    fun setOnAngleChangeListener(listener: (List<GestureAngleConfig>) -> Unit) {
        onAngleChangeListener = listener
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        outerRadius = minOf(w, h) / 2f - dp(12f)
        circleRadius = outerRadius * 0.25f
        handleRadius = dp(7f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (gestureConfigs.isEmpty()) return

        drawCenterCircle(canvas)
        drawSelectedRegion(canvas)
        drawDividerLines(canvas)
        drawLabels(canvas)
        drawSelectedHandles(canvas)
    }

    private fun drawCenterCircle(canvas: Canvas) {
        canvas.drawCircle(centerX, centerY, circleRadius, circlePaint)
    }

    private fun drawSelectedRegion(canvas: Canvas) {
        val index = selectedIndex ?: return
        if (boundaries.isEmpty()) return
        val start = boundaries[index]
        val end =
            if (index == boundaries.lastIndex) boundaries.first() + 360f else boundaries[index + 1]
        val sweep = end - start
        val rectOuter = RectF(
            centerX - outerRadius,
            centerY - outerRadius,
            centerX + outerRadius,
            centerY + outerRadius
        )
        val rectInner = RectF(
            centerX - circleRadius,
            centerY - circleRadius,
            centerX + circleRadius,
            centerY + circleRadius
        )
        ringPath.reset()
        ringPath.arcTo(rectOuter, -start, -sweep)
        ringPath.arcTo(rectInner, -(start + sweep), sweep, false)
        ringPath.close()
        canvas.drawPath(ringPath, highlightPaint)
    }

    private fun drawDividerLines(canvas: Canvas) {
        if (boundaries.isEmpty()) return
        boundaries.forEach { angle ->
            val normalized = normalizeAngle(angle)
            val start = pointOnCircle(normalized, outerRadius)
            val end = pointOnCircle(normalized, circleRadius)
            canvas.drawLine(start.first, start.second, end.first, end.second, dividerPaint)
        }
    }

    private fun drawLabels(canvas: Canvas) {
        if (boundaries.isEmpty() || orderedConfigs.isEmpty()) return
        val labelRadius = (circleRadius + outerRadius) / 1.5f
        orderedConfigs.forEachIndexed { index, config ->
            val start = boundaries[index]
            val end =
                if (index == boundaries.lastIndex) boundaries.first() + 360f else boundaries[index + 1]
            val mid = (start + end) / 2f
            val angle = normalizeAngle(mid)
            val point = pointOnCircle(angle, labelRadius)
            val display = config.displayName
            canvas.drawText(display, point.first, point.second + dp(4f), labelPaint)
        }
    }

    private fun drawSelectedHandles(canvas: Canvas) {
        val index = selectedIndex ?: return
        if (boundaries.isEmpty()) return
        val start = normalizeAngle(boundaries[index])
        val end = normalizeAngle(
            if (index == boundaries.lastIndex) boundaries.first() else boundaries[index + 1]
        )
        val startPoint = pointOnCircle(start, outerRadius)
        val endPoint = pointOnCircle(end, outerRadius)
        canvas.drawCircle(startPoint.first, startPoint.second, handleRadius, handlePaint)
        canvas.drawCircle(endPoint.first, endPoint.second, handleRadius, handlePaint)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val dist = distanceToCenter(event.x, event.y)
                if (!isInRingArea(dist)) return false

                val touchAngle = calculateAngle(event.x, event.y)
                val selectedBoundary = findSelectedBoundaryHit(touchAngle, dist)
                if (selectedBoundary != null) {
                    activeBoundaryIndex = selectedBoundary
                    parent?.requestDisallowInterceptTouchEvent(true)
                    invalidate()
                    return true
                }

                val boundaryHit = findBoundaryHit(event.x, event.y)
                if (boundaryHit != null) {
                    activeBoundaryIndex = boundaryHit
                    selectedIndex = boundaryHit % orderedConfigs.size
                    parent?.requestDisallowInterceptTouchEvent(true)
                    invalidate()
                    return true
                }

                val tappedIndex = findRegionIndex(event.x, event.y)
                if (tappedIndex != null) {
                    selectedIndex = tappedIndex
                    parent?.requestDisallowInterceptTouchEvent(true)
                    invalidate()
                    return true
                }

                return false
            }

            MotionEvent.ACTION_MOVE -> {
                val boundaryIndex = activeBoundaryIndex
                if (boundaryIndex != null) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    val angle = calculateAngle(event.x, event.y)
                    applyBoundaryMove(boundaryIndex, angle)
                    onAngleChangeListener?.invoke(gestureConfigs)
                    invalidate()
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (event.action == MotionEvent.ACTION_UP && activeBoundaryIndex == null) performClick()
                activeBoundaryIndex = null
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return false
    }

    private fun calculateAngle(x: Float, y: Float): Float {
        val deltaX = x - centerX
        val deltaY = y - centerY
        var angle = Math.toDegrees(atan2(-deltaY.toDouble(), deltaX.toDouble())).toFloat()
        if (angle < 0) angle += 360f
        return angle
    }

    private fun findRegionIndex(x: Float, y: Float): Int? {
        if (boundaries.isEmpty()) return null
        val angle = normalizeAngle(calculateAngle(x, y))
        for (i in boundaries.indices) {
            val start = normalizeAngle(boundaries[i])
            val end = normalizeAngle(if (i == boundaries.lastIndex) boundaries.first() else boundaries[i + 1])
            val inRange = if (start <= end) {
                angle >= start && angle < end
            } else {
                angle >= start || angle < end
            }
            if (inRange) return i
        }
        return null
    }

    private fun findSelectedBoundaryHit(touchAngle: Float, dist: Float): Int? {
        val index = selectedIndex ?: return null
        if (boundaries.isEmpty()) return null
        val startBoundary = boundaries[index]
        val endBoundary =
            if (index == boundaries.lastIndex) boundaries.first() + 360f else boundaries[index + 1]
        val startAngle = unwrapAngle(touchAngle, startBoundary)
        val endAngle = unwrapAngle(touchAngle, endBoundary)
        val startHit = isNearBoundaryLine(startAngle, dist, startBoundary)
        val endHit = isNearBoundaryLine(endAngle, dist, endBoundary)
        return when {
            startHit -> index
            endHit -> if (index == boundaries.lastIndex) 0 else index + 1
            else -> null
        }
    }

    private fun findBoundaryHit(x: Float, y: Float): Int? {
        if (boundaries.isEmpty()) return null
        val dist = distanceToCenter(x, y)
        if (!isInRingArea(dist)) return null
        val touchAngle = calculateAngle(x, y)
        var closest: Int? = null
        var minDistance = Float.MAX_VALUE
        boundaries.forEachIndexed { index, boundary ->
            val compareAngle = unwrapAngle(touchAngle, boundary)
            val diff = shortestAngleDistance(compareAngle, boundary)
            if (diff < minDistance && isNearBoundaryLine(compareAngle, dist, boundary)) {
                minDistance = diff
                closest = index
            }
        }
        return closest
    }

    private fun applyBoundaryMove(index: Int, angle: Float) {
        if (boundaries.isEmpty()) return
        when (boundaryType(index)) {
            BoundaryType.HORIZONTAL -> angleA = angleAFromBoundary(index, angle)
            BoundaryType.VERTICAL -> angleB = angleBFromBoundary(index, angle)
        }
        clampAngles()
        rebuildBoundariesFromAngles()
    }

    private fun shortestAngleDistance(a: Float, b: Float): Float {
        val diff = ((b - a + 540f) % 360f) - 180f
        return kotlin.math.abs(diff)
    }

    private fun sweepAngle(start: Float, end: Float): Float {
        val s = normalizeAngle(start)
        val e = normalizeAngle(end)
        var sweep = e - s
        if (sweep < 0f) sweep += 360f
        return sweep
    }

    private fun normalizeAngle(angle: Float): Float {
        var value = angle % 360f
        if (value < 0f) value += 360f
        return value
    }

    private fun pointOnCircle(angle: Float): Pair<Float, Float> {
        return pointOnCircle(angle, outerRadius)
    }

    private fun pointOnCircle(angle: Float, customRadius: Float): Pair<Float, Float> {
        val angleRad = Math.toRadians(angle.toDouble())
        val x = centerX + customRadius * cos(angleRad).toFloat()
        val y = centerY - customRadius * sin(angleRad).toFloat()
        return x to y
    }

    private fun isNearBoundaryLine(touchAngle: Float, dist: Float, edgeAngle: Float): Boolean {
        val diffRad = Math.toRadians(shortestAngleDistance(touchAngle, edgeAngle).toDouble())
        val perpendicular = dist * sin(diffRad).toFloat()
        return perpendicular <= edgeHitSlop
    }

    private fun distanceToCenter(x: Float, y: Float): Float {
        return sqrt((x - centerX).pow(2) + (y - centerY).pow(2))
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun isInRingArea(dist: Float): Boolean {
        return dist >= circleRadius - ringHitSlop && dist <= outerRadius + ringHitSlop
    }

    private fun unwrapAngle(angle: Float, reference: Float): Float {
        var value = angle
        while (value - reference > 180f) value -= 360f
        while (value - reference < -180f) value += 360f
        return value
    }

    private fun rebuildOrder() {
        orderedConfigs.clear()
        val map = gestureConfigs.associateBy { it.role }
        roleOrder.forEach { role ->
            map[role]?.let { orderedConfigs.add(it) }
        }
        gestureConfigs.forEach { config ->
            if (!orderedConfigs.contains(config)) {
                orderedConfigs.add(config)
            }
        }
        if (selectedIndex == null && orderedConfigs.isNotEmpty()) {
            selectedIndex = 0
        }
    }

    private fun rebuildAnglesFromConfigs() {
        val right = orderedConfigs.firstOrNull { it.role == GestureAngleRole.RIGHT }
        val left = orderedConfigs.firstOrNull { it.role == GestureAngleRole.LEFT }
        val up = orderedConfigs.firstOrNull { it.role == GestureAngleRole.UP }
        val down = orderedConfigs.firstOrNull { it.role == GestureAngleRole.DOWN }
        if (right == null || left == null || up == null || down == null) return

        val horizontalSweep =
            (sweepAngle(right.startAngle, right.endAngle) + sweepAngle(left.startAngle, left.endAngle)) / 2f
        val verticalSweep =
            (sweepAngle(up.startAngle, up.endAngle) + sweepAngle(down.startAngle, down.endAngle)) / 2f

        angleA = horizontalSweep / 2f
        angleB = (180f - verticalSweep) / 2f
        clampAngles()
    }

    private fun rebuildBoundariesFromAngles() {
        boundaries.clear()
        if (orderedConfigs.isEmpty()) return
        val obliqueSweep = angleB - angleA
        val verticalSweep = 180f - 2f * angleB
        val sweeps = listOf(
            2f * angleA,
            obliqueSweep,
            verticalSweep,
            obliqueSweep,
            2f * angleA,
            obliqueSweep,
            verticalSweep,
            obliqueSweep
        )
        var current = 360f - angleA
        boundaries.add(current)
        for (i in 1 until sweeps.size) {
            current += sweeps[i - 1]
            boundaries.add(current)
        }
        syncConfigsFromBoundaries()
    }

    private fun syncConfigsFromBoundaries() {
        if (orderedConfigs.isEmpty() || boundaries.isEmpty()) return
        val base = boundaries.first()
        orderedConfigs.forEachIndexed { index, config ->
            val start = boundaries[index]
            val end = if (index == boundaries.lastIndex) base + 360f else boundaries[index + 1]
            config.startAngle = normalizeAngle(start)
            config.endAngle = normalizeAngle(end)
        }
    }

    private fun clampAngles() {
        val minA = minSweep / 2f
        val maxB = (180f - minSweep) / 2f
        angleA = angleA.coerceIn(minA, maxB - minSweep)
        angleB = angleB.coerceIn(angleA + minSweep, maxB)
    }

    private fun boundaryType(index: Int): BoundaryType {
        return if (index == 0 || index == 1 || index == 4 || index == 5) {
            BoundaryType.HORIZONTAL
        } else {
            BoundaryType.VERTICAL
        }
    }

    private fun angleAFromBoundary(index: Int, angle: Float): Float {
        val reference = boundaries.getOrNull(index) ?: return angleA
        val a = unwrapAngle(angle, reference)
        return when (index) {
            0 -> 360f - a
            1 -> a - 360f
            4 -> 540f - a
            5 -> a - 540f
            else -> angleA
        }
    }

    private fun angleBFromBoundary(index: Int, angle: Float): Float {
        val reference = boundaries.getOrNull(index) ?: return angleB
        val a = unwrapAngle(angle, reference)
        return when (index) {
            2 -> a - 360f
            3 -> 540f - a
            6 -> a - 540f
            7 -> 720f - a
            else -> angleB
        }
    }

    private enum class BoundaryType {
        HORIZONTAL,
        VERTICAL
    }
}
