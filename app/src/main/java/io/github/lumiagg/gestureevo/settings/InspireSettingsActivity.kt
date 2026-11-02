package io.github.lumiagg.gestureevo.settings

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.db.APPModel
import io.github.lumiagg.gestureevo.gesture.EventBusManager.produceKeyboardEvent
import io.github.lumiagg.gestureevo.gesture.KeyboardEvent
import io.github.lumiagg.gestureevo.quick.ui.KeyboardType
import io.github.lumiagg.gestureevo.settings.ui.BlackListSelectAPPBottomSheet
import io.github.lumiagg.gestureevo.settings.ui.DefaultLinkOpenManager
import io.github.lumiagg.gestureevo.settings.ui.DefaultSelectAPPBottomSheet
import io.github.lumiagg.gestureevo.settings.ui.NotificationDisplayTimeBottomSheet
import io.github.lumiagg.gestureevo.settings.ui.PointerTouchAreaOffsetBottomSheet
import io.github.lumiagg.gestureevo.settings.ui.SelectAPPCallBack
import io.github.lumiagg.gestureevo.settings.ui.showEditTextBottomSheet
import io.github.lumiagg.gestureevo.settings.ui.showMiuixSheet
import io.github.lumiagg.gestureevo.utils.ToastHelper
import io.github.lumiagg.gestureevo.utils.app.appDisplayName
import io.github.lumiagg.gestureevo.utils.readKey
import io.github.lumiagg.gestureevo.utils.saveKey
import io.github.lumiagg.gestureevo.utils.settingsStore
import kotlinx.coroutines.launch

import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lumiagg.gestureevo.ui.compose.MiuixEvoTheme
import io.github.lumiagg.gestureevo.ui.miuix.MiuixCard
import io.github.lumiagg.gestureevo.ui.miuix.MiuixScreen
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Compose settings surface; shared selectors and value editors retain their existing workflows. */
class InspireSettingsActivity : AppCompatActivity() {
    private var ready by mutableStateOf(false)
    private var focusedSections by mutableStateOf<Set<Int>>(emptySet())
    private val switches = mutableStateMapOf<String, Boolean>()
    private var translationLabel by mutableStateOf("")
    private var imageLabel by mutableStateOf("")
    private var shareImageLabel by mutableStateOf("")
    private var searchLabel by mutableStateOf("")
    private var shareBehaviorLabel by mutableStateOf("")
    private var keyboardLabel by mutableStateOf("")
    private var pinPoolModeLabel by mutableStateOf("")
    private var notificationTimeLabel by mutableStateOf("")
    private var pointerOffsetLabel by mutableStateOf("")
    private var autoDeleteLabel by mutableStateOf("")

    private val notSelection: String
        get() = getString(R.string.inspire_settings_current_not_selected)
    private val clearSelection: String
        get() = getString(R.string.inspire_settings_clear_selection)
    private val evoImageEditorLabel: String
        get() = getString(R.string.inspire_settings_use_evo_image_editor)
    private val evoSearchLabel: String
        get() = getString(R.string.inspire_settings_use_evo_search)

    companion object {
        // Preference keys
        const val KEY_OCR_PRIORITY = "inspire_ocr_priority"
        const val KEY_INSPIRE_DOUBLE_CHECK = "key_inspire_double_check"
        const val KEY_INSPIRE_SELECTION_VIBRATE = "key_inspire_selection_vibrate"
        const val KEY_INSPIRE_EDGE_BACK = "key_inspire_edge_back"
        const val KEY_INSPIRE_DELAY_REMOVE_IMAGE = "key_inspire_delay_remove_image"
        const val KEY_INSPIRE_SHOW_ENGINE = "key_inspire_show_engine"
        const val KEY_DEFAULT_IMAGE_TEXT_OVERLAY = "key_default_image_text_overlay"
        const val KEY_INSPIRE_STRONG_COPY = "key_inspire_strong_copy"
        const val KEY_KEYBOARD_TYPE = "keyboard_type"
        const val KEY_TRANSLATION_APP = "key_translation_app"

        const val KEY_IMAGE_APP = "key_image_app"
        const val KEY_DEFAULT_SHARE_IMAGE_APP = "key_default_share_image_app"
        const val KEY_SEARCH_APP = "key_search_app"
        const val KEY_SEARCH_LINK = "key_search_link"
        const val KEY_SHOW_NOTIFICATION_ICON = "key_show_notification_icon"
        const val KEY_NOTIFICATION_IMAGE_DISPLAY_SECONDS =
            "key_notification_image_display_seconds"
        const val KEY_SHOW_POINTER_TRAIL = "key_show_pointer_trail"
        const val KEY_ALWAYS_SHOW_POINTER_TOUCH_AREA = "key_always_show_pointer_touch_area"
        const val KEY_POINTER_TOUCH_AREA_OFFSET_DP = "key_pointer_touch_area_offset_dp"
        const val KEY_ENABLE_POINTER_DRAG_MODE = "key_enable_pointer_drag_mode"
        const val KEY_PIN_POOL_DISPLAY_MODE = "key_pin_pool_display_mode"
        const val KEY_PIN_POOL_LOCAL_RULE_EXTRACTION = "key_pin_pool_local_rule_extraction"
        const val DEFAULT_PIN_POOL_LOCAL_RULE_EXTRACTION = false
        const val KEY_PIN_POOL_SAVE_SCREEN_CONTENT_IMAGES =
            "key_pin_pool_save_screen_content_images"
        const val KEY_PIN_POOL_AUTO_COPY_IMAGE_TO_CLIPBOARD =
            "key_pin_pool_auto_copy_image_to_clipboard"
        const val DEFAULT_PIN_POOL_AUTO_COPY_IMAGE_TO_CLIPBOARD = false
        const val KEY_PIN_POOL_AUTO_DELETE_DAYS = "key_pin_pool_auto_delete_days"
        const val PIN_POOL_AUTO_DELETE_DAYS_MAX = 30
        const val PIN_POOL_DISPLAY_MODE_FULLSCREEN = "fullscreen"
        const val PIN_POOL_DISPLAY_MODE_LEFT_SIDEBAR = "left_sidebar"
        const val PIN_POOL_DISPLAY_MODE_RIGHT_SIDEBAR = "right_sidebar"
        const val PIN_POOL_DISPLAY_MODE_FOLLOW_TRIGGER = "follow_trigger"
        const val PIN_POOL_DISPLAY_MODE_LEGACY_SIDEBAR = "sidebar"
        const val KEY_DEFAULT_SHARE_BEHAVIOR = "key_default_share_behavior"
        const val KEY_EXTERNAL_DROP_ENABLED = "key_external_drop_enabled"
        const val KEY_EXTERNAL_DROP_SHOW_ENGINES = "key_external_drop_show_engines"
        const val DEFAULT_NOTIFICATION_IMAGE_DISPLAY_SECONDS = 6
//        const val KEY_POINTER_ICON_PATH = "key_pointer_icon_PATH"
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readFocusIntent(intent) // Filter before the first composition, independently of storage IO.
        setContent { MiuixEvoTheme { SettingsContent() } }
        lifecycleScope.launch { initializeSettings(); ready = true }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readFocusIntent(intent)
    }

    private fun readFocusIntent(source: Intent) {
        val ids = buildList {
            source.getIntExtra(SettingsSearchNavigator.EXTRA_TARGET_VIEW_ID, View.NO_ID)
                .takeIf { it != View.NO_ID }?.let(::add)
            source.getIntArrayExtra(SettingsSearchNavigator.EXTRA_TARGET_VIEW_IDS)?.let { addAll(it.toList()) }
        }
        focusedSections = ids.mapNotNull { id ->
            when (id) {
                R.id.screenContentSwitchCard, R.id.ocrPrioritySwitch, R.id.defaultImageTextOverlaySwitch, R.id.doubleCheckSwitch, R.id.selectionVibrateSwitch, R.id.edgeBackSwitch, R.id.delayRemoveImageSwitch, R.id.showEngineSwitch, R.id.strongCopySwitch -> R.id.screenContentSwitchCard
                R.id.defaultShareBehaviorCard, R.id.default_share_behavior_row, R.id.selected_default_share_behavior_text, R.id.externalDropSwitch, R.id.externalDropEnginesSwitch -> R.id.defaultShareBehaviorCard
                R.id.appSelectionCard, R.id.translation_app_row, R.id.selected_translation_app_text, R.id.clear_translation_app_button, R.id.search_app_row, R.id.selected_search_app_text, R.id.clear_search_app_button, R.id.image_app_row, R.id.selected_image_app_text, R.id.clear_image_app_button, R.id.default_share_image_app_row, R.id.selected_default_share_image_app_text, R.id.clear_default_share_image_app_button, R.id.link_open_row -> R.id.appSelectionCard
                R.id.PointerCard, R.id.pointerTrailSwitch, R.id.pointerTouchAreaSwitch, R.id.pointer_touch_area_offset_row, R.id.selected_pointer_touch_area_offset_text, R.id.pointerDragModeSwitch -> R.id.PointerCard
                R.id.pinPoolCard, R.id.pin_pool_mode_row, R.id.selected_pin_pool_mode_text, R.id.pin_pool_local_rule_extraction_row, R.id.pinPoolLocalRuleExtractionSwitch, R.id.pin_pool_save_screen_content_row, R.id.pinPoolSaveScreenContentSwitch, R.id.pin_pool_auto_copy_clipboard_row, R.id.pinPoolAutoCopyClipboardSwitch, R.id.pin_pool_auto_delete_group, R.id.pin_pool_auto_delete_row, R.id.selected_pin_pool_auto_delete_text -> R.id.pinPoolCard
                R.id.notificationCard, R.id.notificationIconSwitch, R.id.notification_listener_white_list_button, R.id.notification_icon_black_list_button, R.id.notification_display_time_row, R.id.selected_notification_display_time_text, R.id.notification_permission_button -> R.id.notificationCard
                R.id.keyboardTypeCard, R.id.keyboard_type_row, R.id.selected_keyboard_type_text -> R.id.keyboardTypeCard
                else -> null
            }
        }.toSet()
    }

    private suspend fun initializeSettings() {
        // Initialize OCR-priority switch state
        val ocrPriority = settingsStore.readKey(KEY_OCR_PRIORITY, true)
        switches[KEY_OCR_PRIORITY] = ocrPriority

        // Double confirmation
        switches[KEY_INSPIRE_DOUBLE_CHECK] = settingsStore.readKey(KEY_INSPIRE_DOUBLE_CHECK, false)

        // Vibrate on selection enter
        switches[KEY_INSPIRE_SELECTION_VIBRATE] =
            settingsStore.readKey(KEY_INSPIRE_SELECTION_VIBRATE, true)
        switches[KEY_INSPIRE_EDGE_BACK] = settingsStore.readKey(KEY_INSPIRE_EDGE_BACK, true)

        // Delayed deletion
        switches[KEY_INSPIRE_DELAY_REMOVE_IMAGE] =
            settingsStore.readKey(KEY_INSPIRE_DELAY_REMOVE_IMAGE, false)

        // Display search engines
        switches[KEY_INSPIRE_SHOW_ENGINE] = settingsStore.readKey(KEY_INSPIRE_SHOW_ENGINE, false)
        switches[KEY_DEFAULT_IMAGE_TEXT_OVERLAY] =
            settingsStore.readKey(KEY_DEFAULT_IMAGE_TEXT_OVERLAY, false)
        switches[KEY_INSPIRE_STRONG_COPY] = settingsStore.readKey(KEY_INSPIRE_STRONG_COPY, false)
        // Default share behavior
        val defaultShareBehavior = settingsStore.readKey(KEY_DEFAULT_SHARE_BEHAVIOR, "tokenization")
        updateDefaultShareBehaviorText(defaultShareBehavior)
        switches[KEY_EXTERNAL_DROP_ENABLED] = settingsStore.readKey(KEY_EXTERNAL_DROP_ENABLED, true)
        switches[KEY_EXTERNAL_DROP_SHOW_ENGINES] =
            settingsStore.readKey(KEY_EXTERNAL_DROP_SHOW_ENGINES, true)

        // Notification icon
        switches[KEY_SHOW_NOTIFICATION_ICON] =
            settingsStore.readKey(KEY_SHOW_NOTIFICATION_ICON, false)
        updateNotificationDisplayTimeText(
            settingsStore.readKey(
                KEY_NOTIFICATION_IMAGE_DISPLAY_SECONDS,
                DEFAULT_NOTIFICATION_IMAGE_DISPLAY_SECONDS
            )
        )

        // Pointer trail
        switches[KEY_SHOW_POINTER_TRAIL] =
            settingsStore.readKey(KEY_SHOW_POINTER_TRAIL, false)

        switches[KEY_ALWAYS_SHOW_POINTER_TOUCH_AREA] =
            settingsStore.readKey(KEY_ALWAYS_SHOW_POINTER_TOUCH_AREA, false)
        updatePointerTouchAreaOffsetText(settingsStore.readKey(KEY_POINTER_TOUCH_AREA_OFFSET_DP, 0))

        switches[KEY_ENABLE_POINTER_DRAG_MODE] =
            settingsStore.readKey(KEY_ENABLE_POINTER_DRAG_MODE, false)

        val pinPoolDisplayMode =
            settingsStore.readKey(KEY_PIN_POOL_DISPLAY_MODE, PIN_POOL_DISPLAY_MODE_RIGHT_SIDEBAR)
        updatePinPoolModeText(pinPoolDisplayMode)
        switches[KEY_PIN_POOL_LOCAL_RULE_EXTRACTION] = settingsStore.readKey(
            KEY_PIN_POOL_LOCAL_RULE_EXTRACTION,
            DEFAULT_PIN_POOL_LOCAL_RULE_EXTRACTION
        )
        val autoSaveEnabled = settingsStore.readKey(
            KEY_PIN_POOL_SAVE_SCREEN_CONTENT_IMAGES,
            true
        )
        switches[KEY_PIN_POOL_SAVE_SCREEN_CONTENT_IMAGES] = autoSaveEnabled
        switches[KEY_PIN_POOL_AUTO_COPY_IMAGE_TO_CLIPBOARD] = settingsStore.readKey(
            KEY_PIN_POOL_AUTO_COPY_IMAGE_TO_CLIPBOARD,
            DEFAULT_PIN_POOL_AUTO_COPY_IMAGE_TO_CLIPBOARD
        )
        updatePinPoolAutoDeleteText(
            settingsStore.readKey(KEY_PIN_POOL_AUTO_DELETE_DAYS, 0)
        )

        // Initialize keyboard type selection state
        val keyboardType = settingsStore.readKey(KEY_KEYBOARD_TYPE, KeyboardType.DOUBLE_KEY.name)
        updateKeyboardTypeText(KeyboardType.valueOfSafe(keyboardType))

        // Translation app
        val translationApp = settingsStore.readKey(KEY_TRANSLATION_APP, "")
        translationLabel = if (translationApp.isEmpty()) notSelection
            else appDisplayName(applicationContext, translationApp) ?: notSelection

        // Image app
        val imageApp = settingsStore.readKey(KEY_IMAGE_APP, "")
        updateImageSelectionText(
            value = if (imageApp.isEmpty()) evoImageEditorLabel
            else appDisplayName(applicationContext, imageApp) ?: evoImageEditorLabel
        )

        // Default image sharing app
        val defaultShareImageApp = settingsStore.readKey(KEY_DEFAULT_SHARE_IMAGE_APP, "")
        shareImageLabel = if (defaultShareImageApp.isEmpty()) notSelection
            else appDisplayName(applicationContext, defaultShareImageApp) ?: notSelection

        // Search app
        val searchLink = settingsStore.readKey(KEY_SEARCH_LINK, "")
        if (searchLink.isEmpty()) {
            val searchApp = settingsStore.readKey(KEY_SEARCH_APP, "")
            updateSearchSelectionText(
                value =
                if (searchApp.isEmpty()) evoSearchLabel
                else appDisplayName(applicationContext, searchApp) ?: evoSearchLabel
            )
        } else {
            updateSearchSelectionText(searchLink)
        }

    }

    @Composable
    private fun SettingsContent() {
        val listState = rememberLazyListState()
        val visibleSections = listOf(
            R.id.screenContentSwitchCard, R.id.defaultShareBehaviorCard, R.id.appSelectionCard, R.id.PointerCard, R.id.pinPoolCard, R.id.notificationCard, R.id.keyboardTypeCard
        ).filter { focusedSections.isEmpty() || it in focusedSections }
        LaunchedEffect(focusedSections) { if (focusedSections.isNotEmpty()) listState.scrollToItem(0) }
        MiuixScreen(title = getString(R.string.inspire_settings_title), onBack = { finish() }, listState = listState) {
            visibleSections.forEach { section ->
                item(key = section) {
                    val focusRequester = remember { FocusRequester() }
                    val isTarget = focusedSections.firstOrNull() == section
                    var settled by remember(isTarget) { mutableStateOf(!isTarget) }
                    val highlightAlpha by animateFloatAsState(
                        targetValue = if (settled) 1f else 0.68f,
                        animationSpec = tween(300), label = "section-focus")
                    LaunchedEffect(ready, isTarget) {
                        if (ready && isTarget) { focusRequester.requestFocus(); settled = true }
                    }
                    MiuixCard(Modifier.alpha(highlightAlpha).focusRequester(focusRequester).focusable()) {
                        when (section) {
                            R.id.screenContentSwitchCard -> {
                                SectionHeader(R.string.inspire_settings_screen_content_title, R.string.inspire_settings_screen_content_description)
                                SettingSwitch(R.string.inspire_settings_ocr_priority, R.string.inspire_settings_ocr_priority_desc, KEY_OCR_PRIORITY, true)
                                SettingSwitch(R.string.inspire_settings_default_image_text_overlay, R.string.inspire_settings_default_image_text_overlay_desc, KEY_DEFAULT_IMAGE_TEXT_OVERLAY, false)
                                SettingSwitch(R.string.inspire_settings_double_check, R.string.inspire_settings_double_check_desc, KEY_INSPIRE_DOUBLE_CHECK, false)
                                SettingSwitch(R.string.inspire_settings_selection_vibrate, R.string.inspire_settings_selection_vibrate_desc, KEY_INSPIRE_SELECTION_VIBRATE, true)
                                SettingSwitch(R.string.inspire_settings_edge_back, R.string.inspire_settings_edge_back_desc, KEY_INSPIRE_EDGE_BACK, true)
                                SettingSwitch(R.string.inspire_settings_delay_remove_image, R.string.inspire_settings_delay_remove_image_desc, KEY_INSPIRE_DELAY_REMOVE_IMAGE, false)
                                SettingSwitch(R.string.inspire_settings_show_engine, R.string.inspire_settings_show_engine_desc, KEY_INSPIRE_SHOW_ENGINE, false)
                                SettingSwitch(R.string.inspire_settings_strong_copy, R.string.inspire_settings_strong_copy_desc, KEY_INSPIRE_STRONG_COPY, false)
                            }
                            R.id.defaultShareBehaviorCard -> {
                                SectionHeader(R.string.inspire_settings_share_settings_title, R.string.inspire_settings_share_settings_description)
                                ArrowPreference(title = getString(R.string.inspire_settings_default_share_behavior_title), summary = shareBehaviorLabel, enabled = ready, onClick = { showDefaultShareBehaviorDialog() })
                                SettingSwitch(R.string.inspire_settings_external_drop, R.string.inspire_settings_external_drop_desc, KEY_EXTERNAL_DROP_ENABLED, true)
                                SettingSwitch(R.string.inspire_settings_external_drop_engines, R.string.inspire_settings_external_drop_engines_desc, KEY_EXTERNAL_DROP_SHOW_ENGINES, true)
                            }
                            R.id.appSelectionCard -> {
                                SectionHeader(R.string.inspire_settings_app_selection_title, R.string.inspire_settings_app_selection_description)
                                AppPreference(R.string.inspire_settings_translation_app_title, translationLabel, notSelection, { openAppSelector(KEY_TRANSLATION_APP) }, { clearSingleAppSelection(KEY_TRANSLATION_APP) })
                                AppPreference(R.string.inspire_settings_search_app_title, searchLabel, evoSearchLabel, { showSearchConfigDialog() }, { selectEvoSearch() })
                                AppPreference(R.string.inspire_settings_image_app_title, imageLabel, evoImageEditorLabel, { showImageAppConfigDialog() }, { selectEvoImageEditor() })
                                AppPreference(R.string.inspire_settings_default_share_image_app_title, shareImageLabel, notSelection, { openAppSelector(KEY_DEFAULT_SHARE_IMAGE_APP) }, { clearSingleAppSelection(KEY_DEFAULT_SHARE_IMAGE_APP) })
                                ArrowPreference(title = getString(R.string.inspire_settings_default_link_open_title), enabled = ready, onClick = { lifecycleScope.launch { DefaultLinkOpenManager(this@InspireSettingsActivity, lifecycleScope).showDefaultLinkOpenSettings() } })
                            }
                            R.id.PointerCard -> {
                                SectionHeader(R.string.inspire_settings_pointer_settings_title, R.string.inspire_settings_pointer_settings_description)
                                SettingSwitch(R.string.inspire_settings_show_pointer_trail, R.string.inspire_settings_show_pointer_trail_desc, KEY_SHOW_POINTER_TRAIL, false)
                                SettingSwitch(R.string.inspire_settings_always_show_pointer_touch_area, R.string.inspire_settings_always_show_pointer_touch_area_desc, KEY_ALWAYS_SHOW_POINTER_TOUCH_AREA, false)
                                ArrowPreference(title = getString(R.string.inspire_settings_pointer_touch_area_offset), summary = pointerOffsetLabel, enabled = ready, onClick = { showPointerTouchAreaOffsetBottomSheet() })
                                SettingSwitch(R.string.inspire_settings_pointer_drag_mode, R.string.inspire_settings_pointer_drag_mode_desc, KEY_ENABLE_POINTER_DRAG_MODE, false)
                            }
                            R.id.pinPoolCard -> {
                                SectionHeader(R.string.inspire_settings_pin_pool_settings_title, R.string.inspire_settings_pin_pool_settings_description)
                                ArrowPreference(title = getString(R.string.inspire_settings_pin_pool_mode_title), summary = pinPoolModeLabel, enabled = ready, onClick = { showPinPoolModeDialog() })
                                SettingSwitch(R.string.inspire_settings_pin_pool_local_rule_extraction_title, R.string.inspire_settings_pin_pool_local_rule_extraction_title_desc, KEY_PIN_POOL_LOCAL_RULE_EXTRACTION, DEFAULT_PIN_POOL_LOCAL_RULE_EXTRACTION)
                                SettingSwitch(R.string.inspire_settings_pin_pool_save_screen_content_title, R.string.inspire_settings_pin_pool_save_screen_content_title_desc, KEY_PIN_POOL_SAVE_SCREEN_CONTENT_IMAGES, true)
                                SettingSwitch(R.string.inspire_settings_pin_pool_auto_copy_clipboard_title, R.string.inspire_settings_pin_pool_auto_copy_clipboard_desc, KEY_PIN_POOL_AUTO_COPY_IMAGE_TO_CLIPBOARD, DEFAULT_PIN_POOL_AUTO_COPY_IMAGE_TO_CLIPBOARD)
                                if (switches[KEY_PIN_POOL_SAVE_SCREEN_CONTENT_IMAGES] != false) {
                                    ArrowPreference(title = getString(R.string.inspire_settings_pin_pool_auto_delete_title), summary = autoDeleteLabel, enabled = ready, onClick = { showPinPoolAutoDeleteBottomSheet() })
                                }
                            }
                            R.id.notificationCard -> {
                                SectionHeader(R.string.inspire_settings_notification_settings_title, R.string.inspire_settings_notification_settings_description)
                                SettingSwitch(R.string.inspire_settings_notification_icon, R.string.inspire_settings_notification_icon_desc, KEY_SHOW_NOTIFICATION_ICON, false)
                                Text(getString(R.string.inspire_settings_notification_behavior),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    fontSize = 12.sp, lineHeight = 16.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                ArrowPreference(title = getString(R.string.inspire_settings_notification_whitelist), enabled = ready, onClick = { showNotificationAppList(true) })
                                ArrowPreference(title = getString(R.string.inspire_settings_notification_blacklist), enabled = ready, onClick = { showNotificationAppList(false) })
                                ArrowPreference(title = getString(R.string.inspire_settings_notification_display_time), summary = notificationTimeLabel, enabled = ready, onClick = { showNotificationDisplayTimeBottomSheet() })
                                ArrowPreference(title = getString(R.string.inspire_settings_notification_permission), enabled = ready, onClick = { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) })
                            }
                            R.id.keyboardTypeCard -> {
                                SectionHeader(R.string.inspire_settings_keyboard_settings_title, R.string.inspire_settings_keyboard_settings_description)
                                ArrowPreference(title = getString(R.string.inspire_settings_keyboard_type_title), summary = keyboardLabel, enabled = ready, onClick = { showKeyboardTypeDialog() })
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
            Text(getString(title), fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
            Text(getString(summary), modifier = Modifier.padding(top = 4.dp), fontSize = 12.sp,
                lineHeight = 16.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }

    @Composable
    private fun SettingSwitch(title: Int, summary: Int, key: String, default: Boolean) {
        SwitchPreference(title = getString(title), summary = getString(summary),
            checked = switches[key] ?: default, enabled = ready, onCheckedChange = { checked ->
                switches[key] = checked
                lifecycleScope.launch { settingsStore.saveKey(key, checked) }
            })
    }

    @Composable
    private fun AppPreference(title: Int, value: String, emptyValue: String, onSelect: () -> Unit, onClear: () -> Unit) {
        ArrowPreference(title = getString(title), summary = value, enabled = ready, onClick = onSelect)
        if (value.isNotEmpty() && value != emptyValue) {
            ArrowPreference(title = getString(R.string.inspire_settings_clear), summary = getString(title),
                enabled = ready, onClick = onClear)
        }
    }

    private fun showNotificationAppList(whitelist: Boolean) {
        if (whitelist) {
            BlackListSelectAPPBottomSheet(this, lifecycleScope,
                object : SelectAPPCallBack { override fun selectCompleted(model: APPModel) {} },
                blackListFlag = APPModel.FLAG_NOTIFICATION_APPS_ENABLE,
                disableIcon = ContextCompat.getDrawable(applicationContext, R.drawable.baseline_check_24)).show()
        } else {
            BlackListSelectAPPBottomSheet(this, lifecycleScope,
                object : SelectAPPCallBack { override fun selectCompleted(model: APPModel) {} },
                blackListFlag = APPModel.FLAG_NOTIFICATION_ICON_DISABLE).show()
        }
    }

    private fun clearSingleAppSelection(key: String) {
        lifecycleScope.launch {
            if (key == KEY_TRANSLATION_APP) translationLabel = notSelection else shareImageLabel = notSelection
            settingsStore.saveKey(key, "")
            ToastHelper.showInUIThread(applicationContext, clearSelection)
        }
    }

    private fun openAppSelector(key: String) {
        DefaultSelectAPPBottomSheet(this, lifecycleScope, object : SelectAPPCallBack {
            override fun selectCompleted(model: APPModel) {
                if (key == KEY_TRANSLATION_APP) translationLabel = model.name else shareImageLabel = model.name
                lifecycleScope.launch { settingsStore.saveKey(key, model.pkgName) }
            }
        }).show()
    }

    private fun showSelectionSheet(
        title: Int,
        options: Array<String>,
        checkedItem: Int? = null,
        onSelect: (Int) -> Unit,
    ) {
        showMiuixSheet(this) { dismiss ->
            Column(Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
                Text(getString(title), modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
                LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 500.dp)) {
                    itemsIndexed(options.toList()) { index, option ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                            .clickable {
                                dismiss()
                                onSelect(index)
                            }
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            Text(option, modifier = Modifier.weight(1f), fontSize = 16.sp, lineHeight = 22.sp)
                            if (checkedItem != null) {
                                RadioButton(selected = index == checkedItem, onClick = null)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun showSearchConfigDialog() {
        val options = arrayOf(
            getString(R.string.inspire_settings_use_evo_search),
            getString(R.string.inspire_settings_select_app),
            getString(R.string.inspire_settings_custom_link)
        )
        showSelectionSheet(R.string.inspire_settings_search_app_title, options) { which ->
            when (which) {
                0 -> selectEvoSearch()
                1 -> openSearchAppSelector()
                2 -> showSearchLinkEditor()
            }
        }
    }

    private fun showImageAppConfigDialog() {
        val options = arrayOf(
            getString(R.string.inspire_settings_use_evo_image_editor),
            getString(R.string.inspire_settings_select_app)
        )
        showSelectionSheet(R.string.inspire_settings_image_app_title, options) { which ->
            when (which) {
                0 -> selectEvoImageEditor()
                1 -> openImageAppSelector()
            }
        }
    }

    private fun selectEvoImageEditor() {
        lifecycleScope.launch {
            updateImageSelectionText(evoImageEditorLabel)
            settingsStore.saveKey(KEY_IMAGE_APP, "")
        }
    }

    private fun openImageAppSelector() {
        DefaultSelectAPPBottomSheet(this, lifecycleScope, object : SelectAPPCallBack {
            override fun selectCompleted(model: APPModel) {
                updateImageSelectionText(model.name)
                lifecycleScope.launch {
                    settingsStore.saveKey(KEY_IMAGE_APP, model.pkgName)
                }
            }
        }).show()
    }

    private fun selectEvoSearch() {
        lifecycleScope.launch {
            updateSearchSelectionText(evoSearchLabel)
            settingsStore.saveKey(KEY_SEARCH_LINK, "")
            settingsStore.saveKey(KEY_SEARCH_APP, "")
        }
    }

    private fun openSearchAppSelector() {
        DefaultSelectAPPBottomSheet(this, lifecycleScope, object : SelectAPPCallBack {
            override fun selectCompleted(model: APPModel) {
                updateSearchSelectionText(model.name)
                lifecycleScope.launch {
                    settingsStore.saveKey(KEY_SEARCH_APP, model.pkgName)
                    settingsStore.saveKey(KEY_SEARCH_LINK, "")
                }
            }
        }).show()
    }

    private fun showSearchLinkEditor() {
        lifecycleScope.launch {
            val searchLink = settingsStore.readKey(KEY_SEARCH_LINK, "")
            showEditTextBottomSheet(
                activity = this@InspireSettingsActivity,
                coroutineScope = lifecycleScope,
                title = getString(R.string.inspire_settings_enter_link),
                hint = getString(R.string.inspire_settings_enter_link),
                defaultText = searchLink,
                onConfirm = { inputText ->
                    if (inputText.isNotEmpty()) {
                        updateSearchSelectionText(inputText)
                        lifecycleScope.launch {
                            settingsStore.saveKey(KEY_SEARCH_LINK, inputText)
                            settingsStore.saveKey(KEY_SEARCH_APP, "")
                        }
                    }
                }
            )
        }
    }



    private fun updateDefaultShareBehaviorText(value: String) {
        shareBehaviorLabel = when (value) {
            "pinpool" -> getString(R.string.inspire_settings_share_to_pinpool)
            else -> getString(R.string.inspire_settings_share_to_tokenization)
        }
    }

    private fun updateKeyboardTypeText(type: KeyboardType) {
        keyboardLabel = when (type) {
            KeyboardType.T9 -> getString(R.string.inspire_settings_keyboard_type_t9)
            KeyboardType.DOUBLE_KEY, KeyboardType.FULL_KEY ->
                getString(R.string.inspire_settings_keyboard_type_double_key)
        }
    }

    private fun updatePinPoolModeText(value: String) {
        pinPoolModeLabel = when (value) {
            PIN_POOL_DISPLAY_MODE_FULLSCREEN -> getString(R.string.inspire_settings_pin_pool_mode_fullscreen)
            PIN_POOL_DISPLAY_MODE_LEFT_SIDEBAR -> getString(R.string.inspire_settings_pin_pool_mode_left_sidebar)
            PIN_POOL_DISPLAY_MODE_FOLLOW_TRIGGER -> getString(R.string.inspire_settings_pin_pool_mode_follow_trigger)
            else -> getString(R.string.inspire_settings_pin_pool_mode_right_sidebar)
        }
    }

    private fun showPinPoolModeDialog() {
        val options = arrayOf(
            getString(R.string.inspire_settings_pin_pool_mode_fullscreen),
            getString(R.string.inspire_settings_pin_pool_mode_left_sidebar),
            getString(R.string.inspire_settings_pin_pool_mode_right_sidebar),
            getString(R.string.inspire_settings_pin_pool_mode_follow_trigger),
        )
        lifecycleScope.launch {
            val current = settingsStore.readKey(
                KEY_PIN_POOL_DISPLAY_MODE,
                PIN_POOL_DISPLAY_MODE_RIGHT_SIDEBAR
            )
            val checkedItem = when (current) {
                PIN_POOL_DISPLAY_MODE_FULLSCREEN -> 0
                PIN_POOL_DISPLAY_MODE_LEFT_SIDEBAR -> 1
                PIN_POOL_DISPLAY_MODE_FOLLOW_TRIGGER -> 3
                else -> 2
            }
            showSelectionSheet(R.string.inspire_settings_pin_pool_mode_title, options, checkedItem) { which ->
                val value = when (which) {
                    0 -> PIN_POOL_DISPLAY_MODE_FULLSCREEN
                    1 -> PIN_POOL_DISPLAY_MODE_LEFT_SIDEBAR
                    3 -> PIN_POOL_DISPLAY_MODE_FOLLOW_TRIGGER
                    else -> PIN_POOL_DISPLAY_MODE_RIGHT_SIDEBAR
                }
                lifecycleScope.launch {
                    settingsStore.saveKey(KEY_PIN_POOL_DISPLAY_MODE, value)
                    updatePinPoolModeText(value)
                }
            }
        }
    }

    private fun showDefaultShareBehaviorDialog() {
        val options = arrayOf(
            getString(R.string.inspire_settings_share_to_tokenization),
            getString(R.string.inspire_settings_share_to_pinpool)
        )
        lifecycleScope.launch {
            val current = settingsStore.readKey(KEY_DEFAULT_SHARE_BEHAVIOR, "tokenization")
            val checkedItem = if (current == "pinpool") 1 else 0
            showSelectionSheet(R.string.inspire_settings_default_share_behavior_title, options, checkedItem) { which ->
                val value = if (which == 1) "pinpool" else "tokenization"
                lifecycleScope.launch {
                    settingsStore.saveKey(KEY_DEFAULT_SHARE_BEHAVIOR, value)
                    updateDefaultShareBehaviorText(value)
                }
            }
        }
    }

    private fun showKeyboardTypeDialog() {
        val options = arrayOf(
            getString(R.string.inspire_settings_keyboard_type_double_key),
            getString(R.string.inspire_settings_keyboard_type_t9)
        )
        lifecycleScope.launch {
            val current = KeyboardType.valueOfSafe(
                settingsStore.readKey(KEY_KEYBOARD_TYPE, KeyboardType.DOUBLE_KEY.name)
            )
            val checkedItem = if (current == KeyboardType.T9) 1 else 0
            showSelectionSheet(R.string.inspire_settings_keyboard_type_title, options, checkedItem) { which ->
                val value = if (which == 1) KeyboardType.T9 else KeyboardType.DOUBLE_KEY
                lifecycleScope.launch {
                    settingsStore.saveKey(KEY_KEYBOARD_TYPE, value.name)
                    updateKeyboardTypeText(value)
                    produceKeyboardEvent(KeyboardEvent(show = true, reloadKeyboard = true))
                }
            }
        }
    }



    private fun updateSearchSelectionText(value: String) { searchLabel = value }

    private fun updateImageSelectionText(value: String) { imageLabel = value }

    private fun showNotificationDisplayTimeBottomSheet() {
        lifecycleScope.launch {
            NotificationDisplayTimeBottomSheet(
                activity = this@InspireSettingsActivity,
                initialSeconds = settingsStore.readKey(
                    KEY_NOTIFICATION_IMAGE_DISPLAY_SECONDS,
                    DEFAULT_NOTIFICATION_IMAGE_DISPLAY_SECONDS
                )
            ) { selectedSeconds ->
                lifecycleScope.launch {
                    settingsStore.saveKey(KEY_NOTIFICATION_IMAGE_DISPLAY_SECONDS, selectedSeconds)
                    updateNotificationDisplayTimeText(selectedSeconds)
                }
            }.show()
        }
    }

    private fun updateNotificationDisplayTimeText(seconds: Int) {
        notificationTimeLabel = formatNotificationDisplayTime(seconds)
    }

    private fun showPointerTouchAreaOffsetBottomSheet() {
        lifecycleScope.launch {
            PointerTouchAreaOffsetBottomSheet(
                activity = this@InspireSettingsActivity,
                initialOffsetDp = settingsStore.readKey(KEY_POINTER_TOUCH_AREA_OFFSET_DP, 0)
            ) { selectedOffsetDp ->
                lifecycleScope.launch {
                    settingsStore.saveKey(KEY_POINTER_TOUCH_AREA_OFFSET_DP, selectedOffsetDp)
                    updatePointerTouchAreaOffsetText(selectedOffsetDp)
                }
            }.show()
        }
    }

    private fun updatePointerTouchAreaOffsetText(offsetDp: Int) {
        pointerOffsetLabel = getString(
            R.string.inspire_settings_pointer_touch_area_offset_dp,
            offsetDp.coerceIn(-100, 100)
        )
    }



    private fun showPinPoolAutoDeleteBottomSheet() {
        lifecycleScope.launch {
            NotificationDisplayTimeBottomSheet(
                activity = this@InspireSettingsActivity,
                initialSeconds = settingsStore.readKey(KEY_PIN_POOL_AUTO_DELETE_DAYS, 0),
                titleRes = R.string.inspire_settings_pin_pool_auto_delete_title,
                maxValue = PIN_POOL_AUTO_DELETE_DAYS_MAX,
                persistLabelRes = R.string.inspire_settings_pin_pool_auto_delete_off_short,
                valueFormatRes = R.string.inspire_settings_pin_pool_auto_delete_days,
            ) { selectedDays ->
                lifecycleScope.launch {
                    settingsStore.saveKey(KEY_PIN_POOL_AUTO_DELETE_DAYS, selectedDays)
                    updatePinPoolAutoDeleteText(selectedDays)
                }
            }.show()
        }
    }

    private fun updatePinPoolAutoDeleteText(days: Int) {
        autoDeleteLabel = formatPinPoolAutoDelete(days)
    }

    private fun formatPinPoolAutoDelete(days: Int): String {
        return if (days <= 0) {
            getString(R.string.inspire_settings_pin_pool_auto_delete_off)
        } else {
            getString(R.string.inspire_settings_pin_pool_auto_delete_days, days)
        }
    }

    private fun formatNotificationDisplayTime(seconds: Int): String {
        return if (seconds <= 0) {
            getString(R.string.inspire_settings_notification_display_time_persist)
        } else {
            getString(R.string.inspire_settings_notification_display_time_seconds, seconds)
        }
    }
}
