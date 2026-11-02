package io.github.lumiagg.gestureevo.quick

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.animation.PathInterpolator
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.animation.addListener
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.allViews
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.db.APPModel
import io.github.lumiagg.gestureevo.db.getAPPDao
import io.github.lumiagg.gestureevo.gesture.TriggerSide
import io.github.lumiagg.gestureevo.gesture.config.GestureActionType
import io.github.lumiagg.gestureevo.quick.ui.KeyboardOnChangeListener
import io.github.lumiagg.gestureevo.quick.ui.KeyboardType
import io.github.lumiagg.gestureevo.quick.ui.KeyboardView
import io.github.lumiagg.gestureevo.settings.AppearanceSettingsActivity.Companion.KEY_BLUR_ENABLED
import io.github.lumiagg.gestureevo.settings.AppearanceSettingsActivity.Companion.KEY_KEYBOARD_BACKGROUND_BLUR
import io.github.lumiagg.gestureevo.settings.AppearanceSettingsActivity.Companion.KEY_KEYBOARD_CARD_BACKGROUND_BLUR
import io.github.lumiagg.gestureevo.settings.InspireSettingsActivity.Companion.KEY_KEYBOARD_TYPE
import io.github.lumiagg.gestureevo.ui.compose.MiuixEvoTheme
import io.github.lumiagg.gestureevo.utils.ActionProxy
import io.github.lumiagg.gestureevo.utils.CardRegionBlur
import io.github.lumiagg.gestureevo.utils.DynamicColorUtils
import io.github.lumiagg.gestureevo.utils.GlassEffectUtils
import io.github.lumiagg.gestureevo.utils.TransparentActivityLauncher
import io.github.lumiagg.gestureevo.utils.TextUtil
import io.github.lumiagg.gestureevo.utils.coerceInSafe
import io.github.lumiagg.gestureevo.utils.dp2px
import io.github.lumiagg.gestureevo.utils.app.syncAppCatalogIfNeeded
import io.github.lumiagg.gestureevo.utils.icon.IconHelper
import io.github.lumiagg.gestureevo.utils.readKey
import io.github.lumiagg.gestureevo.utils.saveKey
import io.github.lumiagg.gestureevo.utils.settingsStore
import io.github.lumiagg.gestureevo.utils.uriBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

class QuickStartActivity : AppCompatActivity() {
    companion object {
        private const val KEY_KEYBOARD_WIDTH_PORT = "key_keyboard_width_port"
        private const val KEY_KEYBOARD_HEIGHT_PORT = "key_keyboard_height_port"
        private const val KEY_KEYBOARD_LOC_X_PORT = "key_keyboard_loc_x_port"
        private const val KEY_KEYBOARD_LOC_Y_PORT = "key_keyboard_loc_y_port"
        private const val KEY_KEYBOARD_WIDTH_LAND = "key_keyboard_width_land"
        private const val KEY_KEYBOARD_HEIGHT_LAND = "key_keyboard_height_land"
        private const val KEY_KEYBOARD_LOC_X_LAND = "key_keyboard_loc_x_land"
        private const val KEY_KEYBOARD_LOC_Y_LAND = "key_keyboard_loc_y_land"
        private const val DEFAULT_WIDTH_RATIO_PORT = 0.9f
        private const val DEFAULT_HEIGHT_RATIO_PORT = 0.58f
        private const val DEFAULT_WIDTH_RATIO_LAND = 0.62f
        private const val DEFAULT_HEIGHT_RATIO_LAND = 0.72f
        private const val MIN_SIZE = 200
        private const val MAX_SCREEN_RATIO = 0.9f
        private val EMPHASIZED = PathInterpolator(0.2f, 0f, 0f, 1f)
        private val EMPHASIZED_ACCELERATE = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)
        private var inputKeys = ""

        fun start(context: Context, side: String? = null) {
            val intent = Intent(context, QuickStartActivity::class.java).apply {
                side?.let { putExtra(TriggerSide.EXTRA_TRIGGER_SIDE, it) }
            }
            TransparentActivityLauncher.start(context, intent)
        }
    }

    private data class KeySet(val widthKey: String, val heightKey: String, val xKey: String, val yKey: String)
    private lateinit var keyboard: KeyboardView
    private var isResizeMode by mutableStateOf(false)
    private var ready by mutableStateOf(false)
    private var panelWidth by mutableStateOf(0)
    private var panelHeight by mutableStateOf(0)
    private var panelX by mutableStateOf(0f)
    private var panelY by mutableStateOf(0f)
    private var panelAlpha by mutableStateOf(0f)
    private var panelScale by mutableStateOf(0.8f)
    private var appResults by mutableStateOf<List<APPModel>>(emptyList())
    private var isAnimating = false
    private var finishRequested = false
    private var activeAnimator: AnimatorSet? = null
    private var searchJob: Job? = null
    private var currentQuery = ""
    private var keyboardBackgroundBlurEnabled = false
    private var keyboardCardBackgroundBlurEnabled = false
    private val minSize = MIN_SIZE.dp2px.toInt()
    private var triggerSide: String? = null

    private fun isLandscape() = resources.displayMetrics.run { widthPixels > heightPixels }

    private fun getOrientationSpecificKeys() = if (isLandscape()) {
        KeySet(KEY_KEYBOARD_WIDTH_LAND, KEY_KEYBOARD_HEIGHT_LAND, KEY_KEYBOARD_LOC_X_LAND, KEY_KEYBOARD_LOC_Y_LAND)
    } else {
        KeySet(KEY_KEYBOARD_WIDTH_PORT, KEY_KEYBOARD_HEIGHT_PORT, KEY_KEYBOARD_LOC_X_PORT, KEY_KEYBOARD_LOC_Y_PORT)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        triggerSide = intent?.getStringExtra(TriggerSide.EXTRA_TRIGGER_SIDE)
        // Keep the manifest's transparent/immersive window and launcher flags unchanged.
        window.decorView.setBackgroundColor(Color.TRANSPARENT)
        keyboard = KeyboardView(this)
        onBackPressedDispatcher.addCallback(this) { finishWithAnimation() }
        setContent { MiuixEvoTheme { QuickStartContent() } }
        lifecycleScope.launch {
            initAppearanceConfig()
            GlassEffectUtils.applyWindowBlurProgress(window, 0f)
            initializeLayout()
            initKeyboard()
            ready = true
            requestAppDbSync()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        triggerSide = intent.getStringExtra(TriggerSide.EXTRA_TRIGGER_SIDE)
    }

    private suspend fun initAppearanceConfig() {
        val blurMaster = settingsStore.readKey(KEY_BLUR_ENABLED, true)
        keyboardBackgroundBlurEnabled = blurMaster && settingsStore.readKey(KEY_KEYBOARD_BACKGROUND_BLUR, false)
        keyboardCardBackgroundBlurEnabled = blurMaster && settingsStore.readKey(KEY_KEYBOARD_CARD_BACKGROUND_BLUR, false)
    }

    private fun applyKeyboardKeyGlassStyle() {
        keyboard.allViews.forEach { view ->
            val card = view as? MaterialCardView ?: return@forEach
            GlassEffectUtils.applyCardStyle(
                card = card,
                color = DynamicColorUtils.surfaceContainerColor,
                glassEnabled = keyboardCardBackgroundBlurEnabled,
                clearForeground = true,
                blurRegion = false,
            )
        }
    }

    private suspend fun initializeLayout() {
        val keys = getOrientationSpecificKeys()
        val width = settingsStore.readKey(keys.widthKey, -1)
        val height = settingsStore.readKey(keys.heightKey, -1)
        val savedX = settingsStore.readKey(keys.xKey, -1)
        val savedY = settingsStore.readKey(keys.yKey, -1)
        val type = settingsStore.readKey(KEY_KEYBOARD_TYPE, KeyboardType.DOUBLE_KEY.name)
        val metrics = resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels
        val defaultWidth = (screenWidth * if (isLandscape()) DEFAULT_WIDTH_RATIO_LAND else DEFAULT_WIDTH_RATIO_PORT).toInt()
        val defaultHeight = (screenHeight * if (isLandscape()) DEFAULT_HEIGHT_RATIO_LAND else DEFAULT_HEIGHT_RATIO_PORT).toInt()
        val hasSavedPosition = savedX > 0 && savedY > 0
        panelWidth = when {
            width <= 0 -> defaultWidth
            !hasSavedPosition && width < defaultWidth -> defaultWidth
            else -> width
        }.coerceInSafe(minSize, (screenWidth * MAX_SCREEN_RATIO).toInt())
        panelHeight = when {
            height <= 0 -> defaultHeight
            !hasSavedPosition && height < defaultHeight -> defaultHeight
            else -> height
        }.coerceInSafe(minSize, (screenHeight * MAX_SCREEN_RATIO).toInt())
        // The former card had zero elevation/content padding, so stored coordinates
        // remain the same physical pixels without adding a new Compose inset.
        panelX = (if (hasSavedPosition) savedX.toFloat() else (screenWidth - panelWidth) / 2f)
            .coerceInSafe(0f, (screenWidth - panelWidth).coerceAtLeast(0).toFloat())
        panelY = (if (hasSavedPosition) savedY.toFloat() else (screenHeight - panelHeight) / 2f)
            .coerceInSafe(0f, (screenHeight - panelHeight).coerceAtLeast(0).toFloat())
        if (hasSavedPosition && (panelX != savedX.toFloat() || panelY != savedY.toFloat())) saveLayoutState()
        keyboard.keyboardType = KeyboardType.valueOfSafe(type)
        applyKeyboardKeyGlassStyle()
    }

    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
    @Composable
    private fun QuickStartContent() {
        Box(Modifier.fillMaxSize()) {
            // A separate outside hit target never intercepts clicks inside the panel.
            Box(Modifier.fillMaxSize().combinedClickable(onClick = { finishWithAnimation() }))
            if (!ready) return@Box
            val density = LocalDensity.current
            val shape = RoundedCornerShape(24.dp)
            var animationStarted by remember { mutableStateOf(false) }
            Box(
                Modifier.offset { IntOffset(panelX.roundToInt(), panelY.roundToInt()) }
                    .requiredSize(with(density) { panelWidth.toDp() }, with(density) { panelHeight.toDp() })
                    .onGloballyPositioned {
                        if (!animationStarted) {
                            animationStarted = true
                            showWithAnimation()
                        }
                    }
                    .shadow(12.dp, shape, clip = false)
                    .graphicsLayer {
                        alpha = panelAlpha
                        scaleX = panelScale
                        scaleY = panelScale
                        this.shape = shape
                        clip = true
                    }
                    .combinedClickable(onClick = {})
            ) {
                // Native system BackgroundBlurDrawable cannot be drawn by Compose.
                // This empty, noninteractive background is only the regional blur bridge;
                // all page structure, results and drag/resize UI below are Compose.
                if (keyboardCardBackgroundBlurEnabled) {
                    AndroidView(
                        factory = { context ->
                            MaterialCardView(context).apply {
                                radius = 24.dp2px
                                isClickable = false
                                isFocusable = false
                                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                                GlassEffectUtils.applyCardStyle(
                                    card = this,
                                    color = DynamicColorUtils.surfaceContainerHighColor,
                                    glassEnabled = true,
                                    backgroundBlurEnabled = keyboardBackgroundBlurEnabled,
                                    blurRegion = GlassEffectUtils.usesCardRegionBlur(keyboardBackgroundBlurEnabled, true),
                                )
                            }
                        },
                        modifier = Modifier.matchParentSize(),
                        onReset = null,
                        onRelease = { CardRegionBlur.update(it, false, 0f) },
                    )
                }
                Card(
                    modifier = Modifier.fillMaxSize(),
                    colors = CardDefaults.defaultColors(
                        color = if (keyboardCardBackgroundBlurEnabled) ComposeColor.Transparent else MiuixTheme.colorScheme.surface,
                    ),
                ) {
                    Column(Modifier.fillMaxSize()) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().height(80.dp),
                            reverseLayout = true,
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                        ) {
                            items(appResults, key = { it.pkgName }) { model -> QuickStartItem(model) }
                        }
                        // Preserve the custom keyboard's T9/double-key layouts, vibration,
                        // long-press functions and native directional focus navigation.
                        AndroidView(
                            factory = { keyboard },
                            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 5.dp),
                        )
                        val handleScale by animateFloatAsState(if (isResizeMode) 1.5f else 1f, tween(200), label = "resize handle")
                        val touchListener = remember { CombinedTouchListener() }
                        Box(
                            Modifier.fillMaxWidth().height(30.dp)
                                .semantics {
                                    contentDescription = getString(if (isResizeMode) R.string.quick_start_resize_hint else R.string.quick_start_move_hint)
                                }
                                .pointerInteropFilter { touchListener.onTouch(it) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.size(100.dp, 5.dp).graphicsLayer { scaleX = handleScale; scaleY = handleScale }
                                .background(MiuixTheme.colorScheme.onSurfaceVariantSummary, RoundedCornerShape(50)))
                        }
                    }
                }
            }
        }
    }

    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    @Composable
    private fun QuickStartItem(model: APPModel) {
        val bitmap = remember(model.pkgName) { IconHelper.getIcon(this, model.pkgName)?.toBitmap()?.asImageBitmap() }
        var focused by remember { mutableStateOf(false) }
        Column(Modifier.width(70.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(50.dp)
                    .background(if (focused) MiuixTheme.colorScheme.secondaryContainer else ComposeColor.Transparent, RoundedCornerShape(12.dp))
                    .onFocusChanged { focused = it.isFocused }
                    .combinedClickable(
                    onClick = { openAPP(model, false) },
                    onLongClick = { openAPP(model, true) },
                ), contentAlignment = Alignment.Center,
            ) {
                bitmap?.let {
                    // Same 8dp image inset and global icon-rounding preference as the old item.
                    Image(it, model.name, Modifier.size(34.dp).clip(RoundedCornerShape(
                        percent = (IconHelper.iconImageRoundPercent.coerceIn(0f, 1f) * 50).roundToInt(),
                    )))
                }
            }
            Text(model.name, modifier = Modifier.fillMaxWidth().height(20.dp), fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                color = MiuixTheme.colorScheme.onSurface)
        }
    }

    private fun initKeyboard() {
        keyboard.onChangeListener = object : KeyboardOnChangeListener {
            override fun onInput(inputSequence: String) {
                currentQuery = inputSequence
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    appResults = if (inputSequence.isNotEmpty()) withContext(Dispatchers.IO) {
                        filterResult(inputSequence)
                    } else emptyList()
                }
            }

            override fun onEnter(longClick: Boolean) {
                appResults.firstOrNull()?.let { openAPP(it, longClick) }
            }

            override fun onSettings(longClick: Boolean) {
                isResizeMode = !isResizeMode
                keyboard.setInputPrompt(getString(if (isResizeMode) R.string.quick_start_resize_hint else R.string.quick_start_move_hint))
            }
        }
        keyboard.resumeContent(inputKeys)
    }

    private fun requestAppDbSync() {
        lifecycleScope.launch {
            withContext(Dispatchers.Default) { syncAppCatalogIfNeeded(applicationContext) }
            val query = currentQuery
            if (query.isNotEmpty()) {
                val results = withContext(Dispatchers.IO) { filterResult(query) }
                if (currentQuery == query) appResults = results
            }
        }
    }

    private suspend fun filterResult(inputSequence: String): List<APPModel> {
        val resultSet = LinkedHashSet<APPModel>()
        when (keyboard.keyboardType) {
            KeyboardType.T9 -> {
                resultSet.addAll(getAPPDao().findByInitialsT9(inputSequence, APPModel.FLAG_LAUNCHER))
                resultSet.addAll(getAPPDao().findByPinYinT9(inputSequence, APPModel.FLAG_LAUNCHER))
            }
            KeyboardType.DOUBLE_KEY, KeyboardType.FULL_KEY -> {
                resultSet.addAll(getAPPDao().findByInitialsDK(inputSequence, APPModel.FLAG_LAUNCHER))
                resultSet.addAll(getAPPDao().findByPinYinDK(inputSequence, APPModel.FLAG_LAUNCHER))
            }
        }
        return resultSet.toList().sortedWith(when (keyboard.keyboardType) {
            KeyboardType.T9 -> compareByDescending<APPModel> { it.openCount }
                .thenBy { -TextUtil.calculateSimilarity(inputSequence, it.initialsT9) }
                .thenBy { -TextUtil.calculateSimilarity(inputSequence, it.pinyinT9) }
            else -> compareByDescending<APPModel> { it.openCount }
                .thenBy { -TextUtil.calculateSimilarity(inputSequence, it.initialsDK) }
                .thenBy { -TextUtil.calculateSimilarity(inputSequence, it.pinyinDK) }
        })
    }

    private fun showWithAnimation() {
        if (isAnimating || isFinishing || isDestroyed) return
        animatePanel(true) { if (finishRequested) finishWithAnimation() }
    }

    private fun animatePanel(show: Boolean, onEnd: () -> Unit) {
        isAnimating = true
        val from = panelAlpha
        val to = if (show) 1f else 0f
        activeAnimator = AnimatorSet().apply {
            val animators = mutableListOf<Animator>(ValueAnimator.ofFloat(from, to).apply {
                addUpdateListener {
                    panelAlpha = it.animatedValue as Float
                    panelScale = 0.8f + 0.2f * panelAlpha
                }
            })
            if (keyboardBackgroundBlurEnabled) GlassEffectUtils.createWindowBlurAnimator(window, from, to)?.let(animators::add)
            playTogether(animators)
            duration = if (show) 300L else 250L
            interpolator = if (show) EMPHASIZED else EMPHASIZED_ACCELERATE
            addListener(onEnd = {
                isAnimating = false
                if (!isDestroyed) onEnd()
            })
            start()
        }
    }

    private fun openAPP(model: APPModel, freeform: Boolean) {
        lifecycleScope.launch(Dispatchers.Default) {
            val link = uriBuilder(GestureActionType.OPEN_APP)
                .appendQueryParameter("pkg", model.pkgName)
                .appendQueryParameter("freeform", freeform.toString())
                .apply { triggerSide?.let { appendQueryParameter("triggerSide", it) } }.build()
            ActionProxy(this@QuickStartActivity, link.toString()).execute(side = triggerSide)
            finishWithAnimation()
        }
    }

    private fun finishWithAnimation() = lifecycleScope.launch(Dispatchers.Main) {
        finishRequested = true
        if (isAnimating) return@launch
        if (ready && panelAlpha > 0f) animatePanel(false) { finish() } else finish()
    }

    override fun finish() {
        if (::keyboard.isInitialized) inputKeys = keyboard.getInputKeys()
        super.finish()
    }

    override fun onDestroy() {
        activeAnimator?.removeAllListeners()
        activeAnimator?.cancel()
        searchJob?.cancel()
        if (::keyboard.isInitialized) keyboard.onChangeListener = object : KeyboardOnChangeListener {}
        GlassEffectUtils.applyWindowBlur(window, false)
        super.onDestroy()
    }

    private fun saveLayoutState() {
        // Snapshot on Main: no background-thread reads of View/Compose geometry.
        val keys = getOrientationSpecificKeys()
        val width = panelWidth
        val height = panelHeight
        val x = panelX.roundToInt()
        val y = panelY.roundToInt()
        lifecycleScope.launch(Dispatchers.IO) {
            settingsStore.apply {
                saveKey(keys.widthKey, width)
                saveKey(keys.heightKey, height)
                saveKey(keys.xKey, x)
                saveKey(keys.yKey, y)
            }
        }
    }

    private inner class CombinedTouchListener {
        private var initialTouchX = 0f
        private var initialTouchY = 0f
        private var initialWidth = 0
        private var initialHeight = 0
        private var initialX = 0f
        private var initialY = 0f
        private var isDragging = false

        fun onTouch(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = true
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    initialWidth = panelWidth
                    initialHeight = panelHeight
                    initialX = panelX
                    initialY = panelY
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!isDragging) return true
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY
                    if (isResizeMode) updateSize(deltaX, deltaY) else updatePosition(deltaX, deltaY)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!isDragging) return true
                    isDragging = false
                    saveLayoutState()
                }
            }
            return true
        }

        private fun updatePosition(deltaX: Float, deltaY: Float) {
            val metrics = resources.displayMetrics
            panelX = (initialX + deltaX).coerceInSafe(0f, (metrics.widthPixels - panelWidth).coerceAtLeast(0).toFloat())
            panelY = (initialY + deltaY).coerceInSafe(0f, (metrics.heightPixels - panelHeight).coerceAtLeast(0).toFloat())
        }

        private fun updateSize(deltaX: Float, deltaY: Float) {
            val metrics = resources.displayMetrics
            panelWidth = (initialWidth + deltaX).toInt().coerceInSafe(minSize, (metrics.widthPixels * MAX_SCREEN_RATIO).toInt())
            panelHeight = (initialHeight + deltaY).toInt().coerceInSafe(minSize, (metrics.heightPixels * MAX_SCREEN_RATIO).toInt())
            updatePosition(0f, 0f)
        }
    }
}
