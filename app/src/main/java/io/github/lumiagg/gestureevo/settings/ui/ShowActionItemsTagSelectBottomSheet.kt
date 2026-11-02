package io.github.lumiagg.gestureevo.settings.ui

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.compose.foundation.clickable
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
import io.github.lumiagg.gestureevo.gesture.config.ActionItemsTag
import io.github.lumiagg.gestureevo.gesture.config.GestureActionDataManager
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Suppress("UNUSED_PARAMETER")
fun showPieGroupSelectBottomSheet(
    activity: AppCompatActivity,
    coroutineScope: LifecycleCoroutineScope,
    title: String = activity.getString(R.string.action_items_tag_select_title),
    subtitle: String = activity.getString(R.string.action_items_tag_select_subtitle),
    currentSelectedTag: String,
    onSelect: (String) -> Unit
) {
    showMiuixSheet(activity) { dismiss ->
        var groups by remember { mutableStateOf<List<ActionItemsTag>>(emptyList()) }
        LaunchedEffect(Unit) {
            val loaded = GestureActionDataManager.readActionListTags(activity.applicationContext)
            if (loaded.isEmpty()) dismiss() else groups = loaded
        }
        Column(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, modifier = Modifier.padding(horizontal = 24.dp), fontSize = 17.sp,
                lineHeight = 22.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, modifier = Modifier.padding(horizontal = 24.dp), fontSize = 14.sp,
                lineHeight = 20.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 500.dp)) {
                items(groups, key = { it.tag }) { group ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .clickable { onSelect(group.tag); dismiss() }
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text(group.name, modifier = Modifier.weight(1f), fontSize = 16.sp, lineHeight = 22.sp)
                        RadioButton(selected = group.tag == currentSelectedTag, onClick = null)
                    }
                }
            }
        }
    }
}
