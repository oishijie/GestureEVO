package io.github.lumiagg.gestureevo.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.gesture.EventBusManager.produceFreeTriggerEvent
import io.github.lumiagg.gestureevo.gesture.FreeTriggerEvent
import io.github.lumiagg.gestureevo.gesture.config.FreeTriggerConfig
import io.github.lumiagg.gestureevo.gesture.config.FreeTriggerDataManager
import io.github.lumiagg.gestureevo.gesture.config.GestureActionDataManager
import io.github.lumiagg.gestureevo.gesture.config.GestureActionItem
import io.github.lumiagg.gestureevo.gesture.ui.GesturePhase
import io.github.lumiagg.gestureevo.gesture.ui.GestureType
import io.github.lumiagg.gestureevo.gesture.ui.GestureTypeEvent
import io.github.lumiagg.gestureevo.settings.ui.BlackListWithNoDBSelectAPPBottomSheet
import io.github.lumiagg.gestureevo.settings.ui.GestureAngleConfig
import io.github.lumiagg.gestureevo.settings.ui.GestureAngleConfigBottomSheet
import io.github.lumiagg.gestureevo.settings.ui.GestureAngleConfigCallback
import io.github.lumiagg.gestureevo.settings.ui.GestureAngleStorge
import io.github.lumiagg.gestureevo.settings.ui.GestureTimingConfigBottomSheet
import io.github.lumiagg.gestureevo.settings.ui.GestureTimingConfigCallback
import io.github.lumiagg.gestureevo.settings.ui.GestureTypeSelectBottomSheet
import io.github.lumiagg.gestureevo.settings.ui.GestureTypeSelectCallback
import io.github.lumiagg.gestureevo.settings.ui.OnQuadrantClick
import io.github.lumiagg.gestureevo.settings.ui.QuadrantCircleView
import io.github.lumiagg.gestureevo.ui.compose.MiuixEvoTheme
import io.github.lumiagg.gestureevo.ui.miuix.MiuixCard
import io.github.lumiagg.gestureevo.ui.miuix.MiuixScreen
import io.github.lumiagg.gestureevo.utils.DynamicColorUtils
import io.github.lumiagg.gestureevo.utils.ScreenUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

class FreeTriggerConfigActivity : AppCompatActivity() {
    private lateinit var trigger: FreeTriggerConfig
    private lateinit var actionSelectionLauncher: ActivityResultLauncher<Intent>
    private var pendingBatchEvents: List<GestureTypeEvent> = emptyList()
    private var deleted = false
    private val persistenceMutex = Mutex()
    private var name by mutableStateOf("")
    private var flags by mutableStateOf(0)
    private var xPosition by mutableStateOf(0f)
    private var yPosition by mutableStateOf(0f)
    private var quadrants by mutableStateOf<List<Boolean>>(emptyList())
    private var pendingWidth by mutableStateOf(0)
    private var pendingHeight by mutableStateOf(0)
    private var savedWidth = 0
    private var savedHeight = 0
    private var sizeAdjustMode by mutableStateOf(SizeAdjustMode.SMALL)
    private var sizeRiskReasons by mutableStateOf<List<String>>(emptyList())

    private enum class SizeAdjustMode(val gain: Int, val label: Int) {
        SMALL(1, R.string.free_trigger_size_mode_small),
        MEDIUM(2, R.string.free_trigger_size_mode_medium),
        LARGE(10, R.string.free_trigger_size_mode_large),
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DynamicColorUtils.captureColor(applicationContext, lifecycleScope)
        trigger = FreeTriggerConfig.toModel(
            savedInstanceState?.getString(STATE_TRIGGER) ?: intent.getStringExtra(EXTRA_TRIGGER_JSON) ?: ""
        ) ?: run {
            finish()
            return
        }
        name = trigger.name
        flags = trigger.state
        xPosition = trigger.xRelativeValue
        yPosition = trigger.yRelativeValue
        quadrants = trigger.getQuadrant().toList()
        savedWidth = trigger.width
        savedHeight = trigger.height
        pendingWidth = savedWidth
        pendingHeight = savedHeight
        sizeAdjustMode = resolveInitialSizeMode(savedWidth, savedHeight)
        pendingBatchEvents = savedInstanceState?.getStringArrayList(STATE_BATCH)?.mapNotNull { key ->
            // Restore the exact events offered by the selector without relying on enum ordinals.
            GestureType.entries.asSequence().flatMap { type ->
                GesturePhase.entries.asSequence().map { phase -> GestureTypeEvent(type, phase) }
            }.firstOrNull { it.toActionKey() == key }
        } ?: emptyList()
        registerSelectionLauncher()
        setContent {
            MiuixEvoTheme { TriggerConfigScreen() }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::trigger.isInitialized) {
            outState.putString(STATE_TRIGGER, trigger.toJson())
            outState.putStringArrayList(STATE_BATCH, ArrayList(pendingBatchEvents.map { it.toActionKey() }))
        }
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (!::trigger.isInitialized || deleted) return
        lifecycleScope.launch {
            produceFreeTriggerEvent(FreeTriggerEvent(isConfig = true, configTrigger = trigger))
        }
    }

    override fun onPause() {
        super.onPause()
        lifecycleScope.launch { produceFreeTriggerEvent(FreeTriggerEvent(isConfig = false)) }
    }

    @Composable
    private fun TriggerConfigScreen() {
        MiuixScreen(title = stringResource(R.string.free_trigger_item_edit_title), onBack = { finish() }) {
            item {
                MiuixCard {
                    SectionHeading(R.string.free_trigger_name_hint, R.string.free_trigger_name_description)
                    TextField(
                        value = name,
                        onValueChange = { name = it; saveTriggerConfig(true) },
                        label = stringResource(R.string.free_trigger_name_hint),
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
            }
            item {
                MiuixCard {
                    SectionHeading(R.string.free_trigger_wheel_behavior_title, R.string.free_trigger_wheel_behavior_desc)
                    SectionHeading(R.string.free_trigger_shape_title, R.string.free_trigger_shape_desc)
                    AndroidView(
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp).size(100.dp),
                        factory = { context ->
                            QuadrantCircleView(context).apply {
                                contentDescription = getString(R.string.free_trigger_shape_title)
                                setQuadrantState(quadrants)
                                onQuadrantClick = object : OnQuadrantClick {
                                    override fun onQuadrantClick(quadrantClickedList: ArrayList<Boolean>) {
                                        quadrants = quadrantClickedList.toList()
                                        saveTriggerConfig(true)
                                    }
                                }
                            }
                        },
                        update = { it.setQuadrantState(quadrants) },
                    )
                    FlagSwitch(R.string.free_trigger_mirror_title, FreeTriggerConfig.FLAG_MIRROR)
                    FlagSwitch(R.string.free_trigger_keyboard_layer_title, FreeTriggerConfig.FLAG_BELOW_KEYBOARD)
                    FlagSwitch(R.string.free_trigger_follow_touch_title, FreeTriggerConfig.FLAG_WHEEL_FOLLOW_TRIGGER_POINT)
                }
            }
            item {
                MiuixCard {
                    SectionHeading(R.string.free_trigger_scope_title, R.string.free_trigger_scope_desc)
                    SectionHeading(R.string.free_trigger_screen_orientation_title, R.string.free_trigger_screen_orientation_desc)
                    // Like the original multi-select ChipGroup, at least one orientation remains selected.
                    FlagSwitch(R.string.free_trigger_orientation_portrait, FreeTriggerConfig.FLAG_PORTRAIT,
                        otherRequiredFlag = FreeTriggerConfig.FLAG_LANDSCAPE)
                    FlagSwitch(R.string.free_trigger_orientation_landscape, FreeTriggerConfig.FLAG_LANDSCAPE,
                        otherRequiredFlag = FreeTriggerConfig.FLAG_PORTRAIT)
                    SectionHeading(R.string.free_trigger_app_filter_title, R.string.free_trigger_app_filter_desc)
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilterButton(R.string.free_trigger_blacklist, FreeTriggerConfig.FLAG_BLACKLIST, FreeTriggerConfig.FLAG_WHITELIST, Modifier.weight(1f))
                        FilterButton(R.string.free_trigger_whitelist, FreeTriggerConfig.FLAG_WHITELIST, FreeTriggerConfig.FLAG_BLACKLIST, Modifier.weight(1f))
                    }
                    ArrowPreference(title = stringResource(R.string.free_trigger_manage_list), onClick = {
                        if (hasFlag(FreeTriggerConfig.FLAG_BLACKLIST)) showBlacklistSelection() else showWhitelistSelection()
                    })
                }
            }
            item {
                MiuixCard {
                    SectionHeading(R.string.free_trigger_actions_title, R.string.free_trigger_actions_desc)
                    ArrowPreference(title = stringResource(R.string.free_trigger_set_action), onClick = ::showActionSelection)
                    ArrowPreference(title = stringResource(R.string.free_trigger_set_angle), onClick = ::showAngleConfig)
                    ArrowPreference(title = stringResource(R.string.free_trigger_set_time), onClick = ::showTimingConfig)
                }
            }
            item {
                MiuixCard {
                    SectionHeading(R.string.free_trigger_layout_title, R.string.free_trigger_layout_desc)
                    ConfigSlider(R.string.free_trigger_position_horizontal, String.format(Locale.US, "%.2f", xPosition),
                        xPosition, 0f..1f, { xPosition = it; saveTriggerConfig(true) }, { saveTriggerConfig(true) })
                    ConfigSlider(R.string.free_trigger_position_vertical, String.format(Locale.US, "%.2f", yPosition),
                        yPosition, 0f..1f, { yPosition = it; saveTriggerConfig(true) }, { saveTriggerConfig(true) })
                    SectionHeading(R.string.free_trigger_size_mode_title)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SizeAdjustMode.entries.forEach { mode ->
                            TextButton(
                                text = stringResource(mode.label),
                                modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                                colors = if (sizeAdjustMode == mode) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors(),
                                onClick = { sizeAdjustMode = mode },
                            )
                        }
                    }
                    Text(text = stringResource(R.string.free_trigger_size_mode_hint, sizeAdjustMode.gain),
                        modifier = Modifier.padding(16.dp), fontSize = 12.sp, lineHeight = 16.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    ConfigSlider(R.string.free_trigger_size_width, "$pendingWidth", actualToSliderValue(pendingWidth), 1f..100f,
                        { pendingWidth = sliderValueToActual(it) }, ::commitSizeChangeIfNeeded)
                    ConfigSlider(R.string.free_trigger_size_height, "$pendingHeight", actualToSliderValue(pendingHeight), 1f..100f,
                        { pendingHeight = sliderValueToActual(it) }, ::commitSizeChangeIfNeeded)
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    TextButton(text = stringResource(R.string.free_trigger_cancel), modifier = Modifier.weight(1f).heightIn(min = 44.dp), onClick = { finish() })
                    TextButton(text = stringResource(R.string.free_trigger_delete), modifier = Modifier.weight(1f).heightIn(min = 44.dp), onClick = ::deleteTrigger)
                }
            }
        }
        if (sizeRiskReasons.isNotEmpty()) {
            Dialog(onDismissRequest = ::restoreSavedSize) {
                MiuixCard {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.free_trigger_size_risk_title), fontSize = 17.sp, lineHeight = 22.sp)
                        Text(stringResource(R.string.free_trigger_size_risk_message, sizeRiskReasons.joinToString("、")),
                            fontSize = 14.sp, lineHeight = 20.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            TextButton(text = stringResource(android.R.string.cancel), modifier = Modifier.weight(1f).heightIn(min = 44.dp), onClick = ::restoreSavedSize)
                            TextButton(text = stringResource(R.string.free_trigger_size_risk_confirm), modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                                colors = ButtonDefaults.textButtonColorsPrimary(), onClick = { acceptPendingSize() })
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SectionHeading(title: Int, summary: Int? = null) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(title), fontSize = 16.sp, lineHeight = 22.sp)
            summary?.let { Text(stringResource(it), fontSize = 12.sp, lineHeight = 16.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
        }
    }

    private fun hasFlag(flag: Int) = flags and flag == flag

    @Composable
    private fun FlagSwitch(title: Int, flag: Int, otherRequiredFlag: Int? = null) {
        SwitchPreference(title = stringResource(title), checked = hasFlag(flag), onCheckedChange = { checked ->
            if (checked || otherRequiredFlag == null || hasFlag(otherRequiredFlag)) {
                flags = if (checked) flags or flag else flags and flag.inv()
                saveTriggerConfig(true)
            }
        })
    }

    @Composable
    private fun FilterButton(title: Int, flag: Int, other: Int, modifier: Modifier) {
        TextButton(text = stringResource(title), modifier = modifier.heightIn(min = 44.dp),
            colors = if (hasFlag(flag)) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors(),
            onClick = { flags = (flags or flag) and other.inv(); saveTriggerConfig(true) })
    }

    @Composable
    private fun ConfigSlider(title: Int, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>,
        onChange: (Float) -> Unit, onFinished: () -> Unit) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(title), fontSize = 16.sp, lineHeight = 22.sp)
                Text(valueText, fontSize = 14.sp, lineHeight = 20.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            Slider(
                value = value.coerceIn(range), valueRange = range,
                steps = if (range == 1f..100f) 98 else 0,
                onValueChange = onChange, onValueChangeFinished = onFinished,
            )
        }
    }

    private fun showActionSelection() {
        GestureTypeSelectBottomSheet(this, lifecycleScope, trigger, object : GestureTypeSelectCallback {
            override fun onGestureEventSelected(gestureTypeEvent: GestureTypeEvent) {
                pendingBatchEvents = emptyList()
                startActionSelection(gestureTypeEvent)
            }
            override fun onBatchGestureEventsSelected(events: List<GestureTypeEvent>) {
                if (events.isEmpty()) return
                pendingBatchEvents = events
                startActionSelection(events.first())
            }
        }).show()
    }

    private fun showAngleConfig() {
        GestureAngleConfigBottomSheet(this, lifecycleScope, trigger, object : GestureAngleConfigCallback {
            override fun onAngleConfigChanged(configs: List<GestureAngleConfig>) {
                val angleMap = mutableMapOf<GestureType, GestureAngleStorge>()
                configs.forEach { angleMap[it.gestureType] = GestureAngleStorge(s = it.startAngle, e = it.endAngle) }
                trigger.gestureAngleMap = angleMap
                saveTriggerConfig(true)
            }
        }).show()
    }

    private fun showTimingConfig() {
        GestureTimingConfigBottomSheet(this, trigger, object : GestureTimingConfigCallback {
            override fun onTimingConfigChanged() { saveTriggerConfig(true) }
        }).show()
    }

    private fun resolveInitialSizeMode(width: Int, height: Int) = when {
        maxOf(width, height) <= 100 -> SizeAdjustMode.SMALL
        maxOf(width, height) <= 200 -> SizeAdjustMode.MEDIUM
        else -> SizeAdjustMode.LARGE
    }

    private fun sliderValueToActual(value: Float) = value.roundToInt().coerceIn(1, 100) * sizeAdjustMode.gain
    private fun actualToSliderValue(value: Int) = ceil(value / sizeAdjustMode.gain.toFloat()).toInt().coerceIn(1, 100).toFloat()

    private fun commitSizeChangeIfNeeded() {
        if (deleted || sizeRiskReasons.isNotEmpty()) return
        if (pendingWidth == savedWidth && pendingHeight == savedHeight) return
        val reasons = getSizeRiskReasons(pendingWidth, pendingHeight)
        if (reasons.isEmpty()) acceptPendingSize() else sizeRiskReasons = reasons
    }

    private fun acceptPendingSize() {
        savedWidth = pendingWidth
        savedHeight = pendingHeight
        sizeRiskReasons = emptyList()
        saveTriggerConfig(true)
    }

    private fun restoreSavedSize() {
        pendingWidth = savedWidth
        pendingHeight = savedHeight
        sizeRiskReasons = emptyList()
    }

    private fun getSizeRiskReasons(widthDp: Int, heightDp: Int): List<String> {
        val screenWidthPx = ScreenUtil.getScreenRealWidth(this, false)
        val screenHeightPx = ScreenUtil.getScreenRealHeight(this, false)
        val density = resources.displayMetrics.density
        val widthPx = (widthDp * density).toInt()
        val heightPx = (heightDp * density).toInt()
        val reasons = mutableListOf<String>()
        if (widthPx > minOf((screenWidthPx * 0.35f).toInt(), (180 * density).toInt())) reasons.add(getString(R.string.free_trigger_size_risk_reason_width))
        if (heightPx > minOf((screenHeightPx * 0.55f).toInt(), (320 * density).toInt())) reasons.add(getString(R.string.free_trigger_size_risk_reason_height))
        if (widthPx.toLong() * heightPx > screenWidthPx.toLong() * screenHeightPx * 0.15f) reasons.add(getString(R.string.free_trigger_size_risk_reason_area))
        return reasons
    }

    private fun showBlacklistSelection() {
        BlackListWithNoDBSelectAPPBottomSheet(this, lifecycleScope,
            blackListSelectAPPCallBack = object : BlackListWithNoDBSelectAPPBottomSheet.BlackListSelectAPPCallBack {
                override fun selectCompleted(blackList: List<String>) { trigger.blackList = blackList; saveTriggerConfig(true) }
            }, blackList = ArrayList(trigger.blackList)).show()
    }

    private fun showWhitelistSelection() {
        BlackListWithNoDBSelectAPPBottomSheet(this, lifecycleScope,
            blackListSelectAPPCallBack = object : BlackListWithNoDBSelectAPPBottomSheet.BlackListSelectAPPCallBack {
                override fun selectCompleted(blackList: List<String>) { trigger.whiteList = blackList; saveTriggerConfig(true) }
            }, blackList = ArrayList(trigger.whiteList), disableIcon = ContextCompat.getDrawable(this, R.drawable.baseline_check_24)).show()
    }

    private fun saveTriggerConfig(onlyShowThisView: Boolean) {
        if (deleted) return
        trigger.name = name
        trigger.state = flags
        trigger.xRelativeValue = xPosition
        trigger.yRelativeValue = yPosition
        // Never let an unrelated immediate save commit an unconfirmed size edit.
        trigger.width = savedWidth
        trigger.height = savedHeight
        trigger.setQuadrant(quadrants)
        val snapshot = FreeTriggerConfig.toModel(trigger.toJson()) ?: return
        lifecycleScope.launch {
            persistenceMutex.withLock {
                if (deleted) return@withLock
                FreeTriggerDataManager.updateTrigger(this@FreeTriggerConfigActivity, snapshot)
                produceFreeTriggerEvent(FreeTriggerEvent(isConfig = true, configTrigger = if (onlyShowThisView) snapshot else null))
                setResult(RESULT_OK)
            }
        }
    }

    private fun deleteTrigger() {
        if (deleted) return
        deleted = true
        lifecycleScope.launch {
            persistenceMutex.withLock {
                FreeTriggerDataManager.removeTrigger(this@FreeTriggerConfigActivity, trigger.tag)
                produceFreeTriggerEvent(FreeTriggerEvent(isConfig = true))
                setResult(RESULT_OK)
                finish()
            }
        }
    }

    private fun startActionSelection(gestureTypeEvent: GestureTypeEvent) {
        actionSelectionLauncher.launch(Intent(this, GestureAddActionActivity::class.java).apply {
            putExtra("triggerActionMode", true)
            putExtra("tag", trigger.tag)
            putExtra("expectItemTag", gestureTypeEvent.toActionKey())
            trigger.getGestureAction(gestureTypeEvent.toActionKey())?.let {
                putExtra("editMode", true)
                putExtra("item", it.toJson())
            }
        })
    }

    private fun registerSelectionLauncher() {
        actionSelectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                lifecycleScope.launch {
                    trigger.updateGestureActionList(this@FreeTriggerConfigActivity)
                    if (pendingBatchEvents.isNotEmpty()) applyBatchSelection()
                    pendingBatchEvents = emptyList()
                    showActionSelection()
                }
            } else pendingBatchEvents = emptyList()
        }
    }

    private suspend fun applyBatchSelection() {
        val uniqueEvents = pendingBatchEvents.distinctBy { it.toActionKey() }
        val primaryEvent = uniqueEvents.firstOrNull() ?: return
        val templateJson = trigger.getGestureAction(primaryEvent.toActionKey())?.toJson() ?: return
        withContext(Dispatchers.IO) {
            uniqueEvents.drop(1).forEach { event ->
                val copy = GestureActionItem.toModel(templateJson) ?: return@forEach
                copy.tag = event.toActionKey()
                GestureActionDataManager.addActionItem(this@FreeTriggerConfigActivity, trigger.tag, copy)
            }
        }
        trigger.updateGestureActionList(this)
    }

    companion object {
        private const val EXTRA_TRIGGER_JSON = "extra_trigger_json"
        private const val STATE_TRIGGER = "free_trigger_config"
        private const val STATE_BATCH = "free_trigger_batch"

        fun createIntent(context: Context, trigger: FreeTriggerConfig): Intent =
            Intent(context, FreeTriggerConfigActivity::class.java).apply { putExtra(EXTRA_TRIGGER_JSON, trigger.toJson()) }
    }
}
