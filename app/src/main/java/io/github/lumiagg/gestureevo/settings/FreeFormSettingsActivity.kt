package io.github.lumiagg.gestureevo.settings

import FreeFormUtils.getHeight
import FreeFormUtils.getLeft
import FreeFormUtils.getTop
import FreeFormUtils.getWidth
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.lifecycle.lifecycleScope
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.settings.ui.ResizableRectangleView
import io.github.lumiagg.gestureevo.ui.compose.MiuixEvoTheme
import io.github.lumiagg.gestureevo.ui.miuix.MiuixCard
import io.github.lumiagg.gestureevo.ui.miuix.MiuixScreen
import io.github.lumiagg.gestureevo.utils.dp2px
import io.github.lumiagg.gestureevo.utils.px2dp
import io.github.lumiagg.gestureevo.utils.readKey
import io.github.lumiagg.gestureevo.utils.saveKey
import io.github.lumiagg.gestureevo.utils.settingsStore
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

class FreeFormSettingsActivity : AppCompatActivity() {
    private var currentOrientation by mutableStateOf(Configuration.ORIENTATION_PORTRAIT)
    private var selectedCode by mutableStateOf(-1)
    private var followTriggerSide by mutableStateOf(false)
    private var shouldDisableSizeAdjustment by mutableStateOf(false)
    private var settingsReady by mutableStateOf(false)
    private var editing by mutableStateOf(false)
    private var rectangle by mutableStateOf(RectF())
    private var targetSections by mutableStateOf(emptySet<Int>())
    private var targetAnchor by mutableStateOf(View.NO_ID)
    private var resizableRectangle: ResizableRectangleView? = null

    private val modeCodes = listOf(-1, 5, 100, 102, 106, 11, -2, MODE_SUNSHINE)
    private val modeLabels: List<String>
        get() = listOf(
            R.string.freeform_mode_default, R.string.freeform_mode_1,
            R.string.freeform_mode_2_oppo, R.string.freeform_mode_3_hw,
            R.string.freeform_mode_4_vivo_share, R.string.freeform_mode_5_flyme,
            R.string.freeform_mode_6_nubia, R.string.freeform_mode_7_sunshine,
        ).map(::getString)

    companion object {
        private const val MODE_SUNSHINE = -3

        const val KEY_ORIENTATION = "current_orientation"
        const val KEY_USE_MI_WINDOW = "use_mi_window"
        const val KEY_FREEFORM_CODE = "freeform_code"
        const val KEY_FREEFORM_FOLLOW_TRIGGER_SIDE = "freeform_follow_trigger_side"

        const val KEY_WIDTH = "freeform_width_dp"
        const val KEY_HEIGHT = "freeform_height_dp"
        const val KEY_LEFT_MARGIN = "freeform_left_margin_dp"
        const val KEY_TOP_MARGIN = "freeform_top_margin_dp"
        const val KEY_EDGE_MARGIN = "freeform_edge_margin_dp"

        fun getSettingsKey(baseKey: String, orientation: Int): String {
            val suffix =
                if (orientation == Configuration.ORIENTATION_PORTRAIT) "_portrait" else "_landscape"
            return "${baseKey}${suffix}"
        }

        private const val STATE_EDITING = "freeform_editor_visible"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentOrientation = savedInstanceState?.getInt(
            KEY_ORIENTATION, Configuration.ORIENTATION_PORTRAIT,
        ) ?: Configuration.ORIENTATION_PORTRAIT
        editing = savedInstanceState?.getBoolean(STATE_EDITING, false) ?: false
        // Decode old View anchors before the first composition, not after loading preferences.
        readDeepLink(intent)
        setScreenOrientation(currentOrientation)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
        enableFullScreen()
        setContent {
            MiuixEvoTheme {
                BackHandler(enabled = editing) { editing = false }
                if (editing && settingsReady && !shouldDisableSizeAdjustment) {
                    EditorWorkspace()
                } else {
                    SettingsPage()
                }
            }
        }
        lifecycleScope.launch {
            updateResizableRectangle()
            settingsStore.data.collect { preferences ->
                val nativeCode = preferences.readKey(KEY_FREEFORM_CODE, -1)
                val useMiWindow = preferences.readKey(KEY_USE_MI_WINDOW, false)
                selectedCode = if (useMiWindow) MODE_SUNSHINE else nativeCode
                followTriggerSide = preferences.readKey(KEY_FREEFORM_FOLLOW_TRIGGER_SIDE, false)
                // Sunshine is UI-only. A stale native code still participates in the original
                // disabled-state contract, even when the UI is currently showing Sunshine.
                shouldDisableSizeAdjustment = nativeCode == 106 || nativeCode == -2 || useMiWindow
                if (shouldDisableSizeAdjustment) editing = false
                settingsReady = true
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        editing = false
        readDeepLink(intent)
    }

    private fun readDeepLink(intent: Intent) {
        val anchors = buildList {
            intent.getIntExtra(SettingsSearchNavigator.EXTRA_TARGET_VIEW_ID, View.NO_ID)
                .takeIf { it != View.NO_ID }?.let(::add)
            intent.getIntArrayExtra(SettingsSearchNavigator.EXTRA_TARGET_VIEW_IDS)?.let {
                addAll(it.toList())
            }
        }
        fun sectionFor(anchor: Int): Int? = when (anchor) {
            R.id.directionCard, R.id.directionButtons,
            R.id.directionButtonPortrait, R.id.directionButtonLandscape -> R.id.directionCard
            R.id.dropdownCard, R.id.dropdownMenu, R.id.dropdownAutoComplete -> R.id.dropdownCard
            R.id.sizeCard, R.id.sizeInfoText, R.id.enableSizeChange, R.id.resetSizeButton,
            R.id.followTriggerSideRow, R.id.followTriggerSideSwitch,
            R.id.resizableRectangle -> R.id.sizeCard
            else -> null
        }
        val preferenceSection = when (
            intent.getStringExtra(SettingsSearchNavigator.EXTRA_TARGET_PREFERENCE_KEY)
        ) {
            KEY_ORIENTATION -> R.id.directionCard
            KEY_USE_MI_WINDOW, KEY_FREEFORM_CODE -> R.id.dropdownCard
            KEY_FREEFORM_FOLLOW_TRIGGER_SIDE, KEY_WIDTH, KEY_HEIGHT,
            KEY_LEFT_MARGIN, KEY_TOP_MARGIN, KEY_EDGE_MARGIN -> R.id.sizeCard
            else -> null
        }
        targetSections = anchors.mapNotNull(::sectionFor).toSet().ifEmpty {
            preferenceSection?.let { setOf(it) } ?: emptySet()
        }
        targetAnchor = anchors.firstOrNull { sectionFor(it) != null }
            ?: if (intent.getStringExtra(SettingsSearchNavigator.EXTRA_TARGET_PREFERENCE_KEY) ==
                KEY_FREEFORM_FOLLOW_TRIGGER_SIDE
            ) R.id.followTriggerSideRow else preferenceSection ?: View.NO_ID
    }

    private fun enableFullScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Force DecorView creation before asking PhoneWindow for its controller.
            // Some OEMs dereference an uncreated decor inside window.insetsController.
            window.decorView.windowInsetsController?.apply {
                hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enableFullScreen()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(KEY_ORIENTATION, currentOrientation)
        outState.putBoolean(STATE_EDITING, editing)
        super.onSaveInstanceState(outState)
    }

    private fun setScreenOrientation(orientation: Int) {
        requestedOrientation = if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    private fun selectOrientation(orientation: Int) {
        if (currentOrientation == orientation) return
        editing = false
        currentOrientation = orientation
        setScreenOrientation(orientation)
        lifecycleScope.launch { updateResizableRectangle() }
    }

    private fun selectMode(index: Int) {
        val code = modeCodes.getOrNull(index) ?: return
        selectedCode = code
        // Disable immediately, without waiting for the DataStore observer's next emission.
        shouldDisableSizeAdjustment = code == 106 || code == -2 || code == MODE_SUNSHINE
        if (shouldDisableSizeAdjustment) editing = false
        lifecycleScope.launch {
            settingsStore.edit { preferences ->
                preferences[booleanPreferencesKey(KEY_USE_MI_WINDOW)] = code == MODE_SUNSHINE
                if (code != MODE_SUNSHINE) {
                    preferences[intPreferencesKey(KEY_FREEFORM_CODE)] = code
                }
            }
        }
    }

    private suspend fun updateResizableRectangle() {
        val orientation = currentOrientation
        val left = getLeft(orientation, settingsStore).dp2px
        val top = getTop(orientation, settingsStore).dp2px
        val width = getWidth(orientation, settingsStore).dp2px
        val height = getHeight(orientation, settingsStore).dp2px
        if (orientation != currentOrientation) return
        rectangle = RectF(left, top, left + width, top + height)
        resizableRectangle?.setRectangle(rectangle)
    }

    private fun saveRectangle(rect: RectF, screenWidth: Int) {
        // The View reuses its mutable RectF. Snapshot geometry and orientation BEFORE launch.
        val snapshot = RectF(rect)
        val orientation = currentOrientation
        rectangle = snapshot
        val edgeMargin = minOf(
            snapshot.left, (screenWidth - snapshot.right).coerceAtLeast(0f),
        ).px2dp
        lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
            // A rotation/finish must not cancel an already accepted drag write. One atomic edit
            // also prevents consumers from observing a new width with an old position.
            withContext(NonCancellable) {
                settingsStore.edit { preferences ->
                    preferences[intPreferencesKey(getSettingsKey(KEY_WIDTH, orientation))] = snapshot.width().px2dp
                    preferences[intPreferencesKey(getSettingsKey(KEY_HEIGHT, orientation))] = snapshot.height().px2dp
                    preferences[intPreferencesKey(getSettingsKey(KEY_LEFT_MARGIN, orientation))] = snapshot.left.px2dp
                    preferences[intPreferencesKey(getSettingsKey(KEY_TOP_MARGIN, orientation))] = snapshot.top.px2dp
                    preferences[intPreferencesKey(getSettingsKey(KEY_EDGE_MARGIN, orientation))] = edgeMargin
                }
            }
        }
    }

    private fun resetSize() {
        val orientation = currentOrientation
        lifecycleScope.launch {
            settingsStore.edit { preferences ->
                listOf(KEY_WIDTH, KEY_HEIGHT, KEY_LEFT_MARGIN, KEY_TOP_MARGIN, KEY_EDGE_MARGIN)
                    .forEach { base ->
                        val key = getSettingsKey(base, orientation)
                        // Preserve reset compatibility with both historical numeric types.
                        preferences.remove(intPreferencesKey(key))
                        preferences.remove(floatPreferencesKey(key))
                    }
            }
            if (orientation == currentOrientation) {
                updateResizableRectangle()
                if (!shouldDisableSizeAdjustment) editing = true
            }
        }
    }

    @Composable
    private fun SettingsPage() {
        val labels = modeLabels
        MiuixScreen(
            title = stringResource(R.string.freeform_settings_title),
            onBack = { onBackPressedDispatcher.onBackPressed() },
        ) {
            if (targetSections.isEmpty() || R.id.directionCard in targetSections) {
                item(key = R.id.directionCard) {
                    MiuixCard(modifier = anchorModifier(R.id.directionCard)) {
                        SectionHeading(R.string.freeform_settings_direction_title,
                            R.string.freeform_settings_direction_description)
                        Row(
                            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OrientationButton(Configuration.ORIENTATION_PORTRAIT,
                                R.string.freeform_settings_direction_portrait, Modifier.weight(1f))
                            OrientationButton(Configuration.ORIENTATION_LANDSCAPE,
                                R.string.freeform_settings_direction_landscape, Modifier.weight(1f))
                        }
                    }
                }
            }
            if (targetSections.isEmpty() || R.id.dropdownCard in targetSections) {
                item(key = R.id.dropdownCard) {
                    MiuixCard(modifier = anchorModifier(R.id.dropdownCard)) {
                        OverlayDropdownPreference(
                            title = stringResource(R.string.freeform_settings_mode_title),
                            summary = stringResource(R.string.freeform_settings_mode_description),
                            items = labels,
                            selectedIndex = modeCodes.indexOf(selectedCode).coerceAtLeast(0),
                            onSelectedIndexChange = ::selectMode,
                        )
                    }
                }
            }
            if (targetSections.isEmpty() || R.id.sizeCard in targetSections) {
                item(key = R.id.sizeCard) {
                    MiuixCard(modifier = anchorModifier(R.id.sizeCard)) {
                        SectionHeading(R.string.freeform_settings_size_title,
                            R.string.freeform_settings_size_description)
                        SizeInfo(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        SwitchPreference(
                            title = stringResource(R.string.freeform_settings_size_enable),
                            summary = stringResource(R.string.freeform_settings_size_enable_desc),
                            checked = editing,
                            enabled = settingsReady && !shouldDisableSizeAdjustment,
                            onCheckedChange = { editing = it },
                        )
                        Column(modifier = anchorModifier(R.id.followTriggerSideRow)) {
                            SwitchPreference(
                                title = stringResource(R.string.freeform_settings_follow_trigger_side),
                                summary = stringResource(R.string.freeform_settings_follow_trigger_side_desc),
                                checked = followTriggerSide,
                                enabled = settingsReady && !shouldDisableSizeAdjustment,
                                onCheckedChange = { checked ->
                                    followTriggerSide = checked
                                    lifecycleScope.launch {
                                        settingsStore.saveKey(KEY_FREEFORM_FOLLOW_TRIGGER_SIDE, checked)
                                    }
                                },
                            )
                        }
                        ResetControl()
                    }
                }
            }
        }
    }

    @Composable
    private fun OrientationButton(value: Int, label: Int, modifier: Modifier) {
        TextButton(
            text = stringResource(label),
            modifier = modifier.heightIn(min = 44.dp),
            colors = if (currentOrientation == value) ButtonDefaults.textButtonColorsPrimary()
                else ButtonDefaults.textButtonColors(),
            onClick = { selectOrientation(value) },
        )
    }

    @Composable
    private fun SectionHeading(title: Int, description: Int) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(title), fontSize = 16.sp, lineHeight = 22.sp)
            Text(stringResource(description), fontSize = 12.sp, lineHeight = 16.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }

    @Composable
    private fun SizeInfo(modifier: Modifier = Modifier) {
        Text(
            text = if (settingsReady) stringResource(
                R.string.freeform_size_info, rectangle.width().toInt(), rectangle.height().toInt(),
                rectangle.left.toInt(), rectangle.top.toInt(),
            ) else stringResource(R.string.freeform_settings_size_info_default),
            modifier = modifier,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontFamily = FontFamily.Monospace,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun ResetControl() {
        val enabled = settingsReady && !shouldDisableSizeAdjustment
        // Keep the deliberately long-press-only reset; a normal tap cannot erase a layout.
        Box(
            Modifier.fillMaxWidth().padding(16.dp)
                .combinedClickable(enabled = enabled, onClick = {}, onLongClick = ::resetSize)
                .heightIn(min = 44.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.freeform_settings_size_reset), fontSize = 15.sp,
                lineHeight = 21.sp, color = if (enabled) MiuixTheme.colorScheme.primary
                    else MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun anchorModifier(anchor: Int): Modifier {
        val focus = androidx.compose.runtime.remember { FocusRequester() }
        val bringIntoView = androidx.compose.runtime.remember { BringIntoViewRequester() }
        val normalizedAnchor = when (targetAnchor) {
            R.id.followTriggerSideSwitch -> R.id.followTriggerSideRow
            R.id.directionButtons, R.id.directionButtonPortrait,
            R.id.directionButtonLandscape -> R.id.directionCard
            R.id.dropdownMenu, R.id.dropdownAutoComplete -> R.id.dropdownCard
            R.id.sizeInfoText, R.id.enableSizeChange, R.id.resetSizeButton,
            R.id.resizableRectangle -> R.id.sizeCard
            else -> targetAnchor
        }
        LaunchedEffect(normalizedAnchor, settingsReady) {
            if (normalizedAnchor == anchor && settingsReady) {
                focus.requestFocus()
                bringIntoView.bringIntoView()
            }
        }
        return Modifier.bringIntoViewRequester(bringIntoView).focusRequester(focus).focusable()
    }

    @Composable
    private fun EditorWorkspace() {
        // This Box is the entire edge-to-edge window, not a preview in a card or the space
        // below an app bar. AndroidView receives unscaled screen pixels with origin (0, 0).
        // Controls overlay the canvas; they must NEVER change the canvas's measured bounds.
        Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    ResizableRectangleView(context).also { view ->
                        resizableRectangle = view
                        view.setRectangle(rectangle)
                        view.setOnRectangleChangedListener(object : ResizableRectangleView.OnRectangleChangedListener {
                            override fun onRectangleChanged(rect: RectF) {
                                saveRectangle(rect, view.width)
                            }

                            override fun onHideButtonClicked() {
                                editing = false
                            }
                        })
                    }
                },
                update = { view ->
                    // No resize, translation, density conversion or aspect-ratio fitting here.
                    view.visibility = View.VISIBLE
                    view.setRectangle(rectangle)
                },
                onRelease = { view ->
                    if (resizableRectangle === view) resizableRectangle = null
                },
            )
            IconButton(
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                onClick = { editing = false },
            ) {
                Icon(painterResource(R.drawable.baseline_arrow_back_24),
                    contentDescription = stringResource(R.string.resizable_rect_hide))
            }
            // Read-only telemetry does not consume pointer events: bottom-edge handles remain
            // draggable through this overlay. The original center Hide button also exits editing.
            MiuixCard(Modifier.align(Alignment.BottomCenter).padding(12.dp)) {
                SizeInfo(Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            }
        }
    }
}
