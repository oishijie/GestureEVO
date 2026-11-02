package io.github.lumiagg.gestureevo.settings.ui

import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.gesture.config.GestureActionType
import io.github.lumiagg.gestureevo.utils.GestureActionLink
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** One catalog for gesture, wheel and external-action editors. Null parameters require configuration. */
internal data class ActionChoice(
    val type: GestureActionType,
    val title: Int? = null,
    val parameters: Map<String, String>? = null
) {
    fun label(activity: AppCompatActivity): String = title?.let(activity::getString) ?: type.showName(activity)
}

internal fun showActionSelectBottomSheet(
    activity: AppCompatActivity,
    current: GestureActionLink,
    allowWheel: Boolean = true,
    allowNone: Boolean = true,
    allowClickThrough: Boolean = true,
    onSelected: (ActionChoice) -> Unit
) {
    val categories = actionCategories(Build.VERSION.SDK_INT, allowWheel, allowNone, allowClickThrough)
    showMiuixSheet(activity, dismissOnOutside = true) { dismiss ->
        var search by rememberSaveable { mutableStateOf("") }
        val query = search.trim()
        // Filter the restricted catalog, never the unrestricted set of action types.
        val visibleCategories = categories.mapNotNull { (category, choices) ->
            val visible = choices.filter { choice ->
                query.isBlank() || choice.label(activity).contains(query, true) ||
                    choice.type.name.contains(query, true) || activity.getString(category).contains(query, true)
            }
            if (visible.isEmpty()) null else category to visible
        }
        Text(
            text = stringResource(R.string.quick_setup_choose_action),
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp),
            fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium,
        )
        TextField(
            value = search,
            onValueChange = { search = it },
            label = stringResource(R.string.action_search),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 12.dp),
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 500.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (visibleCategories.isEmpty()) {
                item(key = "empty", contentType = "empty") {
                    Text(
                        text = stringResource(R.string.action_search_empty),
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        fontSize = 14.sp, lineHeight = 20.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            visibleCategories.forEach { (category, choices) ->
                item(key = "category:$category", contentType = "heading") {
                    Text(
                        text = stringResource(category),
                        modifier = Modifier.semantics { heading() }
                            .padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
                        fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
                items(
                    items = choices,
                    key = { "$category:${it.type.name}:${it.parameters}" },
                    contentType = { "action" },
                ) { choice ->
                    val selected = current.actionType == choice.type &&
                        (choice.parameters?.all { (key, value) ->
                            current.queryList.any { it.first == key && it.second == value }
                        } ?: true)
                    ActionChoiceRow(activity, choice, selected) {
                        // Close before opening the caller's follow-up configuration sheet.
                        dismiss()
                        onSelected(choice)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionChoiceRow(
    activity: AppCompatActivity,
    choice: ActionChoice,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = androidx.compose.animation.core.tween(150),
        label = "actionPress",
    )
    val haptic = LocalHapticFeedback.current
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected || focused || hovered) colors.secondaryContainer else colors.surface)
            .hoverable(interactionSource)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painter = painterResource(actionChoiceIcon(choice)),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = colors.primary,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(choice.label(activity), fontSize = 16.sp, lineHeight = 22.sp,
                fontWeight = FontWeight.Medium, color = colors.onSurface)
            Text(
                text = stringResource(if (choice.parameters == null) R.string.action_requires_config else R.string.action_ready),
                fontSize = 12.sp, lineHeight = 16.sp, color = colors.onSurfaceVariantSummary,
            )
        }
        RadioButton(selected = selected, onClick = null)
    }
}

private fun actionChoiceIcon(choice: ActionChoice): Int = when (choice.type) {
    GestureActionType.ACC_ACTION -> when (choice.parameters?.get("accAction")) {
        "1" -> R.drawable.baseline_arrow_back_24
        "2" -> R.drawable.baseline_home_24
        "4" -> R.drawable.baseline_circle_notifications_24
        "8" -> R.drawable.icon_lock
        "9" -> R.drawable.baseline_screenshot_24
        else -> R.drawable.baseline_menu_24
    }
    GestureActionType.OPEN_APP, GestureActionType.OPEN_ACTIVITY, GestureActionType.OTHER_SHORTCUT -> R.drawable.baseline_image_24
    GestureActionType.SEARCH, GestureActionType.SCREEN_SEARCH -> R.drawable.baseline_search_24
    GestureActionType.SCREEN_CONTENT, GestureActionType.SCREEN_CONTENT_FULLSCREEN -> R.drawable.ic_screen_content_fullscreen_text_24
    GestureActionType.KEYBOARD -> R.drawable.baseline_keyboard_24
    GestureActionType.PIN_POOL -> R.drawable.baseline_push_pin_24
    GestureActionType.OTHER_LINK -> R.drawable.ic_baseline_insert_link_24
    GestureActionType.CLIPBOARD_ACTION -> R.drawable.ic_baseline_content_copy_24
    GestureActionType.SHOW_ACTION_VIEW -> R.drawable.baseline_menu_24
    else -> R.drawable.baseline_gesture_24
}

/** SDK and entry-point restrictions are applied before search, so hidden actions cannot leak into results. */
internal fun actionCategories(
    sdk: Int,
    allowWheel: Boolean = true,
    allowNone: Boolean = true,
    allowClickThrough: Boolean = true
): List<Pair<Int, List<ActionChoice>>> {
    fun simple(type: GestureActionType) = ActionChoice(type, parameters = emptyMap())
    fun navigation(id: Int, title: Int) = ActionChoice(GestureActionType.ACC_ACTION, title, mapOf("accAction" to id.toString()))
    val system = mutableListOf(
        navigation(1, R.string.acc_action_select_back), navigation(2, R.string.acc_action_select_home),
        navigation(3, R.string.acc_action_select_recents), simple(GestureActionType.RECENT_APP),
        navigation(4, R.string.acc_action_select_notifications), navigation(5, R.string.acc_action_select_quick_settings),
        navigation(6, R.string.acc_action_select_power_dialog)
    )
    if (sdk >= 28) {
        system += navigation(9, R.string.acc_action_select_screenshot)
        system += navigation(8, R.string.acc_action_select_lock_screen)
    }
    return listOf(
        R.string.quick_setup_category_system to system,
        R.string.quick_setup_category_tools to listOf(
            simple(GestureActionType.SEARCH), simple(GestureActionType.SCREEN_SEARCH),
            simple(GestureActionType.SCREEN_CONTENT), simple(GestureActionType.SCREEN_CONTENT_FULLSCREEN),
            simple(GestureActionType.PIN_POOL), simple(GestureActionType.POINTER), simple(GestureActionType.KEYBOARD)
        ),
        R.string.quick_setup_category_apps to listOf(
            ActionChoice(GestureActionType.OPEN_APP), ActionChoice(GestureActionType.OPEN_ACTIVITY),
            ActionChoice(GestureActionType.OTHER_SHORTCUT), ActionChoice(GestureActionType.FREEZE_APP),
            simple(GestureActionType.FREEFORM_FOREGROUND)
        ),
        R.string.action_category_gestures to listOfNotNull(
            ActionChoice(GestureActionType.SHOW_ACTION_VIEW).takeIf { allowWheel },
            ActionChoice(GestureActionType.PAUSE_GESTURE),
            simple(GestureActionType.CLICK_THROUGH).takeIf { allowClickThrough }
        ),
        R.string.quick_setup_category_other to listOfNotNull(
            ActionChoice(GestureActionType.OTHER_LINK), ActionChoice(GestureActionType.OPEN_SHELL),
            ActionChoice(GestureActionType.CLIPBOARD_ACTION), ActionChoice(GestureActionType.TEMP_DND),
            simple(GestureActionType.NONE).takeIf { allowNone }
        )
    )
}

