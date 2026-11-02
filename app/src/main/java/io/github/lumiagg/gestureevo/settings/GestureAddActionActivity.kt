package io.github.lumiagg.gestureevo.settings

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.db.APPModel
import io.github.lumiagg.gestureevo.gesture.ActionViewEvent
import io.github.lumiagg.gestureevo.gesture.EventBusManager
import io.github.lumiagg.gestureevo.gesture.config.GestureActionDataManager
import io.github.lumiagg.gestureevo.gesture.config.ActionConfig
import io.github.lumiagg.gestureevo.gesture.config.GestureActionDataManager.buildTag
import io.github.lumiagg.gestureevo.gesture.config.GestureActionItem
import io.github.lumiagg.gestureevo.gesture.config.GestureActionType
import io.github.lumiagg.gestureevo.gesture.config.IconType
import io.github.lumiagg.gestureevo.gesture.ui.ActionView
import io.github.lumiagg.gestureevo.gesture.ui.RoundImageTextView
import io.github.lumiagg.gestureevo.gesture.ui.gestureActionIcon
import io.github.lumiagg.gestureevo.settings.ui.ActivityModel
import io.github.lumiagg.gestureevo.settings.ui.showActionSelectBottomSheet
import io.github.lumiagg.gestureevo.settings.ui.DefaultSelectAPPBottomSheet
import io.github.lumiagg.gestureevo.settings.ui.LocalUserShortcutSheet
import io.github.lumiagg.gestureevo.settings.ui.SelectAPPCallBack
import io.github.lumiagg.gestureevo.settings.ui.SelectActivityBottomSheet
import io.github.lumiagg.gestureevo.settings.ui.SelectActivityCallBack
import io.github.lumiagg.gestureevo.settings.ui.SelectShortcutCallback
import io.github.lumiagg.gestureevo.settings.ui.ShortcutModel
import io.github.lumiagg.gestureevo.settings.ui.ShortcutSelectAPPBottomSheet
import io.github.lumiagg.gestureevo.settings.ui.showEditTextBottomSheet
import io.github.lumiagg.gestureevo.settings.ui.showPieGroupSelectBottomSheet
import io.github.lumiagg.gestureevo.utils.GestureActionLink
import io.github.lumiagg.gestureevo.utils.MultiUserAPP
import io.github.lumiagg.gestureevo.utils.NotificationPermissionUtils
import io.github.lumiagg.gestureevo.utils.TempDndPermissionRequester
import io.github.lumiagg.gestureevo.utils.icon.IconFileUtils
import io.github.lumiagg.gestureevo.utils.icon.IconHelper
import io.github.lumiagg.gestureevo.utils.icon.IconImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.lumiagg.gestureevo.ui.compose.MiuixEvoTheme
import io.github.lumiagg.gestureevo.ui.miuix.MiuixCard
import io.github.lumiagg.gestureevo.ui.miuix.MiuixScreen
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

data class ConfigButtonsState(var enable: Boolean = false)
class GestureAddActionActivity : AppCompatActivity() {
    // Stable identities are also used by asynchronous app/shortcut selection callbacks.
    private class ActionEditorState(val link: GestureActionLink) {
        var text by mutableStateOf("")
        var selected by mutableStateOf(false)
        var freeform by mutableStateOf(false)
        var newWindow by mutableStateOf(false)
        var dualOpen by mutableStateOf(false)
        var timeout by mutableStateOf(false)
        fun getActionLink() = link
    }
    private data class Choice(val title: Int, val onSelect: () -> Unit)
    private data class ChoiceDialog(val title: Int, val choices: List<Choice>)
    private var choiceDialog by mutableStateOf<ChoiceDialog?>(null)
    private var iconImage by mutableStateOf<Any?>(null)
    private var iconText by mutableStateOf<String?>(null)
    private var saving by mutableStateOf(false)
    private var itemListTag = ""
    private var sourceActionView = ActionView.ActionViewType.MAIN
    private var editMode = false
    private var externalActionMode = false
    private var actionItem = GestureActionItem()
    private val firstActionLink = GestureActionLink()
    private val secondActionLink = GestureActionLink()
    private val firstConfig = ActionEditorState(firstActionLink)
    private val secondConfig = ActionEditorState(secondActionLink)
    private val iconFileUtils = IconFileUtils(this)
    private var lastSelectedConfig: ActionEditorState? = null
    private var selectTriggerActionMode = false
    private var expectItemTag: String? = null
    private lateinit var tempDndPermissionRequester: TempDndPermissionRequester

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tempDndPermissionRequester = TempDndPermissionRequester(this)
        initData()
        // Fixed gestures always execute on timeout, including after picking or syncing a link.
        if (selectTriggerActionMode) {
            firstConfig.timeout = true
            firstActionLink.isTimeout = true
            if (firstConfig.text.isNotEmpty()) applyLink(firstConfig, false)
        }
        setContent {
            MiuixEvoTheme {
                EditorScreen()
                choiceDialog?.let { ChoicesDialog(it) }
            }
        }
    }

    @Composable
    private fun EditorScreen() {
        MiuixScreen(
            title = stringResource(if (externalActionMode) R.string.external_action_config_title else R.string.gesture_add_action_config_select_event),
            onBack = { finish() },
            actions = {
                TextButton(text = stringResource(if (externalActionMode) R.string.external_action_save_button else R.string.gesture_add_action_save),
                    colors = ButtonDefaults.textButtonColorsPrimary(), enabled = !saving, onClick = { if (externalActionMode) saveExternalAction() else saveAction(false) })
            },
        ) {
            if (!externalActionMode) item {
                MiuixCard {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Only this local icon preview is a View; no XML is embedded.
                        AndroidView(factory = { context -> RoundImageTextView(context).apply {
                            contentDescription = getString(R.string.icon_select_title)
                            setOnClickListener { selectImage() }
                        } }, modifier = Modifier.size(60.dp), update = { view ->
                            val image = iconImage
                            val text = iconText
                            when {
                                text != null -> view.setText(text)
                                image != null -> view.setImage(image)
                                editMode -> gestureActionIcon(this@GestureAddActionActivity, view, actionItem)
                                else -> view.setImage(R.drawable.baseline_image_24)
                            }
                        })
                        TextButton(text = stringResource(R.string.icon_select_title), enabled = !saving, onClick = ::selectImage)
                    }
                }
            }
            item { ConfigCard(firstConfig, true) }
            if (!externalActionMode && !selectTriggerActionMode) {
                item {
                    MiuixCard {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            TextButton(text = stringResource(R.string.gesture_add_action_sync_up), modifier = Modifier.weight(1f), enabled = !saving,
                                onClick = { syncLink(secondConfig, firstConfig) })
                            TextButton(text = stringResource(R.string.gesture_add_action_sync_down), modifier = Modifier.weight(1f), enabled = !saving,
                                onClick = { syncLink(firstConfig, secondConfig) })
                        }
                    }
                }
                item { ConfigCard(secondConfig, false) }
                item {
                    MiuixCard {
                        ArrowPreference(title = stringResource(R.string.gesture_add_action_transparent_icon), enabled = !saving,
                            onClick = { if (editMode) saveAction(true) else batchSavePlaceHolder(5) })
                    }
                }
            }
        }
    }

    @Composable
    private fun ConfigCard(config: ActionEditorState, first: Boolean) {
        MiuixCard {
            if (!selectTriggerActionMode) Text(
                text = stringResource(if (externalActionMode) R.string.external_action_config_title else if (first) R.string.gesture_add_action_config_short_press else R.string.gesture_add_action_long_press),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium,
            )
            ArrowPreference(title = stringResource(R.string.gesture_add_action_config_select_event),
                summary = if (config.selected) config.link.actionType.showName(this@GestureAddActionActivity) else null,
                enabled = !saving, onClick = { showActionPicker(config) })
            SwitchPreference(title = stringResource(R.string.gesture_add_action_config_freeform), checked = config.freeform, enabled = !saving,
                onCheckedChange = { config.freeform = it; updateFlags(config) })
            SwitchPreference(title = stringResource(R.string.gesture_add_action_config_new_window), checked = config.newWindow, enabled = !saving,
                onCheckedChange = { config.newWindow = it; updateFlags(config) })
            SwitchPreference(title = stringResource(R.string.gesture_add_action_config_dual_open), checked = config.dualOpen, enabled = !saving,
                onCheckedChange = { config.dualOpen = it; updateFlags(config) })
            SwitchPreference(title = stringResource(if (first) R.string.gesture_add_action_touch_execute else R.string.gesture_add_action_config_timeout_execute),
                checked = config.timeout, enabled = !saving && !(first && selectTriggerActionMode),
                onCheckedChange = { config.timeout = it; updateFlags(config) })
            TextField(value = config.text, onValueChange = { config.text = it },
                label = stringResource(R.string.gesture_add_action_config_link_hint),
                modifier = Modifier.fillMaxWidth().padding(16.dp), enabled = !saving)
        }
    }

    private fun updateFlags(config: ActionEditorState) {
        val link = config.link
        link.isFreeform = config.freeform
        link.isNewWindow = config.newWindow
        link.isTimeout = config.timeout || (config === firstConfig && selectTriggerActionMode)
        link.userId = if (config.dualOpen && link.pkg != null) {
            MultiUserAPP(applicationContext).getSerialNumberForUser(link.pkg!!)
        } else 0
        applyLink(config, false)
    }

    @Composable
    private fun ChoicesDialog(model: ChoiceDialog) {
        Dialog(onDismissRequest = { choiceDialog = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            MiuixCard(Modifier.padding(horizontal = 12.dp)) {
                Text(stringResource(model.title), modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp),
                    fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
                Column(Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState())) {
                    model.choices.forEach { choice ->
                        ArrowPreference(title = stringResource(choice.title), onClick = {
                            choiceDialog = null
                            choice.onSelect()
                        })
                    }
                }
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    TextButton(text = stringResource(android.R.string.cancel), modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                        onClick = { choiceDialog = null })
                }
            }
        }
    }

    private fun initData() {
        externalActionMode = intent.getBooleanExtra(EXTRA_EXTERNAL_ACTION_MODE, false)
        if (externalActionMode) {
            intent.getStringExtra(EXTRA_EXTERNAL_ACTION_LINK)?.let {
                fillActionConfig(firstConfig, it)
            }
            return
        }

        itemListTag = intent.getStringExtra("tag").toString()
        sourceActionView = ActionView.ActionViewType.valueOf(
            intent.getStringExtra("source") ?: ActionView.ActionViewType.MAIN.name
        )
        editMode = intent.getBooleanExtra("editMode", false)
        selectTriggerActionMode = intent.getBooleanExtra("triggerActionMode", false)
        expectItemTag = intent.getStringExtra("expectItemTag")
        if (editMode) {
            GestureActionItem.toModel(intent.getStringExtra("item")!!)?.let {
                actionItem = it
                actionItem.firstStageLink?.let {
                    fillActionConfig(firstConfig, actionItem.firstStageLink!!)
                }
                actionItem.secondStageLink?.let {
                    fillActionConfig(secondConfig, actionItem.secondStageLink!!)
                }
            }
        }
    }

    private fun fillActionConfig(
        configState: ActionEditorState, link: String
    ) {
        val actionLink = configState.getActionLink()
        actionLink.parseLink(link)
        configState.selected = true
        configState.freeform = actionLink.isFreeform
        configState.newWindow = actionLink.isNewWindow
        configState.dualOpen = actionLink.isDualOpen
        configState.timeout = actionLink.isTimeout
        applyLink(configState, needSyncToLongAction = false)
    }

    /**
     * 统一动作目录：手势、轮盘与外部动作入口共用同一个可搜索的分类面板。
     * 外部动作模式下隐藏「无动作 / 点击穿透 / 显示动作视图」，与旧弹窗过滤规则一致。
     */
    private fun showActionPicker(configState: ActionEditorState) {
        showActionSelectBottomSheet(
            activity = this,
            current = configState.getActionLink(),
            allowWheel = !externalActionMode,
            allowNone = !externalActionMode,
            allowClickThrough = !externalActionMode,
        ) { choice ->
            val type = choice.type
            if (!choice.parameters.isNullOrEmpty()) {
                configState.link.actionType = type
                configState.link.queryList.clear()
                applyActionParameters(configState, choice.parameters)
            } else {
                selectAction(configState, type)
            }
        }
    }

    /** 把目录里带预设参数的动作（如系统导航 accAction）写回链接。 */
    private fun applyActionParameters(
        configState: ActionEditorState,
        parameters: Map<String, String>
    ) {
        val link = configState.getActionLink()
        parameters.forEach { (key, value) ->
            link.queryList.removeAll { it.first == key }
            link.queryList.add(key to value)
        }
        applyLink(configState)
    }

    private val selectImageLauncher = registerForActivityResult(IconEditorContract()) { path ->
        if (path != null) lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) { IconImageIO.read(path) }
            if (bitmap != null) {
                iconFileUtils.setImgBitmap(bitmap)
                iconFileUtils.imgPath = path
                actionItem.iconType = IconType.URI
                actionItem.icon = path
                updateIconImage(bitmap)
            } else {
                Toast.makeText(this@GestureAddActionActivity, R.string.icon_editor_load_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun selectGalleryIcon() {
        lifecycleScope.launch {
            val style = if (selectTriggerActionMode) null else {
                intent.getStringExtra(EXTRA_ICON_WHEEL_STYLE) ?: run {
                    val tag = itemListTag.takeUnless { it.isBlank() || it == "null" } ?: ActionConfig.DEFAULT_TAG
                    val main = GestureActionDataManager.readActionConfig(this@GestureAddActionActivity, tag, false)
                    val config = if (sourceActionView == ActionView.ActionViewType.SUB) {
                        GestureActionDataManager.readActionConfig(this@GestureAddActionActivity, tag, true).apply {
                            if (subActionFollowConfig) {
                                itemDiameter = main.itemDiameter * 0.8f
                                iconPadding = main.iconPadding * 0.8f
                                iconCornerRadius = main.iconCornerRadius * 0.8f
                            }
                        }
                    } else main
                    config.toJson(false)
                }
            }
            selectImageLauncher.launch(IconEditorRequest(wheelStyle = style))
        }
    }

    private fun updateAndSaveUriImage(img: Drawable) {
        actionItem.iconType = IconType.URI
        updateIconImage(img)
        // Let IconImageIO rasterize vectors/adaptive icons at a valid target size on save.
        iconFileUtils.setImgDrawable(img)
    }

    private fun updateIconImage(image: Any?) {
        iconText = null
        iconImage = image
    }

    private fun updateIconText(text: String) {
        iconImage = null
        iconText = text
    }

    private fun selectImage() {
        choiceDialog = ChoiceDialog(R.string.icon_select_title, listOf(
            Choice(R.string.icon_select_gallery, ::selectGalleryIcon),
            Choice(R.string.icon_select_app_icon) {
                DefaultSelectAPPBottomSheet(this, lifecycleScope, object : SelectAPPCallBack {
                    override fun selectCompleted(model: APPModel) {
                        IconHelper.getIcon(applicationContext, model.pkgName)?.let(::updateAndSaveUriImage)
                    }
                }).show()
            },
            Choice(R.string.icon_select_text_icon) {
                showEditTextBottomSheet(activity = this, coroutineScope = lifecycleScope,
                    title = getString(R.string.gesture_add_action_text_icon_title),
                    hint = getString(R.string.gesture_add_action_text_icon_hint),
                    onConfirm = { inputText ->
                        actionItem.name = inputText.trim()
                        actionItem.iconType = IconType.TEXT
                        updateIconText(actionItem.name ?: "")
                    })
            },
        ))
    }

    private val shortcutPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.let { data ->
                val shortcutIntent =
                    data.getParcelableExtra<Intent>("android.intent.extra.shortcut.INTENT")
                val shortcutName = data.getStringExtra("android.intent.extra.shortcut.NAME")
                val shortcutIcon =
                    data.getParcelableExtra<Bitmap>("android.intent.extra.shortcut.ICON")

                val shortcutUri = shortcutIntent?.toUri(Intent.URI_INTENT_SCHEME)

                lastSelectedConfig?.let { configState ->
                    shortcutUri?.let { uri ->
                        val actionLink = configState.getActionLink()
                        actionLink.addParams("uri", uri)
                        applyLink(configState)
                        shortcutIcon?.let { icon ->
                            actionItem.iconType = IconType.URI
                            iconFileUtils.setImgBitmap(icon)
                            updateIconImage(icon)
                        }
                    }
                }
            }
        }
    }

    private fun selectAccAction(callback: (Int, Drawable?) -> Unit) {
        choiceDialog = ChoiceDialog(R.string.acc_action_select_title, listOf(
            R.string.acc_action_select_back to 1,
            R.string.acc_action_select_recents to 3,
            R.string.acc_action_select_quick_settings to 5,
            R.string.acc_action_select_notifications to 4,
            R.string.acc_action_select_home to 2,
            R.string.acc_action_select_screenshot to 9,
            R.string.acc_action_select_lock_screen to 8,
            R.string.acc_action_select_power_dialog to 6,
        ).map { (title, id) -> Choice(title) { callback(id, null) } })
    }

    private fun selectClipboardAction(callback: (String, Drawable?) -> Unit) {
        choiceDialog = ChoiceDialog(R.string.clipboard_action_select_title, listOf(
            R.string.clipboard_action_select_pin to "pin",
            R.string.clipboard_action_select_search to "search",
            R.string.clipboard_action_select_translate to "translate",
            R.string.clipboard_action_select_share_inspire to "inspire",
            R.string.clipboard_action_select_save_image to "saveImage",
        ).map { (title, id) -> Choice(title) { callback(id, null) } })
    }

    private fun selectAction(
        configState: ActionEditorState, type: GestureActionType
    ) {
        lastSelectedConfig = configState
        val actionLink = configState.getActionLink()
        actionLink.actionType = type
        val oldQueryList: ArrayList<Pair<String, String>> = ArrayList()
        oldQueryList.addAll(actionLink.queryList)
        actionLink.queryList.clear()

        when (type) {
            GestureActionType.OPEN_APP, GestureActionType.FREEZE_APP -> {
                DefaultSelectAPPBottomSheet(this, lifecycleScope, object : SelectAPPCallBack {
                    override fun selectCompleted(model: APPModel) {
                        actionLink.addParams("pkg", model.pkgName)
                        applyLink(configState)
                        if (configState == firstConfig && actionItem.iconType == IconType.OTHER) {
                            actionItem.iconType = IconType.OTHER
                            updateIconImage(
                                IconHelper.getIcon(applicationContext, model.pkgName)
                            )
                        }
                    }
                }).show()
            }

            GestureActionType.OPEN_ACTIVITY -> {
                SelectActivityBottomSheet(this, lifecycleScope, object : SelectActivityCallBack {
                    override fun selectCompleted(model: ActivityModel) {
                        val intent = Intent().apply {
                            component = ComponentName(model.packageName, model.activityName)
                        }
                        // Convert Intent to URI
                        val uri = intent.toUri(Intent.URI_INTENT_SCHEME)
                        actionLink.addParams("uri", uri)
                        actionLink.addParams("pkg", model.packageName)
                        actionLink.addParams("activityName", model.activityName)
                        actionLink.addParams("isExported", model.isExported.toString())
                        applyLink(configState)
                        actionItem.iconType = IconType.OTHER
                        updateIconImage(
                            IconHelper.getIcon(applicationContext, model.packageName)
                        )
                    }
                }).show()
            }

            GestureActionType.OTHER_LINK -> {
                showEditTextBottomSheet(
                    activity = this,
                    coroutineScope = lifecycleScope,
                    title = getString(R.string.gesture_add_action_link_title),
                    hint = getString(R.string.gesture_add_action_link_hint),
                    defaultText = oldQueryList.find { it.first == "uri" }?.second,
                    onConfirm = { inputText ->
                        if (inputText.isNotEmpty()) {
                            actionLink.addParams("uri", inputText)
                            applyLink(configState)
                        }
                    }
                )
            }

            GestureActionType.OPEN_SHELL -> {
                showEditTextBottomSheet(
                    activity = this,
                    coroutineScope = lifecycleScope,
                    title = getString(R.string.gesture_add_action_shell_title),
                    hint = getString(R.string.gesture_add_action_shell_hint),
                    defaultText = oldQueryList.find { it.first == "shell" }?.second,
                    onConfirm = { inputText ->
                        if (inputText.isNotEmpty()) {
                            actionLink.addParams("shell", inputText)
                            applyLink(configState)
                        }
                    }
                )
            }

            GestureActionType.TEMP_DND -> {
                selectTempDndAction(configState, actionLink, oldQueryList)
            }

            GestureActionType.PAUSE_GESTURE -> {
                showPauseGestureSecondsInput(configState, actionLink, oldQueryList)
            }

            GestureActionType.OTHER_SHORTCUT -> {
                selectOtherShortcut(configState)
            }

            GestureActionType.SHOW_ACTION_VIEW -> {
                showPieGroupSelectBottomSheet(
                    activity = this,
                    coroutineScope = lifecycleScope,
                    currentSelectedTag = itemListTag,
                    onSelect = { selectedTag ->
                        actionLink.addParams("actionItemsTag", selectedTag)
                        applyLink(configState)
                    }
                )
            }

            GestureActionType.ACC_ACTION -> {
                selectAccAction { actionId, icon ->
                    val actionLink = configState.getActionLink()
                    actionLink.addParams("accAction", actionId.toString())
                    applyLink(configState)
                }
            }

            GestureActionType.CLIPBOARD_ACTION -> {
                selectClipboardAction { actionId, icon ->
                    val actionLink = configState.getActionLink()
                    actionLink.addParams("clipAction", actionId)
                    applyLink(configState)
                }
            }

            else -> {
                applyLink(configState)
            }
        }
    }

    private fun selectTempDndAction(
        configState: ActionEditorState,
        actionLink: GestureActionLink,
        oldQueryList: ArrayList<Pair<String, String>>
    ) {
        val showMinutesInput = {
            showEditTextBottomSheet(
                activity = this,
                coroutineScope = lifecycleScope,
                title = getString(R.string.gesture_add_action_temp_dnd_minutes_title),
                hint = getString(R.string.gesture_add_action_temp_dnd_minutes_hint),
                defaultText = oldQueryList.find { it.first == "minutes" }?.second ?: "15",
                onConfirm = { inputText ->
                    val minutes = inputText.trim().toIntOrNull()
                    if (minutes == null || minutes <= 0) {
                        Toast.makeText(
                            this,
                            getString(R.string.gesture_add_action_temp_dnd_minutes_invalid),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        actionLink.addParams("minutes", minutes.toString())
                        applyLink(configState)
                    }
                }
            )
        }

        tempDndPermissionRequester.ensurePermissionWithPrompt {
            showMinutesInput()
        }
    }

    private fun showPauseGestureSecondsInput(
        configState: ActionEditorState,
        actionLink: GestureActionLink,
        oldQueryList: ArrayList<Pair<String, String>>
    ) {
        val showSecondsInput = {
            showEditTextBottomSheet(
                activity = this,
                coroutineScope = lifecycleScope,
                title = getString(R.string.gesture_add_action_pause_seconds_title),
                hint = getString(R.string.gesture_add_action_pause_seconds_hint),
                defaultText = oldQueryList.find { it.first == "seconds" }?.second ?: "3",
                onConfirm = { inputText ->
                    val seconds = inputText.trim().toIntOrNull()
                    if (seconds == null || seconds <= 0) {
                        Toast.makeText(
                            this,
                            getString(R.string.gesture_add_action_pause_seconds_invalid),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        actionLink.addParams("seconds", seconds.toString())
                        applyLink(configState)
                    }
                }
            )
        }
        // The countdown notification is the only handle on a paused trigger, so ask for
        // notification access while the action is being configured.
        if (NotificationPermissionUtils.canPostNotifications(this)) {
            showSecondsInput()
        } else {
            tempDndPermissionRequester.ensureNotificationPermissionWithPrompt(showSecondsInput)
        }
    }

    private fun selectOtherShortcut(configState: ActionEditorState) {
        choiceDialog = ChoiceDialog(R.string.shortcut_source_select_title, listOf(
            Choice(R.string.shortcut_source_select_local) {
                // Bind the result to the editor which actually launched the external picker.
                ShortcutSelectAPPBottomSheet(this, lifecycleScope, object : SelectAPPCallBack {
                    override fun selectCompleted(model: APPModel) {
                        val shortcutIntent = Intent("android.intent.action.CREATE_SHORTCUT").setPackage(model.pkgName)
                        if (packageManager.queryIntentActivities(shortcutIntent, 0).isNotEmpty()) {
                            lastSelectedConfig = configState
                            shortcutPickerLauncher.launch(shortcutIntent)
                        } else {
                            Toast.makeText(this@GestureAddActionActivity, R.string.gesture_add_action_shortcut_create_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                }).show()
            },
            Choice(R.string.shortcut_source_select_online) {
                LocalUserShortcutSheet(this, lifecycleScope, object : SelectShortcutCallback {
                    override fun selectCompleted(model: ShortcutModel) {
                        val actionLink = configState.getActionLink()
                        actionLink.addParams("uri", model.scheme)
                        actionLink.addParams("pkg", model.packageName)
                        applyLink(configState)
                    }
                }).show()
            },
        ))
    }

    private fun applyLink(
        configState: ActionEditorState, needSyncToLongAction: Boolean = true
    ) {
        val actionLink = configState.getActionLink()
        if (configState === firstConfig && selectTriggerActionMode) {
            actionLink.isTimeout = true
            configState.timeout = true
        }
        configState.selected = true
        configState.text = actionLink.buildLink()

        if (
            needSyncToLongAction &&
            !externalActionMode &&
            configState == firstConfig &&
            secondConfig.text.isEmpty()
        ) {
            fillActionConfig(secondConfig, firstActionLink.buildLink())
        }
    }

    private fun syncLink(
        sourceConfig: ActionEditorState,
        targetConfig: ActionEditorState
    ) {
        fillActionConfig(targetConfig, sourceConfig.text)
    }


    private fun batchSavePlaceHolder(total: Int) {
        if (saving) return
        saving = true
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                repeat(total) {
                    val item = GestureActionItem().apply { iconType = IconType.INVISIBLE; tag = buildTag() }
                    GestureActionDataManager.addActionItem(applicationContext, itemListTag, item)
                }
            }
            finish()
        }
    }

    private fun saveExternalAction() {
        val link = firstConfig.text
        if (link.isEmpty()) {
            Toast.makeText(
                this,
                getString(R.string.external_action_select_required),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        setResult(
            RESULT_OK,
            Intent().apply {
                putExtra(EXTRA_EXTERNAL_ACTION_LINK, link)
            }
        )
        finish()
    }

    private fun saveAction(isPlaceHolder: Boolean) {
        if (saving) return
        saving = true
        actionItem.firstStageLink = if (selectTriggerActionMode) {
            GestureActionLink().parseLink(firstConfig.text).apply { isTimeout = true }.buildLink()
        } else firstConfig.text
        actionItem.secondStageLink = secondConfig.text
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                if (iconFileUtils.saveIcon()) actionItem.icon = iconFileUtils.imgPath
                if (isPlaceHolder) actionItem.iconType = IconType.INVISIBLE
                Log.d(TAG, "saveAction: tag $itemListTag")
                if (editMode) {
                    GestureActionDataManager.updateActionItem(applicationContext, itemListTag, actionItem)
                } else {
                    actionItem.tag = expectItemTag ?: buildTag()
                    GestureActionDataManager.addActionItem(applicationContext, itemListTag, actionItem)
                }
            }
            if (selectTriggerActionMode) setResult(RESULT_OK, Intent())
            finish()
        }
    }

    override fun finish() {
        super.finish()
        if (selectTriggerActionMode || externalActionMode) return
        if (sourceActionView == ActionView.ActionViewType.MAIN) {
            EventBusManager.produceActionViewEvent(
                lifecycleScope, ActionViewEvent(
                    show = true,
                    workMode = ActionView.WorkMode.CONFIG_MAIN,
                    mainItemListTag = itemListTag
                )
            )
        } else {
            EventBusManager.produceActionViewEvent(
                lifecycleScope, ActionViewEvent(
                    show = true,
                    workMode = ActionView.WorkMode.CONFIG_SUB,
                    subItemListTag = itemListTag
                )
            )
        }
    }


    companion object {
        const val TAG = "GestureAddActionActivity"
        const val EXTRA_EXTERNAL_ACTION_MODE = "extra_external_action_mode"
        const val EXTRA_EXTERNAL_ACTION_LINK = "extra_external_action_link"
        const val EXTRA_RETURN_ACTION_ITEM = "extra_return_action_item"
        const val EXTRA_ACTION_ITEM = "item"
        const val EXTRA_ICON_WHEEL_STYLE = "icon_wheel_style"
    }
}
