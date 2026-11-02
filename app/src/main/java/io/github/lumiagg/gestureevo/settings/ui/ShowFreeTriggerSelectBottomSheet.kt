package io.github.lumiagg.gestureevo.settings.ui

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.gesture.config.FreeTriggerConfig
import io.github.lumiagg.gestureevo.gesture.config.FreeTriggerDataManager
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal data class FreeTriggerSelectOption(
    val trigger: FreeTriggerConfig,
    val isMirror: Boolean
) {
    val tag: String get() = trigger.tag
}

internal fun freeTriggerSelectionOptions(triggers: List<FreeTriggerConfig>): List<FreeTriggerSelectOption> =
    triggers.flatMap { trigger ->
        buildList {
            add(FreeTriggerSelectOption(trigger, false))
            if (trigger.getState(FreeTriggerConfig.FLAG_MIRROR)) {
                add(FreeTriggerSelectOption(trigger, true))
            }
        }
    }

@Suppress("UNUSED_PARAMETER")
fun showFreeTriggerSelectBottomSheet(
    activity: AppCompatActivity,
    coroutineScope: LifecycleCoroutineScope,
    title: String = activity.getString(R.string.external_call_trigger_select_title),
    subtitle: String = activity.getString(R.string.external_call_trigger_select_subtitle),
    currentSelectedTag: String,
    currentSelectedMirror: Boolean = false,
    onSelect: (String, Boolean) -> Unit
) {
    showMiuixSheet(activity) { dismiss ->
        var options by remember { mutableStateOf<List<FreeTriggerSelectOption>>(emptyList()) }
        LaunchedEffect(Unit) {
            val triggers = FreeTriggerDataManager.readTriggers(activity.applicationContext)
            if (triggers.isEmpty()) dismiss() else options = freeTriggerSelectionOptions(triggers)
        }
        Column(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, modifier = Modifier.padding(horizontal = 24.dp), fontSize = 17.sp,
                lineHeight = 22.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, modifier = Modifier.padding(horizontal = 24.dp), fontSize = 14.sp,
                lineHeight = 20.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 500.dp)) {
                items(options, key = { "${it.tag}:${it.isMirror}" }) { option ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .selectable(selected = option.tag == currentSelectedTag &&
                            option.isMirror == currentSelectedMirror, role = Role.RadioButton,
                            onClick = { onSelect(option.tag, option.isMirror); dismiss() })
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text(triggerDisplayName(activity, option.trigger, option.isMirror),
                            modifier = Modifier.weight(1f), fontSize = 16.sp, lineHeight = 22.sp)
                        RadioButton(selected = option.tag == currentSelectedTag &&
                            option.isMirror == currentSelectedMirror, onClick = null)
                    }
                }
            }
        }
    }
}

fun triggerDisplayName(
    activity: AppCompatActivity,
    trigger: FreeTriggerConfig,
    isMirror: Boolean
): String = if (isMirror) {
    activity.getString(R.string.action_view_trigger_mirror_name, trigger.name)
} else {
    trigger.name
}
