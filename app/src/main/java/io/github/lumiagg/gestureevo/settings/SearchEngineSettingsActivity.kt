package io.github.lumiagg.gestureevo.settings

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Job
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text as M3Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.search.ContactSearchHelper
import io.github.lumiagg.gestureevo.search.QuickSearchActivity
import io.github.lumiagg.gestureevo.search.config.EngineType
import io.github.lumiagg.gestureevo.search.config.SearchEngine
import io.github.lumiagg.gestureevo.search.config.SearchEngineDataManager
import io.github.lumiagg.gestureevo.ui.compose.MiuixEvoTheme
import io.github.lumiagg.gestureevo.ui.miuix.MiuixCard
import io.github.lumiagg.gestureevo.ui.miuix.MiuixScreen
import io.github.lumiagg.gestureevo.utils.readKey
import io.github.lumiagg.gestureevo.utils.saveKey
import io.github.lumiagg.gestureevo.utils.settingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

class SearchEngineSettingsActivity : AppCompatActivity() {

    companion object {
        const val KEY_ENGINE_SHOW_LABEL = "engine_show_label"
        const val KEY_ROW_COUNT = "engine_row_count"
        const val KEY_COLUMN_COUNT = "engine_column_count"
        const val KEY_BOTTOM_INPUT_LAYOUT = "search_bottom_input_layout"
        const val KEY_RESULT_DOUBLE_COLUMN = "search_result_double_column"
        const val KEY_KEYBOARD_FOLLOW_ANIMATION = "search_keyboard_follow_animation"
        const val KEY_SHOW_IMAGE_BADGE = "search_show_image_badge"
        const val KEY_SHOW_CLIPBOARD_CONTENT = "search_show_clipboard_content"
        const val KEY_SEARCH_HISTORY = "search_history"
        const val KEY_SEARCH_CONTACT = "search_contact"
        const val KEY_SEARCH_SETTINGS_SHORTCUTS = "search_settings_shortcuts"
        const val KEY_ENTER_FIRST_RESULT = "search_enter_first_result"
        const val KEY_KEEP_LAST_SEARCH_RESULT = "search_keep_last_search_result"
        const val KEY_DEFAULT_SEARCH_PAGE = "default_search_page"
        const val MIN_ROW_COUNT = 1
        const val MAX_ROW_COUNT = 5
        const val MIN_COLUMN_COUNT = 2
        const val MAX_COLUMN_COUNT = 8
    }

    private var focusedSections by mutableStateOf<Set<Int>>(emptySet())
    private var focusRequestVersion by mutableIntStateOf(0)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readFocusIntent(intent)
    }

    /** Keep the historical XML anchors accepted by settings search and home shortcuts. */
    private fun readFocusIntent(source: Intent) {
        val ids = buildList {
            source.getIntExtra(SettingsSearchNavigator.EXTRA_TARGET_VIEW_ID, View.NO_ID)
                .takeIf { it != View.NO_ID }?.let(::add)
            source.getIntArrayExtra(SettingsSearchNavigator.EXTRA_TARGET_VIEW_IDS)?.let { addAll(it.toList()) }
        }
        focusedSections = ids.mapNotNull { id ->
            when (id) {
                R.id.engineListCard, R.id.engine_recycler, R.id.empty_view, R.id.progress_bar,
                R.id.fab_add, R.id.fab_preview_sort -> R.id.engineListCard
                R.id.displayCard, R.id.rowCountRow, R.id.rowCountMinus, R.id.rowCountValue,
                R.id.rowCountPlus, R.id.columnCountRow, R.id.columnCountMinus,
                R.id.columnCountValue, R.id.columnCountPlus, R.id.showLabelSwitch,
                R.id.bottomLayoutSwitch, R.id.doubleColumnSwitch, R.id.keyboardAnimationSwitch,
                R.id.imageBadgeSwitch -> R.id.displayCard
                R.id.functionCard, R.id.clipboardContentSwitch, R.id.historySwitch,
                R.id.contactSwitch, R.id.settingsShortcutsSwitch, R.id.enterFirstSwitch,
                R.id.keepLastSwitch -> R.id.functionCard
                R.id.startupCard, R.id.defaultSearchPageSwitch -> R.id.startupCard
                R.id.transferCard, R.id.importEnginesRow, R.id.exportEnginesRow,
                R.id.hiddenResultsCard, R.id.hiddenResultsRow -> R.id.transferCard
                else -> null
            }
        }.toSet()
        focusRequestVersion++
    }

    /** 通讯录权限结果：授予则落库并同步，拒绝则回退开关。 */
    private var onContactPermissionResult: ((Boolean) -> Unit)? = null

    private val contactPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        onContactPermissionResult?.invoke(granted)
        onContactPermissionResult = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readFocusIntent(intent) // Filter synchronously, before the first composition or any IO.
        setContent {
            MiuixEvoTheme {
                SettingsScreen(
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                    onRequestContacts = { callback ->
                        onContactPermissionResult = callback
                        contactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                    },
                    onEditEngine = { engine -> editSearchEngine(engine) },
                    onAddEngine = {
                        startActivity(Intent(this, SearchEngineAddActivity::class.java))
                    },
                )
            }
        }
    }

    private fun editSearchEngine(engine: SearchEngine) {
        startActivity(
            Intent(this, SearchEngineAddActivity::class.java).apply {
                putExtra("editMode", true)
                putExtra("engine", engine.toJson())
            }
        )
    }

    @Composable
    private fun SettingsScreen(
        onBack: () -> Unit,
        onRequestContacts: ((Boolean) -> Unit) -> Unit,
        onEditEngine: (SearchEngine) -> Unit,
        onAddEngine: () -> Unit,
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val listState = rememberLazyListState()
        val visibleSections = listOf(R.id.engineListCard, R.id.displayCard, R.id.functionCard,
            R.id.startupCard, R.id.transferCard)
            .filter { focusedSections.isEmpty() || it in focusedSections }
        LaunchedEffect(focusRequestVersion) {
            focusedSections.firstOrNull()?.let { target ->
                val index = visibleSections.indexOf(target)
                if (index >= 0) listState.scrollToItem(index)
            }
        }

        var rowCount by remember { mutableIntStateOf(1) }
        var columnCount by remember { mutableIntStateOf(5) }
        var showLabel by remember { mutableStateOf(true) }
        var bottomLayout by remember { mutableStateOf(false) }
        var doubleColumn by remember { mutableStateOf(false) }
        var keyboardFollow by remember { mutableStateOf(false) }
        var imageBadge by remember { mutableStateOf(true) }
        var clipboardContent by remember { mutableStateOf(true) }
        var searchHistory by remember { mutableStateOf(true) }
        var settingsShortcuts by remember { mutableStateOf(false) }
        var enterFirst by remember { mutableStateOf(false) }
        var keepLast by remember { mutableStateOf(true) }
        var searchContact by remember { mutableStateOf(false) }
        var defaultSearchPage by remember { mutableStateOf(false) }
        var showDefaultPageDialog by remember { mutableStateOf(false) }
        var showImportDialog by remember { mutableStateOf(false) }
        var importJson by remember { mutableStateOf("") }
        var pendingContactEnable by remember { mutableStateOf(false) }
        var actionEngineTag by remember { mutableStateOf<String?>(null) }
        var actionMenuExpanded by remember { mutableStateOf(false) }

        val engines = remember { mutableStateListOf<SearchEngine>() }
        var engineRefreshJob by remember { mutableStateOf<Job?>(null) }
        var engineWriteJob by remember { mutableStateOf<Job?>(null) }
        fun persistEngineChange(block: suspend () -> Unit) {
            engineRefreshJob?.cancel()
            val previousWrite = engineWriteJob
            engineWriteJob = scope.launch {
                previousWrite?.join()
                block()
            }
        }

        // Only refresh engines on resume: re-reading preferences here would undo in-flight edits.
        // A single cancellable reader cannot apply an older snapshot after a newer resume/local edit.
        DisposableEffect(lifecycle, scope) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        engineRefreshJob?.cancel()
                        engineRefreshJob = scope.launch {
                            engineWriteJob?.join()
                            val refreshed = withContext(Dispatchers.IO) {
                                SearchEngineDataManager.readSearchEngineList(context.applicationContext)
                            }
                            engines.clear()
                            engines.addAll(refreshed)
                        }
                    }
                    Lifecycle.Event.ON_PAUSE -> engineRefreshJob?.cancel()
                    else -> Unit
                }
            }
            lifecycle.addObserver(observer) // Also catches up if composition starts already resumed.
            onDispose {
                lifecycle.removeObserver(observer)
                engineRefreshJob?.cancel()
            }
        }

        LaunchedEffect(Unit) {
            rowCount = context.settingsStore.readKey(KEY_ROW_COUNT, 1)
            columnCount = context.settingsStore.readKey(KEY_COLUMN_COUNT, 5)
            showLabel = context.settingsStore.readKey(KEY_ENGINE_SHOW_LABEL, true)
            bottomLayout = context.settingsStore.readKey(KEY_BOTTOM_INPUT_LAYOUT, false)
            doubleColumn = context.settingsStore.readKey(KEY_RESULT_DOUBLE_COLUMN, false)
            keyboardFollow = context.settingsStore.readKey(KEY_KEYBOARD_FOLLOW_ANIMATION, false)
            imageBadge = context.settingsStore.readKey(KEY_SHOW_IMAGE_BADGE, true)
            clipboardContent = context.settingsStore.readKey(KEY_SHOW_CLIPBOARD_CONTENT, true)
            searchHistory = context.settingsStore.readKey(KEY_SEARCH_HISTORY, true)
            settingsShortcuts = context.settingsStore.readKey(KEY_SEARCH_SETTINGS_SHORTCUTS, false)
            enterFirst = context.settingsStore.readKey(KEY_ENTER_FIRST_RESULT, false)
            keepLast = context.settingsStore.readKey(KEY_KEEP_LAST_SEARCH_RESULT, true)
            searchContact = context.settingsStore.readKey(KEY_SEARCH_CONTACT, false)
            defaultSearchPage = context.settingsStore.readKey(KEY_DEFAULT_SEARCH_PAGE, false)
        }

        MiuixScreen(
            title = stringResource(R.string.search_engine_settings_title),
            onBack = onBack,
            listState = listState,
        ) {
            // ---- 引擎列表
            if (R.id.engineListCard in visibleSections) item(key = R.id.engineListCard) {
                SearchSectionCard(R.id.engineListCard) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.search_engine_settings_engine_list_title),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.search_engine_settings_engine_list_description),
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    if (engines.isEmpty()) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.search_engine_settings_empty_title),
                                fontSize = 14.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    } else {
                        engines.forEach { engine ->
                            key(engine.tag) {
                                val dragDescription = stringResource(R.string.search_engine_drag_reorder)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    // 手柄独立处理拖拽，行内容的长按只负责打开菜单。
                                    Text(
                                        text = "≡",
                                        modifier = Modifier
                                            .semantics { contentDescription = dragDescription }
                                            .pointerInput(engine.tag) {
                                                var dragDistance = 0f
                                                var orderChanged = false
                                                val threshold = 48.dp.toPx()
                                                fun finishDrag() {
                                                    dragDistance = 0f
                                                    if (orderChanged) {
                                                        orderChanged = false
                                                        val snapshot = engines.toList()
                                                        persistEngineChange {
                                                            SearchEngineDataManager.saveSearchEngineList(context, snapshot)
                                                        }
                                                    }
                                                }
                                                detectDragGestures(
                                                    onDragStart = {
                                                        engineRefreshJob?.cancel()
                                                        dragDistance = 0f
                                                        orderChanged = false
                                                        actionMenuExpanded = false
                                                    },
                                                    onDragCancel = { finishDrag() },
                                                    onDragEnd = { finishDrag() },
                                                    onDrag = { change, amount ->
                                                        change.consume()
                                                        dragDistance += amount.y
                                                        val currentIndex = engines.indexOfFirst { it.tag == engine.tag }
                                                        if (currentIndex >= 0 && kotlin.math.abs(dragDistance) >= threshold) {
                                                            val targetIndex = currentIndex + if (dragDistance > 0f) 1 else -1
                                                            if (targetIndex in engines.indices) {
                                                                val currentEngine = engines.removeAt(currentIndex)
                                                                engines.add(targetIndex, currentEngine)
                                                                orderChanged = true
                                                            }
                                                            dragDistance = 0f
                                                        }
                                                    },
                                                )
                                            }
                                            .padding(16.dp),
                                        fontSize = 24.sp,
                                    )
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .combinedClickable(
                                                onClick = { onEditEngine(engine) },
                                                onLongClick = {
                                                    actionEngineTag = engine.tag
                                                    actionMenuExpanded = true
                                                },
                                            ),
                                    ) {
                                        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 8.dp)) {
                                            Text(text = engine.name.orEmpty(), fontSize = 16.sp)
                                            Text(
                                                text = engineTypeLabel(engine.engineType),
                                                fontSize = 12.sp,
                                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = actionMenuExpanded && actionEngineTag == engine.tag,
                                            onDismissRequest = { actionMenuExpanded = false },
                                        ) {
                                            DropdownMenuItem(
                                                text = { M3Text(stringResource(R.string.search_engine_option_set_default)) },
                                                onClick = {
                                                    actionMenuExpanded = false
                                                    if (engines.any { it.tag == engine.tag }) {
                                                        persistEngineChange { SearchEngineDataManager.setDefaultEngineTag(context, engine.tag) }
                                                    }
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = {
                                                    M3Text(stringResource(
                                                        if (engine.showInSearchEngineList) R.string.search_engine_option_hide
                                                        else R.string.search_engine_option_show
                                                    ))
                                                },
                                                onClick = {
                                                    actionMenuExpanded = false
                                                    val currentIndex = engines.indexOfFirst { it.tag == engine.tag }
                                                    if (currentIndex >= 0) {
                                                        val currentEngine = engines.removeAt(currentIndex)
                                                        currentEngine.showInSearchEngineList = !currentEngine.showInSearchEngineList
                                                        val targetIndex = if (currentEngine.showInSearchEngineList) {
                                                            engines.indexOfFirst { !it.showInSearchEngineList }
                                                                .takeIf { it >= 0 } ?: engines.size
                                                        } else engines.size
                                                        engines.add(targetIndex, currentEngine)
                                                        val snapshot = engines.toList()
                                                        persistEngineChange { SearchEngineDataManager.saveSearchEngineList(context, snapshot) }
                                                    }
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = { M3Text(stringResource(R.string.search_engine_option_delete)) },
                                                onClick = {
                                                    actionMenuExpanded = false
                                                    val currentIndex = engines.indexOfFirst { it.tag == engine.tag }
                                                    if (currentIndex >= 0) {
                                                        engines.removeAt(currentIndex)
                                                        persistEngineChange {
                                                            SearchEngineDataManager.removeSearchEngine(context, engine.tag)
                                                        }
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    ArrowPreference(
                        title = stringResource(R.string.search_engine_settings_add_engine),
                        onClick = onAddEngine,
                    )
                    ArrowPreference(
                        title = stringResource(R.string.search_engine_settings_preview_mode),
                        onClick = { QuickSearchActivity.start(this@SearchEngineSettingsActivity, previewSortMode = true) },
                    )
                }
            }

            // ---- 显示设置
            if (R.id.displayCard in visibleSections) item(key = R.id.displayCard) {
                SearchSectionCard(R.id.displayCard) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.search_engine_settings_display_title),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(12.dp))
                        StepperRow(
                            label = stringResource(R.string.home_title_row_count),
                            value = rowCount,
                            onMinus = {
                                val next = (rowCount - 1).coerceIn(MIN_ROW_COUNT, MAX_ROW_COUNT)
                                if (next != rowCount) {
                                    rowCount = next
                                    scope.launch { context.settingsStore.saveKey(KEY_ROW_COUNT, next) }
                                }
                            },
                            onPlus = {
                                val next = (rowCount + 1).coerceIn(MIN_ROW_COUNT, MAX_ROW_COUNT)
                                if (next != rowCount) {
                                    rowCount = next
                                    scope.launch { context.settingsStore.saveKey(KEY_ROW_COUNT, next) }
                                }
                            },
                        )
                        StepperRow(
                            label = stringResource(R.string.home_title_column_count),
                            value = columnCount,
                            onMinus = {
                                val next = (columnCount - 1).coerceIn(MIN_COLUMN_COUNT, MAX_COLUMN_COUNT)
                                if (next != columnCount) {
                                    columnCount = next
                                    scope.launch { context.settingsStore.saveKey(KEY_COLUMN_COUNT, next) }
                                }
                            },
                            onPlus = {
                                val next = (columnCount + 1).coerceIn(MIN_COLUMN_COUNT, MAX_COLUMN_COUNT)
                                if (next != columnCount) {
                                    columnCount = next
                                    scope.launch { context.settingsStore.saveKey(KEY_COLUMN_COUNT, next) }
                                }
                            },
                        )
                    }
                    SwitchPreference(
                        title = stringResource(R.string.search_engine_settings_show_labels),
                        summary = stringResource(R.string.search_engine_settings_show_labels_desc),
                        checked = showLabel,
                        onCheckedChange = {
                            showLabel = it
                            scope.launch { context.settingsStore.saveKey(KEY_ENGINE_SHOW_LABEL, it) }
                        },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.search_engine_settings_bottom_layout),
                        summary = stringResource(R.string.search_engine_settings_bottom_layout_desc),
                        checked = bottomLayout,
                        onCheckedChange = {
                            bottomLayout = it
                            scope.launch { context.settingsStore.saveKey(KEY_BOTTOM_INPUT_LAYOUT, it) }
                        },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.search_engine_settings_result_double_column),
                        summary = stringResource(R.string.search_engine_settings_result_double_column_desc),
                        checked = doubleColumn,
                        onCheckedChange = {
                            doubleColumn = it
                            scope.launch { context.settingsStore.saveKey(KEY_RESULT_DOUBLE_COLUMN, it) }
                        },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.search_engine_settings_keyboard_follow_animation),
                        summary = stringResource(R.string.search_engine_settings_keyboard_follow_animation_desc),
                        checked = keyboardFollow,
                        onCheckedChange = {
                            keyboardFollow = it
                            scope.launch { context.settingsStore.saveKey(KEY_KEYBOARD_FOLLOW_ANIMATION, it) }
                        },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.search_engine_settings_image_badge),
                        summary = stringResource(R.string.search_engine_settings_image_badge_desc),
                        checked = imageBadge,
                        onCheckedChange = {
                            imageBadge = it
                            scope.launch { context.settingsStore.saveKey(KEY_SHOW_IMAGE_BADGE, it) }
                        },
                    )
                }
            }

            // ---- 功能设置
            if (R.id.functionCard in visibleSections) item(key = R.id.functionCard) {
                SearchSectionCard(R.id.functionCard) {
                    SwitchPreference(
                        title = stringResource(R.string.search_engine_settings_show_clipboard_content),
                        summary = stringResource(R.string.search_engine_settings_show_clipboard_content_desc),
                        checked = clipboardContent,
                        onCheckedChange = {
                            clipboardContent = it
                            scope.launch { context.settingsStore.saveKey(KEY_SHOW_CLIPBOARD_CONTENT, it) }
                        },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.search_engine_settings_search_history),
                        summary = stringResource(R.string.search_engine_settings_search_history_desc),
                        checked = searchHistory,
                        onCheckedChange = {
                            searchHistory = it
                            scope.launch { context.settingsStore.saveKey(KEY_SEARCH_HISTORY, it) }
                        },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.search_engine_settings_search_settings),
                        summary = stringResource(R.string.search_engine_settings_search_settings_desc),
                        checked = settingsShortcuts,
                        onCheckedChange = {
                            settingsShortcuts = it
                            scope.launch { context.settingsStore.saveKey(KEY_SEARCH_SETTINGS_SHORTCUTS, it) }
                        },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.search_engine_settings_enter_first_result),
                        summary = stringResource(R.string.search_engine_settings_enter_first_result_desc),
                        checked = enterFirst,
                        onCheckedChange = {
                            enterFirst = it
                            scope.launch { context.settingsStore.saveKey(KEY_ENTER_FIRST_RESULT, it) }
                        },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.search_engine_settings_keep_last_result),
                        summary = stringResource(R.string.search_engine_settings_keep_last_result_desc),
                        checked = keepLast,
                        onCheckedChange = {
                            keepLast = it
                            scope.launch { context.settingsStore.saveKey(KEY_KEEP_LAST_SEARCH_RESULT, it) }
                        },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.search_engine_settings_search_contacts),
                        summary = stringResource(R.string.search_engine_settings_search_contacts_desc),
                        checked = searchContact,
                        onCheckedChange = { checked ->
                            if (checked && !ContactSearchHelper.hasContactPermission(context)) {
                                pendingContactEnable = true
                                onRequestContacts { granted ->
                                    if (granted) {
                                        searchContact = true
                                        scope.launch {
                                            context.settingsStore.saveKey(KEY_SEARCH_CONTACT, true)
                                            ContactSearchHelper.syncContactsToDatabase(
                                                context.applicationContext,
                                                force = true,
                                            )
                                        }
                                    } else {
                                        searchContact = false
                                        Toast.makeText(
                                            context,
                                            R.string.search_engine_settings_search_contacts,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                }
                            } else {
                                searchContact = checked
                                scope.launch {
                                    context.settingsStore.saveKey(KEY_SEARCH_CONTACT, checked)
                                    if (checked) {
                                        ContactSearchHelper.syncContactsToDatabase(
                                            context.applicationContext,
                                            force = true,
                                        )
                                    }
                                }
                            }
                        },
                    )
                }
            }

            // ---- 启动设置
            if (R.id.startupCard in visibleSections) item(key = R.id.startupCard) {
                SearchSectionCard(R.id.startupCard) {
                    SwitchPreference(
                        title = stringResource(R.string.search_engine_settings_default_search_page),
                        summary = stringResource(R.string.search_engine_settings_default_search_page_desc),
                        checked = defaultSearchPage,
                        onCheckedChange = { checked ->
                            if (!checked) {
                                defaultSearchPage = false
                                scope.launch {
                                    context.settingsStore.saveKey(KEY_DEFAULT_SEARCH_PAGE, false)
                                }
                            } else {
                                showDefaultPageDialog = true
                            }
                        },
                    )
                }
            }

            // ---- 导入导出与隐藏结果
            if (R.id.transferCard in visibleSections) item(key = R.id.transferCard) {
                SearchSectionCard(R.id.transferCard) {
                    ArrowPreference(
                        title = stringResource(R.string.search_engine_settings_import),
                        onClick = { showImportDialog = true },
                    )
                    ArrowPreference(
                        title = stringResource(R.string.search_engine_settings_export),
                        onClick = { exportSearchEngines() },
                    )
                    ArrowPreference(
                        title = stringResource(R.string.hidden_search_results_title),
                        summary = stringResource(R.string.hidden_search_results_summary),
                        onClick = {
                            startActivity(Intent(this@SearchEngineSettingsActivity, HiddenSearchResultsActivity::class.java))
                        },
                    )
                }
            }
        }

        if (showDefaultPageDialog) {
            AlertDialog(
                onDismissRequest = { showDefaultPageDialog = false; defaultSearchPage = false },
                title = {
                    M3Text(stringResource(R.string.search_engine_settings_default_search_page_dialog_title))
                },
                text = {
                    M3Text(stringResource(R.string.search_engine_settings_default_search_page_dialog_message))
                },
                confirmButton = {
                    TextButton(onClick = {
                        showDefaultPageDialog = false
                        defaultSearchPage = true
                        scope.launch {
                            context.settingsStore.saveKey(KEY_DEFAULT_SEARCH_PAGE, true)
                        }
                    }) { M3Text(stringResource(android.R.string.ok)) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showDefaultPageDialog = false
                        defaultSearchPage = false
                    }) { M3Text(stringResource(android.R.string.cancel)) }
                },
            )
        }

        if (showImportDialog) {
            AlertDialog(
                onDismissRequest = { showImportDialog = false },
                title = { M3Text(stringResource(R.string.search_engine_import_title)) },
                text = {
                    Column {
                        M3Text(stringResource(R.string.search_engine_import_message))
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = importJson,
                            onValueChange = { importJson = it },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showImportDialog = false
                        importSearchEngines(importJson)
                        importJson = ""
                    }) { M3Text(stringResource(R.string.search_engine_import_action)) }
                },
                dismissButton = {
                    TextButton(onClick = { showImportDialog = false }) {
                        M3Text(stringResource(R.string.search_engine_cancel))
                    }
                },
            )
        }
    }

    @Composable
    private fun SearchSectionCard(section: Int, content: @Composable ColumnScope.() -> Unit) {
        val focusRequester = remember { FocusRequester() }
        val isTarget = focusedSections.firstOrNull() == section
        var settled by remember(isTarget, focusRequestVersion) { mutableStateOf(!isTarget) }
        val highlightAlpha by animateFloatAsState(
            targetValue = if (settled) 1f else 0.68f,
            animationSpec = tween(300), label = "search-section-focus",
        )
        LaunchedEffect(isTarget, focusRequestVersion) {
            if (isTarget) {
                focusRequester.requestFocus()
                settled = true
            }
        }
        MiuixCard(
            modifier = Modifier.alpha(highlightAlpha).focusRequester(focusRequester).focusable(),
            content = content,
        )
    }

    @Composable
    private fun StepperRow(
        label: String,
        value: Int,
        onMinus: () -> Unit,
        onPlus: () -> Unit,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, fontSize = 16.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepButton(text = "−", onClick = onMinus)
                Text(
                    text = value.toString(),
                    fontSize = 16.sp,
                    modifier = Modifier.width(40.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                StepButton(text = "+", onClick = onPlus)
            }
        }
    }

    @Composable
    private fun StepButton(text: String, onClick: () -> Unit) {
        Column(
            modifier = Modifier
                .size(32.dp)
                .clickable(onClick = onClick),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = text, fontSize = 18.sp, color = MiuixTheme.colorScheme.primary)
        }
    }

    private fun engineTypeLabel(type: EngineType): String = when (type) {
        EngineType.DIRECT_LINK -> getString(R.string.search_engine_type_direct_link)
        EngineType.SHARE_TO_APP -> getString(R.string.search_engine_type_share)
        EngineType.SHARE_IMAGE_TO_APP -> getString(R.string.search_engine_type_share_image)
        EngineType.JUMP_TO_ACTIVITY -> getString(R.string.search_engine_type_jump_activity)
        EngineType.EXTERN_JUMP_LINK -> getString(R.string.search_engine_type_extern_jump)
    }

    private fun importSearchEngines(json: String) {
        if (json.isEmpty()) {
            Toast.makeText(this, R.string.search_engine_import_empty, Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val appContext = applicationContext
            try {
                val engines = SearchEngine.toModelList(json)
                if (engines.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            appContext,
                            R.string.search_engine_import_json_error,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    return@launch
                }
                SearchEngineDataManager.importSearchEngines(appContext, engines)
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, R.string.search_engine_import_success, Toast.LENGTH_SHORT).show()
                    recreate()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, R.string.search_engine_import_json_error, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun exportSearchEngines() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val json = SearchEngineDataManager.exportSearchEnginesJson(applicationContext)
                withContext(Dispatchers.Main) {
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(
                        android.content.ClipData.newPlainText("search_engines", json)
                    )
                    Toast.makeText(this@SearchEngineSettingsActivity, R.string.search_engine_settings_export, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SearchEngineSettingsActivity, R.string.search_engine_import_json_error, Toast.LENGTH_SHORT).show()
                    android.util.Log.e("SearchEngineSettings", "export failed", e)
                }
            }
        }
    }
}
