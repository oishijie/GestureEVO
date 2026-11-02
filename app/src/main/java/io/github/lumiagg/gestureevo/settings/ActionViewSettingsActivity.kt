package io.github.lumiagg.gestureevo.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.db.APPModel
import io.github.lumiagg.gestureevo.gesture.ActionViewEvent
import io.github.lumiagg.gestureevo.gesture.EventBusManager
import io.github.lumiagg.gestureevo.gesture.config.ActionConfig
import io.github.lumiagg.gestureevo.gesture.config.ActionItemsTag
import io.github.lumiagg.gestureevo.gesture.config.FreeTriggerConfig
import io.github.lumiagg.gestureevo.gesture.config.FreeTriggerDataManager
import io.github.lumiagg.gestureevo.gesture.config.GestureActionType
import io.github.lumiagg.gestureevo.gesture.config.GestureActionDataManager
import io.github.lumiagg.gestureevo.gesture.config.GestureActionDataManager.buildTag
import io.github.lumiagg.gestureevo.gesture.ui.ActionView
import io.github.lumiagg.gestureevo.settings.ui.BlackListSelectAPPBottomSheet
import io.github.lumiagg.gestureevo.settings.ui.SelectAPPCallBack
import io.github.lumiagg.gestureevo.settings.ui.showFreeTriggerSelectBottomSheet
import io.github.lumiagg.gestureevo.settings.ui.showActionConfigBottomSheet
import io.github.lumiagg.gestureevo.settings.ui.showEditTextBottomSheet
import io.github.lumiagg.gestureevo.settings.ui.showPieGroupSelectBottomSheet
import io.github.lumiagg.gestureevo.settings.ui.triggerDisplayName
import io.github.lumiagg.gestureevo.ui.compose.MiuixEvoTheme
import io.github.lumiagg.gestureevo.ui.miuix.MiuixCard
import io.github.lumiagg.gestureevo.ui.miuix.MiuixScreen
import io.github.lumiagg.gestureevo.utils.ClipboardUtils
import io.github.lumiagg.gestureevo.utils.ToastHelper
import io.github.lumiagg.gestureevo.utils.uriBuilder
import io.github.lumiagg.gestureevo.utils.app.syncAppCatalog
import io.github.lumiagg.gestureevo.utils.readKey
import io.github.lumiagg.gestureevo.utils.saveKey
import io.github.lumiagg.gestureevo.utils.settingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** The settings surface is Compose; runtime wheel editors remain the shared overlay workflow. */
class ActionViewSettingsActivity : AppCompatActivity() {
    private var itemListTag by mutableStateOf(ActionConfig.DEFAULT_TAG)
    private val pieGroups = mutableStateListOf<ActionItemsTag>()
    private var previewTriggerTag = ""
    private var previewMirror = false
    private var externalCallActionItemsTag = ""
    private var externalCallTriggerTag = ""
    private var externalCallMirror = false
    private var previewTriggerName by mutableStateOf("")
    private var externalCallSummary by mutableStateOf("")
    private var externalCallLink by mutableStateOf("")
    private var showNotice by mutableStateOf(false)
    private var ready by mutableStateOf(false)
    private var showAnim by mutableStateOf(true)
    private var showVibrate by mutableStateOf(true)
    private var disableAllVibrate by mutableStateOf(false)
    private var selectedHint by mutableStateOf(true)
    private var focusedSections by mutableStateOf<Set<Int>>(emptySet())
    private var externalFocus by mutableStateOf(false)

    companion object {
        const val KEY_MAIN_SHOW_ANIM = "key_main_show_anim"
        const val KEY_MAIN_SHOW_VIBRATE = "key_main_show_vibrate"
        const val KEY_DISABLE_ALL_WHEEL_VIBRATE = "key_disable_all_wheel_vibrate"
        const val KEY_SELECTED_HINT_ICON = "key_selected_hint_icon"
        const val KEY_PREVIEW_TRIGGER_TAG = "key_preview_trigger_tag"
        const val KEY_PREVIEW_MIRROR = "key_preview_mirror"
        const val KEY_EXTERNAL_CALL_MIRROR = "key_external_call_mirror"
        const val KEY_HIDE_ACTION_VIEW_NOTICE = "key_hide_action_view_notice"
        const val EXTRA_SCROLL_TO_EXTERNAL_CALL = "extra_scroll_to_external_call"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readFocusIntent(intent) // Filter before the first composition, not after the database read.
        setContent { MiuixEvoTheme { SettingsContent() } }
        lifecycleScope.launch {
            previewTriggerTag = settingsStore.readKey(KEY_PREVIEW_TRIGGER_TAG, "")
            previewMirror = settingsStore.readKey(KEY_PREVIEW_MIRROR, false)
            externalCallMirror = settingsStore.readKey(KEY_EXTERNAL_CALL_MIRROR, false)
            showAnim = settingsStore.readKey(KEY_MAIN_SHOW_ANIM, true)
            showVibrate = settingsStore.readKey(KEY_MAIN_SHOW_VIBRATE, true)
            disableAllVibrate = settingsStore.readKey(KEY_DISABLE_ALL_WHEEL_VIBRATE, false)
            selectedHint = settingsStore.readKey(KEY_SELECTED_HINT_ICON, true)
            showNotice = !settingsStore.readKey(KEY_HIDE_ACTION_VIEW_NOTICE, false)
            setupPieGroupTabs()
            refreshPreviewTriggerSummary()
            refreshExternalCallSummary()
            ready = true
        }
        lifecycleScope.launch(Dispatchers.IO) { syncAppCatalog(applicationContext) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readFocusIntent(intent)
    }

    // Normalize both section IDs and historical XML row IDs to their Compose section.
    private fun readFocusIntent(source: Intent) {
        val ids = buildList {
            source.getIntExtra(SettingsSearchNavigator.EXTRA_TARGET_VIEW_ID, View.NO_ID)
                .takeIf { it != View.NO_ID }?.let(::add)
            source.getIntArrayExtra(SettingsSearchNavigator.EXTRA_TARGET_VIEW_IDS)?.let { addAll(it.toList()) }
        }
        focusedSections = ids.mapNotNull { id ->
            when (id) {
                R.id.actionItemListTagsCard, R.id.selectPieGroupButton, R.id.currentPieGroupName,
                R.id.addPieGroupButton, R.id.managePieGroupButton -> R.id.actionItemListTagsCard
                R.id.previewTriggerCard, R.id.previewTriggerButton, R.id.previewTriggerName,
                R.id.previewPieButton -> R.id.previewTriggerCard
                R.id.primaryPieCard, R.id.pieContentConfigEntry, R.id.pieStyleConfigEntry,
                R.id.pieSubStyleConfigEntry, R.id.pieRecentBlackListButton -> R.id.primaryPieCard
                R.id.miscCard, R.id.mainViewShowAnimSwitch, R.id.mainViewShowVibrateSwitch,
                R.id.disableAllWheelVibrateSwitch, R.id.selectedHintIconSwitch -> R.id.miscCard
                R.id.externalCallCard, R.id.externalCallConfigButton, R.id.externalCallCopyButton,
                R.id.externalCallSummary, R.id.externalCallLink, R.id.externalCallLinkTitle -> R.id.externalCallCard
                else -> null
            }
        }.toSet()
        externalFocus = focusedSections.isEmpty() && source.getBooleanExtra(EXTRA_SCROLL_TO_EXTERNAL_CALL, false)
    }

    @Composable
    private fun SettingsContent() {
        val listState = rememberLazyListState()
        val currentName = pieGroups.find { it.tag == itemListTag }?.name
        val visibleSections = listOf(R.id.actionItemListTagsCard, R.id.previewTriggerCard,
            R.id.primaryPieCard, R.id.miscCard, R.id.externalCallCard)
            .filter { focusedSections.isEmpty() || it in focusedSections }
        val noticeVisible = showNotice && focusedSections.isEmpty()
        LaunchedEffect(ready, externalFocus, noticeVisible, focusedSections) {
            if (ready && externalFocus) {
                val index = visibleSections.indexOf(R.id.externalCallCard)
                if (index >= 0) listState.animateScrollToItem(index + if (noticeVisible) 1 else 0)
            }
        }
        MiuixScreen(
            title = currentName?.let { getString(R.string.action_view_settings_title_with_group, it) }
                ?: getString(R.string.action_view_settings_title),
            onBack = { finish() },
            actions = {
                IconButton(onClick = { showCurrentPieGroupSelector() }) {
                    Icon(painter = painterResource(R.drawable.baseline_pie_chart_24),
                        contentDescription = getString(R.string.action_view_settings_current_group_title))
                }
            },
            listState = listState,
        ) {
            if (noticeVisible) item(key = "notice") {
                MiuixCard {
                    Text(getString(R.string.action_view_settings_debug_notice),
                        modifier = Modifier.padding(16.dp), fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    ArrowPreference(title = getString(R.string.action_view_settings_notice_close), onClick = {
                        showNotice = false
                        lifecycleScope.launch { settingsStore.saveKey(KEY_HIDE_ACTION_VIEW_NOTICE, true) }
                    })
                }
            }
            visibleSections.forEach { section ->
                item(key = section) {
                    val focusRequester = remember { FocusRequester() }
                    val isTarget = focusedSections.firstOrNull() == section ||
                        (externalFocus && section == R.id.externalCallCard)
                    var settled by remember(isTarget) { mutableStateOf(!isTarget) }
                    val highlightAlpha by animateFloatAsState(
                        targetValue = if (settled) 1f else 0.68f,
                        animationSpec = tween(300), label = "section-focus")
                    LaunchedEffect(ready, isTarget) {
                        if (ready && isTarget) {
                            focusRequester.requestFocus()
                            settled = true
                        }
                    }
                    MiuixCard(modifier = Modifier.alpha(highlightAlpha)
                        .focusRequester(focusRequester).focusable()) {
                        when (section) {
                            R.id.actionItemListTagsCard -> {
                                SectionHeader(R.string.action_view_settings_groups_title, R.string.action_view_settings_groups_description)
                                ArrowPreference(title = getString(R.string.action_view_settings_current_group_title),
                                    summary = currentName ?: getString(R.string.action_view_settings_current_group_not_selected),
                                    enabled = ready && pieGroups.isNotEmpty(), onClick = { showCurrentPieGroupSelector() })
                                ArrowPreference(title = getString(R.string.action_view_settings_add_group),
                                    enabled = ready, onClick = { showAddPieGroupDialog() })
                                ArrowPreference(title = getString(R.string.action_view_settings_manage),
                                    enabled = ready && pieGroups.isNotEmpty(), onClick = {
                                        pieGroups.find { it.tag == itemListTag }?.let(::showManagePieGroupDialog)
                                    })
                            }
                            R.id.previewTriggerCard -> {
                                SectionHeader(R.string.action_view_settings_preview_title, R.string.action_view_settings_preview_summary)
                                ArrowPreference(title = getString(R.string.action_view_preview_trigger_title),
                                    summary = previewTriggerName, enabled = ready, onClick = { selectPreviewTrigger() })
                                ArrowPreference(title = getString(R.string.action_view_settings_preview_button),
                                    enabled = ready, onClick = { previewWheel() })
                            }
                            R.id.primaryPieCard -> {
                                SectionHeader(R.string.action_view_settings_primary_title, R.string.action_view_settings_primary_description)
                                ArrowPreference(title = getString(R.string.action_view_settings_content_entry_title),
                                    enabled = ready, onClick = { checkAccessibility(); showConfiguration(false) })
                                ArrowPreference(title = getString(R.string.action_view_settings_main_style_entry_title),
                                    enabled = ready, onClick = { editStyle(false) })
                                ArrowPreference(title = getString(R.string.action_view_settings_sub_style_entry_title),
                                    enabled = ready, onClick = { editStyle(true) })
                                ArrowPreference(title = getString(R.string.action_view_settings_blacklist_manage),
                                    enabled = ready, onClick = {
                                        BlackListSelectAPPBottomSheet(this@ActionViewSettingsActivity, lifecycleScope,
                                            object : SelectAPPCallBack { override fun selectCompleted(model: APPModel) {} },
                                            blackListFlag = APPModel.FLAG_RECENT_APPS_DISABLE).show()
                                    })
                            }
                            R.id.miscCard -> {
                                SectionHeader(R.string.action_view_settings_misc_title, R.string.action_view_settings_misc_description)
                                SwitchPreference(title = getString(R.string.action_view_settings_selected_hint),
                                    summary = getString(R.string.action_view_settings_selected_hint_desc),
                                    checked = selectedHint, enabled = ready, onCheckedChange = { selectedHint = it; saveSwitch(KEY_SELECTED_HINT_ICON, it) })
                                SwitchPreference(title = getString(R.string.action_view_settings_show_anim),
                                    summary = getString(R.string.action_view_settings_show_anim_desc),
                                    checked = showAnim, enabled = ready, onCheckedChange = { showAnim = it; saveSwitch(KEY_MAIN_SHOW_ANIM, it) })
                                SwitchPreference(title = getString(R.string.action_view_settings_show_vibrate),
                                    summary = getString(R.string.action_view_settings_show_vibrate_desc),
                                    checked = showVibrate, enabled = ready, onCheckedChange = { showVibrate = it; saveSwitch(KEY_MAIN_SHOW_VIBRATE, it) })
                                SwitchPreference(title = getString(R.string.action_view_settings_disable_all_wheel_vibrate),
                                    summary = getString(R.string.action_view_settings_disable_all_wheel_vibrate_desc),
                                    checked = disableAllVibrate, enabled = ready, onCheckedChange = { disableAllVibrate = it; saveSwitch(KEY_DISABLE_ALL_WHEEL_VIBRATE, it) })
                            }
                            R.id.externalCallCard -> {
                                SectionHeader(R.string.external_call_title, R.string.external_call_description)
                                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                    Text(externalCallSummary, fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                    Text(getString(R.string.external_call_link_title), modifier = Modifier.padding(top = 12.dp), fontSize = 15.sp)
                                    SelectionContainer {
                                        Text(externalCallLink, modifier = Modifier.padding(top = 4.dp), fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace, color = MiuixTheme.colorScheme.primary)
                                    }
                                }
                                ArrowPreference(title = getString(R.string.external_call_config_button),
                                    enabled = ready, onClick = { selectExternalConfiguration() })
                                ArrowPreference(title = getString(R.string.external_call_copy_button),
                                    enabled = ready, onClick = {
                                        lifecycleScope.launch {
                                            ClipboardUtils.copyText(this@ActionViewSettingsActivity, buildExternalCallLink())
                                            ToastHelper.showInUIThread(this@ActionViewSettingsActivity, getString(R.string.external_call_link_copied))
                                        }
                                    })
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SectionHeader(title: Int, summary: Int) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(getString(title), fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(getString(summary), modifier = Modifier.padding(top = 4.dp), fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }

    private fun saveSwitch(key: String, value: Boolean) {
        lifecycleScope.launch { settingsStore.saveKey(key, value) }
    }

    private fun checkAccessibility() {
        if (!evoAccessibilityServiceIsEnable(applicationContext)) {
            Toast.makeText(applicationContext, getString(R.string.action_view_accessibility_required), Toast.LENGTH_SHORT).show()
        }
    }

    private fun editStyle(sub: Boolean) {
        checkAccessibility()
        showActionConfigBottomSheet(this, itemListTag, sub, lifecycleScope, { showConfiguration(sub) })
    }

    private fun showConfiguration(sub: Boolean) {
        lifecycleScope.launch {
            EventBusManager.produceActionViewEvent(this, ActionViewEvent(show = true,
                workMode = if (sub) ActionView.WorkMode.CONFIG_SUB else ActionView.WorkMode.CONFIG_MAIN,
                mainItemListTag = itemListTag, triggerConfig = getPreviewTrigger(), isMirror = previewMirror))
        }
    }

    private fun previewWheel() {
        lifecycleScope.launch {
            val trigger = getPreviewTrigger()
            if (trigger == null) {
                ToastHelper.showInUIThread(applicationContext, getString(R.string.action_view_no_trigger_selected))
                return@launch
            }
            EventBusManager.produceActionViewEvent(this, ActionViewEvent(show = true,
                workMode = ActionView.WorkMode.ACTIVE, mainItemListTag = itemListTag,
                triggerConfig = trigger, isMirror = previewMirror))
        }
    }

    override fun onPause() {
        super.onPause()
        EventBusManager.produceActionViewEvent(lifecycleScope,
            ActionViewEvent(show = false, workMode = ActionView.WorkMode.ACTIVE))
    }

    private fun selectPreviewTrigger() {
        showFreeTriggerSelectBottomSheet(activity = this, coroutineScope = lifecycleScope,
            title = getString(R.string.action_view_preview_trigger_select_title),
            subtitle = getString(R.string.action_view_preview_trigger_select_subtitle),
            currentSelectedTag = previewTriggerTag, currentSelectedMirror = previewMirror) { tag, mirror ->
            lifecycleScope.launch {
                previewTriggerTag = tag
                previewMirror = mirror
                settingsStore.saveKey(KEY_PREVIEW_TRIGGER_TAG, tag)
                settingsStore.saveKey(KEY_PREVIEW_MIRROR, mirror)
                refreshPreviewTriggerSummary()
            }
        }
    }

    private fun selectExternalConfiguration() {
        lifecycleScope.launch {
            showPieGroupSelectBottomSheet(activity = this@ActionViewSettingsActivity, coroutineScope = lifecycleScope,
                title = getString(R.string.external_call_group_select_title),
                subtitle = getString(R.string.external_call_group_select_subtitle),
                currentSelectedTag = externalCallActionItemsTag) { tag ->
                externalCallActionItemsTag = tag
                // Refresh even when the second selector is cancelled: the group selection already applies.
                lifecycleScope.launch { refreshExternalCallSummary() }
                showFreeTriggerSelectBottomSheet(activity = this@ActionViewSettingsActivity, coroutineScope = lifecycleScope,
                    currentSelectedTag = externalCallTriggerTag, currentSelectedMirror = externalCallMirror) { triggerTag, mirror ->
                    lifecycleScope.launch {
                        externalCallTriggerTag = triggerTag
                        externalCallMirror = mirror
                        settingsStore.saveKey(KEY_EXTERNAL_CALL_MIRROR, mirror)
                        refreshExternalCallSummary()
                    }
                }
            }
        }
    }

    private fun showCurrentPieGroupSelector() {
        if (pieGroups.isEmpty()) return
        lifecycleScope.launch {
            showPieGroupSelectBottomSheet(activity = this@ActionViewSettingsActivity, coroutineScope = lifecycleScope,
                title = getString(R.string.action_view_settings_current_group_title),
                subtitle = getString(R.string.action_view_settings_groups_description),
                currentSelectedTag = itemListTag) { itemListTag = it }
        }
    }

    private suspend fun setupPieGroupTabs() {
        val groups = GestureActionDataManager.readActionListTags(applicationContext)
        pieGroups.clear()
        pieGroups.addAll(groups)
        if (pieGroups.isNotEmpty() && pieGroups.none { it.tag == itemListTag }) itemListTag = pieGroups.first().tag
    }

    private suspend fun ensureExternalCallSelection() {
        if (pieGroups.isNotEmpty() && pieGroups.none { it.tag == externalCallActionItemsTag }) {
            externalCallActionItemsTag = pieGroups.first().tag
        }
        val triggers = FreeTriggerDataManager.readTriggers(applicationContext)
        if (triggers.isNotEmpty() && triggers.none { it.tag == externalCallTriggerTag }) {
            externalCallTriggerTag = triggers.first().tag
            externalCallMirror = false
            settingsStore.saveKey(KEY_EXTERNAL_CALL_MIRROR, false)
        }
        if (externalCallMirror && triggers.none { it.tag == externalCallTriggerTag && it.getState(FreeTriggerConfig.FLAG_MIRROR) }) {
            externalCallMirror = false
            settingsStore.saveKey(KEY_EXTERNAL_CALL_MIRROR, false)
        }
    }

    private suspend fun ensurePreviewTriggerSelection() {
        val triggers = FreeTriggerDataManager.readTriggers(applicationContext)
        if (triggers.isNotEmpty() && triggers.none { it.tag == previewTriggerTag }) {
            previewTriggerTag = triggers.first().tag
            previewMirror = false
            settingsStore.saveKey(KEY_PREVIEW_TRIGGER_TAG, previewTriggerTag)
            settingsStore.saveKey(KEY_PREVIEW_MIRROR, false)
        }
        if (previewMirror && triggers.none { it.tag == previewTriggerTag && it.getState(FreeTriggerConfig.FLAG_MIRROR) }) {
            previewMirror = false
            settingsStore.saveKey(KEY_PREVIEW_MIRROR, false)
        }
    }

    private suspend fun refreshPreviewTriggerSummary() {
        ensurePreviewTriggerSelection()
        previewTriggerName = FreeTriggerDataManager.getTrigger(applicationContext, previewTriggerTag)
            ?.let { triggerDisplayName(this, it, previewMirror) } ?: getString(R.string.action_view_no_trigger_selected)
    }

    private suspend fun getPreviewTrigger(): FreeTriggerConfig? {
        ensurePreviewTriggerSelection()
        return FreeTriggerDataManager.getTrigger(applicationContext, previewTriggerTag)
    }

    private suspend fun refreshExternalCallSummary() {
        ensureExternalCallSelection()
        val groupName = pieGroups.find { it.tag == externalCallActionItemsTag }?.name ?: getString(R.string.external_call_not_selected)
        val triggerName = FreeTriggerDataManager.getTrigger(applicationContext, externalCallTriggerTag)
            ?.let { triggerDisplayName(this, it, externalCallMirror) } ?: getString(R.string.external_call_not_selected)
        externalCallSummary = getString(R.string.external_call_summary_format, groupName, triggerName)
        externalCallLink = buildExternalCallLink()
    }

    private suspend fun buildExternalCallLink(): String {
        ensureExternalCallSelection()
        return uriBuilder(GestureActionType.SHOW_ACTION_VIEW).apply {
            if (externalCallActionItemsTag.isNotBlank()) appendQueryParameter("actionItemsTag", externalCallActionItemsTag)
            if (externalCallTriggerTag.isNotBlank()) appendQueryParameter("triggerTag", externalCallTriggerTag)
            if (externalCallMirror) appendQueryParameter("isMirror", true.toString())
        }.build().toString()
    }

    private fun showAddPieGroupDialog() {
        showEditTextBottomSheet(activity = this, coroutineScope = lifecycleScope,
            title = getString(R.string.action_view_add_group_title), hint = getString(R.string.action_view_group_name_hint),
            onConfirm = { newName ->
                lifecycleScope.launch {
                    val newTag = buildTag()
                    GestureActionDataManager.addActionItemsTag(applicationContext, ActionItemsTag().apply { tag = newTag; name = newName })
                    itemListTag = newTag
                    setupPieGroupTabs()
                    refreshExternalCallSummary()
                }
            })
    }

    private fun showManagePieGroupDialog(group: ActionItemsTag) {
        showEditTextBottomSheet(activity = this, coroutineScope = lifecycleScope,
            title = getString(R.string.action_view_edit_group_title), hint = getString(R.string.action_view_group_name_hint),
            defaultText = group.name,
            onConfirm = { newName ->
                lifecycleScope.launch {
                    GestureActionDataManager.updateActionItemsTag(applicationContext, group.tag, newName)
                    setupPieGroupTabs()
                    refreshExternalCallSummary()
                }
            }, showDeleteButton = true,
            deleteConfirmationMessage = getString(R.string.action_view_delete_group_confirm, group.name),
            onDelete = {
                lifecycleScope.launch {
                    GestureActionDataManager.removeActionItemsTag(applicationContext, group.tag)
                    if (itemListTag == group.tag) itemListTag = ""
                    setupPieGroupTabs()
                    refreshExternalCallSummary()
                }
            })
    }
}
