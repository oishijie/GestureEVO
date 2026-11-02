package io.github.lumiagg.gestureevo.settings.ui

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleCoroutineScope
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.gesture.config.FreeTriggerConfig
import io.github.lumiagg.gestureevo.gesture.config.GestureActionType
import io.github.lumiagg.gestureevo.gesture.ui.GesturePhase
import io.github.lumiagg.gestureevo.gesture.ui.GestureType
import io.github.lumiagg.gestureevo.gesture.ui.GestureTypeEvent
import io.github.lumiagg.gestureevo.ui.miuix.MiuixCard
import io.github.lumiagg.gestureevo.utils.GestureActionLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class GestureTypeHeader(val title: String)

data class GestureTypeModel(
    val gestureType: GestureType,
    val displayName: String,
    val description: String = "",
    val phaseActions: Map<GesturePhase, String> = emptyMap()
)

interface GestureTypeSelectCallback {
    fun onGestureEventSelected(gestureTypeEvent: GestureTypeEvent)

    fun onBatchGestureEventsSelected(events: List<GestureTypeEvent>) {}
}

class GestureTypeSelectBottomSheet(
    private val activity: Activity,
    private val coroutineScope: LifecycleCoroutineScope,
    private val triggerConfig: FreeTriggerConfig,
    private val callback: GestureTypeSelectCallback
) {
    // UI/callback order deliberately differs from GesturePhase.entries (TD, RD, SP, BACK).
    private val phaseOrder = listOf(GesturePhase.TD, GesturePhase.SP, GesturePhase.RD, GesturePhase.BACK)

    fun show() {
        showMiuixSheet(activity, dismissOnOutside = false) { dismiss ->
            var gestureList by remember { mutableStateOf<List<Any>>(emptyList()) }
            var isBatchMode by remember { mutableStateOf(false) }
            var selectedGestureTypes by remember { mutableStateOf<Set<GestureType>>(emptySet()) }
            var selectedPhases by remember { mutableStateOf(setOf(GesturePhase.TD)) }
            // Loading belongs to this composition: closing the sheet cannot update a dead View.
            LaunchedEffect(Unit) {
                gestureList = withContext(Dispatchers.Default) { getAvailableGestureTypes() }
            }
            val gestureModels = gestureList.filterIsInstance<GestureTypeModel>()
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.gesture_type_select_for_corner, triggerConfig.name),
                        modifier = Modifier.weight(1f),
                        fontSize = 22.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    TextButton(
                        text = stringResource(if (isBatchMode) R.string.gesture_type_batch_done else R.string.gesture_type_select_mode_batch),
                        modifier = Modifier.heightIn(min = 44.dp),
                        onClick = {
                            isBatchMode = !isBatchMode
                            // Leaving batch mode clears gestures, but retains the chosen phase set.
                            if (!isBatchMode) selectedGestureTypes = emptySet()
                        },
                    )
                }
                // The only scroll container is lazy and bounded; footer actions never scroll away.
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 500.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "instructions") {
                        Text(
                            text = stringResource(if (isBatchMode) R.string.gesture_type_batch_hint else R.string.gesture_type_select_note),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    if (isBatchMode) {
                        item(key = "phases") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                phaseOrder.chunked(2).forEach { phases ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        phases.forEach { phase ->
                                            val selected = phase in selectedPhases
                                            TextButton(
                                                text = phase.showName(activity),
                                                modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                                                colors = if (selected) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors(),
                                                onClick = {
                                                    // Like the original chips, zero selected phases is valid.
                                                    selectedPhases = if (selected) selectedPhases - phase else selectedPhases + phase
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    items(
                        items = gestureList,
                        key = { model ->
                            when (model) {
                                is GestureTypeHeader -> "header_${model.title}"
                                is GestureTypeModel -> model.gestureType.name
                                else -> error("Unknown gesture row")
                            }
                        },
                        contentType = { if (it is GestureTypeHeader) "header" else "gesture" },
                    ) { model ->
                        when (model) {
                            is GestureTypeHeader -> Text(
                                text = model.title,
                                modifier = Modifier.padding(top = 4.dp),
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            is GestureTypeModel -> MiuixCard {
                                if (isBatchMode) {
                                    SwitchPreference(
                                        title = model.displayName,
                                        checked = model.gestureType in selectedGestureTypes,
                                        onCheckedChange = { checked ->
                                            selectedGestureTypes = if (checked) selectedGestureTypes + model.gestureType else selectedGestureTypes - model.gestureType
                                        },
                                    )
                                } else {
                                    GestureItem(model, dismiss)
                                }
                            }
                        }
                    }
                }
                if (isBatchMode) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.gesture_type_batch_selected_count, selectedGestureTypes.size, selectedPhases.size),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            TextButton(
                                text = stringResource(R.string.gesture_type_batch_select_all),
                                modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                                onClick = { selectedGestureTypes = gestureModels.map { it.gestureType }.toSet() },
                            )
                            TextButton(
                                text = stringResource(R.string.gesture_type_batch_clear),
                                modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                                onClick = { selectedGestureTypes = emptySet() },
                            )
                        }
                        TextButton(
                            text = stringResource(R.string.gesture_type_batch_apply),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            enabled = selectedGestureTypes.isNotEmpty() && selectedPhases.isNotEmpty(),
                            onClick = {
                                val events = buildBatchEvents(gestureModels, selectedGestureTypes, selectedPhases)
                                if (events.isNotEmpty()) {
                                    callback.onBatchGestureEventsSelected(events)
                                    dismiss()
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun GestureItem(model: GestureTypeModel, dismiss: () -> Unit) {
        val phases = when (model.gestureType) {
            GestureType.SINGLE_CLICK, GestureType.DOUBLE_CLICK -> listOf(GesturePhase.TD)
            GestureType.LONG_PRESS -> listOf(GesturePhase.TD, GesturePhase.RD)
            else -> phaseOrder
        }
        // Preserve the original action-name comparison and empty-link default action.
        val allowFollowUp = model.phaseActions[GesturePhase.TD] == GestureActionType.NONE.showName(activity)
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(model.displayName, fontSize = 16.sp, lineHeight = 22.sp,
                fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.onSurface)
            if (phases.size > 1 && !allowFollowUp) {
                Text(stringResource(R.string.gesture_type_phase_locked), fontSize = 12.sp,
                    lineHeight = 16.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        }
        phases.forEach { phase ->
            val enabled = phase == GesturePhase.TD || allowFollowUp
            ArrowPreference(
                title = phase.showName(activity),
                summary = model.phaseActions[phase] ?: stringResource(R.string.gesture_phase_unset),
                enabled = enabled,
                onClick = {
                    if (enabled) {
                        callback.onGestureEventSelected(GestureTypeEvent(model.gestureType, phase))
                        dismiss()
                    }
                },
            )
        }
    }

    private fun getActionKey(gestureType: GestureType, phase: GesturePhase): String =
        "trigger_${gestureType.name}_${phase.name}_action"

    private fun getActionNameFromLink(actionLink: String): String =
        if (actionLink.isNotEmpty()) {
            GestureActionLink().apply { parseLink(actionLink) }.actionType.showName(activity)
        } else {
            GestureActionType.SHOW_ACTION_VIEW.showName(activity)
        }

    private suspend fun getAvailableGestureTypes(): List<Any> {
        // Preserve the six supported directions, their UI order, and exclusion of TOUCH.
        val tap = listOf(
            createGestureModel(GestureType.SINGLE_CLICK, activity.getString(R.string.gesture_type_single_click)),
            createGestureModel(GestureType.DOUBLE_CLICK, activity.getString(R.string.gesture_type_double_click)),
            createGestureModel(GestureType.LONG_PRESS, activity.getString(R.string.gesture_type_long_press)),
        )
        val swipe = listOf(
            createGestureModel(GestureType.SWIPE_LEFT, activity.getString(R.string.gesture_type_swipe_left)),
            createGestureModel(GestureType.SWIPE_RIGHT, activity.getString(R.string.gesture_type_swipe_right)),
            createGestureModel(GestureType.SWIPE_UP, activity.getString(R.string.gesture_type_swipe_up)),
            createGestureModel(GestureType.SWIPE_DOWN, activity.getString(R.string.gesture_type_swipe_down)),
            createGestureModel(GestureType.SWIPE_OBLIQUE_UP, activity.getString(R.string.gesture_type_swipe_oblique_up)),
            createGestureModel(GestureType.SWIPE_OBLIQUE_DOWN, activity.getString(R.string.gesture_type_swipe_oblique_down)),
        )
        return buildList {
            add(GestureTypeHeader(activity.getString(R.string.gesture_type_group_tap)))
            addAll(tap)
            add(GestureTypeHeader(activity.getString(R.string.gesture_type_group_swipe)))
            addAll(swipe)
        }
    }

    private suspend fun createGestureModel(gestureType: GestureType, displayName: String): GestureTypeModel {
        val phaseActions = GesturePhase.entries.associateWith { phase ->
            val actionLink = triggerConfig.getGestureAction(getActionKey(gestureType, phase))?.firstStageLink ?: ""
            getActionNameFromLink(actionLink)
        }
        return GestureTypeModel(
            gestureType = gestureType,
            displayName = displayName,
            description = activity.getString(R.string.gesture_type_select_description),
            phaseActions = phaseActions,
        )
    }

    private fun buildBatchEvents(
        gestureModels: List<GestureTypeModel>,
        selectedGestureTypes: Set<GestureType>,
        selectedPhases: Set<GesturePhase>,
    ): List<GestureTypeEvent> = buildList {
        // Batch intentionally permits all selected phases for every selected gesture,
        // including click SP/BACK and follow-ups locked in single-item mode.
        gestureModels.filter { it.gestureType in selectedGestureTypes }.forEach { model ->
            phaseOrder.filter { it in selectedPhases }.forEach { phase ->
                add(GestureTypeEvent(model.gestureType, phase))
            }
        }
    }
}
