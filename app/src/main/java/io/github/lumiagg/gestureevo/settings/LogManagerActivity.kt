package io.github.lumiagg.gestureevo.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.diagnostics.*
import io.github.lumiagg.gestureevo.ui.compose.MiuixEvoTheme
import io.github.lumiagg.gestureevo.ui.miuix.MiuixCard
import io.github.lumiagg.gestureevo.ui.miuix.MiuixScreen
import kotlinx.coroutines.*
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import java.text.DateFormat
import java.util.Date

class LogManagerActivity : AppCompatActivity() {
    private var levelIndex by mutableStateOf(0)
    private var moduleIndex by mutableStateOf(0)
    private var timeIndex by mutableStateOf(0)
    private var query by mutableStateOf("")
    private var storageDetails by mutableStateOf("")
    private var storageSummary by mutableStateOf("")
    private var healthDetails by mutableStateOf("")
    private var healthLabel by mutableStateOf(R.string.log_health_normal)
    private var healthWarning by mutableStateOf(false)
    private var loggingEnabled by mutableStateOf(AppLogger.enabled)
    private var detailedEnabled by mutableStateOf(AppLogger.detailed)
    private var activeSession by mutableStateOf(ProblemSessions.active)
    private var captureStatus by mutableStateOf(SystemLogCapture.status.value)
    private var selectedSession by mutableStateOf<ProblemSession?>(null)
    private var records by mutableStateOf<List<LogRecord>>(emptyList())
    private var hasMore by mutableStateOf(false)
    private var querying by mutableStateOf(false)
    private var busy by mutableStateOf(false)
    private var dialog by mutableStateOf<LogDialog?>(null)
    private var traceInput by mutableStateOf("")
    private var listState: LazyListState? = null
    private var queryJob: Job? = null
    private var generation = 0
    private var fingerprint: String? = null
    private var pendingArchive: File? = null

    // Dialog is a Compose window primitive; all visible controls and surfaces are Miuix.
    private sealed interface LogDialog {
        data object Settings : LogDialog
        data object Status : LogDialog
        data object Clear : LogDialog
        data object Capture : LogDialog
        data object Trace : LogDialog
        data class Record(val record: LogRecord) : LogDialog
        data class Choices(val title: Int, val labels: List<String>, val selected: Int? = null,
                           val onSelect: (Int) -> Unit) : LogDialog
    }

    private val saveArchive = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val file = pendingArchive
        pendingArchive = null
        if (uri != null && file != null) lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    file.inputStream().use { input -> requireNotNull(contentResolver.openOutputStream(uri)).use { input.copyTo(it) } }
                }
                toast(R.string.log_saved)
            } catch (e: Exception) { showFailure(e) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        levelIndex = (savedInstanceState?.getInt("level") ?: 0).coerceIn(0, LogLevel.entries.size)
        moduleIndex = (savedInstanceState?.getInt("module") ?: 0).coerceIn(0, LogModule.entries.size)
        timeIndex = (savedInstanceState?.getInt("time") ?: 0).coerceIn(0, 2)
        query = savedInstanceState?.getString("query").orEmpty()
        pendingArchive = savedInstanceState?.getString("archive")?.let(::File)
        selectedSession = ProblemSessions.history.value.firstOrNull { it.id == savedInstanceState?.getString("session") }
        syncToggles()
        setContent { MiuixEvoTheme { LogScreen() } }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { SystemLogCapture.status.collect { captureStatus = it } }
                launch {
                    ProblemSessions.history.collect { history ->
                        selectedSession = history.firstOrNull { it.id == selectedSession?.id }
                        syncToggles()
                        reload()
                    }
                }
                reload()
                while (true) {
                    delay(5000)
                    syncToggles()
                    if (!busy && records.size <= PAGE_SIZE && listState?.canScrollBackward == false) {
                        val token = withContext(Dispatchers.IO) { sourceFingerprint() }
                        if (token != fingerprint) load(reset = true, debounce = false)
                    }
                }
            }
        }
    }

    @Composable
    private fun LogScreen() {
        val scrollState = rememberLazyListState()
        SideEffect { listState = scrollState }
        MiuixScreen(
            title = stringResource(R.string.log_manager_title),
            onBack = { onBackPressedDispatcher.onBackPressed() },
            listState = scrollState,
            actions = {
                IconButton(enabled = !busy, onClick = { chooseExport() }) {
                    Icon(painterResource(R.drawable.ic_baseline_share_24), stringResource(R.string.log_export))
                }
                IconButton(onClick = {
                    choices(R.string.log_manager_title, listOf(getString(R.string.log_settings_title),
                        getString(R.string.log_status_title), getString(R.string.log_clear))) {
                        when (it) {
                            0 -> showSettings()
                            1 -> showStatus()
                            else -> if (!busy) dialog = LogDialog.Clear
                        }
                    }
                }) {
                    Icon(painterResource(R.drawable.baseline_miscellaneous_services_24), stringResource(R.string.log_settings_title))
                }
            },
        ) {
            item {
                MiuixCard {
                    ArrowPreference(title = getString(healthLabel), summary = storageSummary,
                        onClick = { showStatus() })
                    if (healthWarning) Caption(healthDetails)
                }
            }
            item {
                MiuixCard {
                    ArrowPreference(title = getString(R.string.log_problem_title),
                        summary = getString(if (loggingEnabled) R.string.log_status_on else R.string.log_status_off),
                        onClick = { showSettings() })
                    Caption(sessionDescription())
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(text = getString(when {
                            activeSession != null -> R.string.log_problem_finish
                            !loggingEnabled -> R.string.log_problem_enable_start
                            else -> R.string.log_problem_start
                        }), modifier = Modifier.fillMaxWidth(), enabled = !busy,
                            colors = ButtonDefaults.textButtonColorsPrimary(), onClick = { recordProblem() })
                        TextButton(text = getString(R.string.log_problem_history_short),
                            modifier = Modifier.fillMaxWidth(), onClick = { showHistory() })
                        TextButton(text = getString(if (captureStatus.running) R.string.log_stop else R.string.log_system),
                            modifier = Modifier.fillMaxWidth(), onClick = { capture() })
                    }
                    if (captureStatus.result != "idle") Caption(captureDescription(captureStatus))
                }
            }
            item {
                MiuixCard {
                    Row(Modifier.fillMaxWidth().padding(16.dp)) {
                        TextField(value = query, onValueChange = { query = it; reload() },
                            label = getString(R.string.log_search_short), singleLine = true,
                            modifier = Modifier.weight(1f))
                        if (query.isNotEmpty()) IconButton(onClick = { query = ""; reload() },
                            modifier = Modifier.size(48.dp)) {
                            Icon(painterResource(R.drawable.baseline_clear_24), getString(android.R.string.cancel))
                        }
                    }
                    FilterRow(R.string.log_level, levelValues(), levelIndex) { levelIndex = it }
                    FilterRow(R.string.log_module, moduleValues(), moduleIndex) { moduleIndex = it }
                    FilterRow(R.string.log_time, resources.getStringArray(R.array.log_time_ranges).toList(), timeIndex) { timeIndex = it }
                }
            }
            item { Text(getString(R.string.log_records_title), modifier = Modifier.padding(horizontal = 4.dp),
                fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium) }
            items(records, key = { it.id }) { record -> RecordCard(record) }
            if (records.isEmpty() && !querying) item {
                MiuixCard { Caption(getString(R.string.log_empty)) }
            }
            if (hasMore) item {
                TextButton(text = getString(R.string.log_load_more), modifier = Modifier.fillMaxWidth(),
                    enabled = !busy && !querying, onClick = { load(reset = false) })
            }
        }
        LogDialogs()
    }

    @Composable
    private fun Caption(text: String) {
        Text(text, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            fontSize = 12.sp, lineHeight = 16.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }

    @Composable
    private fun FilterRow(title: Int, values: List<String>, selected: Int, update: (Int) -> Unit) {
        ArrowPreference(title = getString(title), summary = values[selected], onClick = {
            choices(title, values, selected) { update(it); reload() }
        })
    }

    @Composable
    private fun RecordCard(record: LogRecord) {
        val target = record.fields.entries.firstOrNull {
            it.key in setOf("package", "targetPackage", "target_package", "target", "component")
        }?.value
        val context = listOfNotNull(target,
            record.trace?.let { getString(R.string.log_trace_short, it.take(8)) }
                ?: record.session?.let { getString(R.string.log_session_short, it.take(8)) }).joinToString(" · ")
        MiuixCard {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${time(record.timestamp)} · ${resources.getStringArray(R.array.log_modules)[record.module.ordinal]}",
                    modifier = Modifier.weight(1f), fontSize = 12.sp, lineHeight = 16.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Text(resources.getStringArray(R.array.log_level_names)[record.level.ordinal],
                    modifier = Modifier.background(
                        when (record.level) {
                            LogLevel.ERROR -> MiuixTheme.colorScheme.errorContainer
                            LogLevel.WARN -> MiuixTheme.colorScheme.tertiaryContainer
                            else -> MiuixTheme.colorScheme.secondaryContainer
                        }, RoundedCornerShape(6.dp)
                    ).padding(horizontal = 8.dp, vertical = 2.dp),
                    color = when (record.level) {
                        LogLevel.ERROR -> MiuixTheme.colorScheme.onErrorContainer
                        LogLevel.WARN -> MiuixTheme.colorScheme.onTertiaryContainer
                        else -> MiuixTheme.colorScheme.onSurface
                    },
                    fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
            }
            ArrowPreference(title = eventTitle(record.event), summary = context.takeIf { it.isNotEmpty() },
                onClick = { dialog = LogDialog.Record(record) })
        }
    }

    @Composable
    private fun LogDialogs() {
        val current = dialog ?: return
        val title = when (current) {
            LogDialog.Settings -> R.string.log_settings_title
            LogDialog.Status -> R.string.log_status_title
            LogDialog.Clear -> R.string.log_clear
            LogDialog.Capture -> R.string.log_system
            LogDialog.Trace -> R.string.log_export_trace
            is LogDialog.Record -> R.string.log_details
            is LogDialog.Choices -> current.title
        }
        Dialog(onDismissRequest = { dialog = null }) {
            MiuixCard {
                Text(getString(title), modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp),
                    fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
                // Bound long history/detail content without introducing nested lazy lists.
                key(current) {
                    Column(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 500.dp).verticalScroll(rememberScrollState())) {
                        when (current) {
                            LogDialog.Settings -> {
                                SwitchPreference(title = getString(R.string.log_recording), checked = loggingEnabled,
                                    onCheckedChange = { AppLogger.setEnabled(it); syncToggles(); reload() })
                                SwitchPreference(title = getString(R.string.log_detailed_short), checked = detailedEnabled,
                                    enabled = loggingEnabled, onCheckedChange = { AppLogger.setDetailed(it); syncToggles() })
                                DialogText(getString(R.string.log_detailed_hint))
                                DialogText(getString(R.string.log_capture_idle))
                            }
                            LogDialog.Status -> DialogText(listOf(storageDetails, healthDetails).filter { it.isNotEmpty() }.joinToString("\n\n"))
                            LogDialog.Clear -> DialogText(getString(R.string.log_clear_confirm))
                            LogDialog.Capture -> {
                                DialogText(getString(R.string.log_system_description))
                                ArrowPreference(title = getString(R.string.log_use_shizuku), onClick = {
                                    dialog = null; SystemLogCapture.start(false)
                                })
                                ArrowPreference(title = getString(R.string.log_use_root), onClick = {
                                    dialog = null; SystemLogCapture.start(true)
                                })
                            }
                            LogDialog.Trace -> TextField(value = traceInput, onValueChange = { traceInput = it },
                                label = getString(R.string.log_trace_hint), singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp))
                            is LogDialog.Record -> SelectionContainer {
                                Text(format(current.record), modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                                    fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 20.sp)
                            }
                            is LogDialog.Choices -> current.labels.forEachIndexed { index, label ->
                                TextButton(text = if (current.selected == index) "✓ $label" else label,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).heightIn(min = 44.dp),
                                    colors = if (current.selected == index) ButtonDefaults.textButtonColorsPrimary()
                                        else ButtonDefaults.textButtonColors(),
                                    onClick = { dialog = null; current.onSelect(index) })
                            }
                        }
                    }
                }
                if (current is LogDialog.Record) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        TextButton(text = getString(R.string.log_copy), modifier = Modifier.weight(1f), onClick = {
                            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                .setPrimaryClip(ClipData.newPlainText("log", format(current.record)))
                        })
                        current.record.trace?.let { trace ->
                            TextButton(text = getString(R.string.log_export_trace), modifier = Modifier.weight(1f),
                                enabled = !busy, onClick = { chooseDestination(LogFilter(trace = trace)) })
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    val confirm = current == LogDialog.Clear || current == LogDialog.Trace
                    TextButton(text = getString(if (confirm || current is LogDialog.Choices || current == LogDialog.Capture)
                        android.R.string.cancel else android.R.string.ok), modifier = Modifier.weight(1f), onClick = { dialog = null })
                    if (confirm) TextButton(text = getString(android.R.string.ok), modifier = Modifier.weight(1f),
                        enabled = !busy, colors = ButtonDefaults.textButtonColorsPrimary(), onClick = {
                            dialog = null
                            if (current == LogDialog.Clear) clearLogs()
                            else {
                                val trace = traceInput.trim()
                                if (trace.matches(Regex("[A-Za-z0-9_-]{1,100}"))) chooseDestination(LogFilter(trace = trace))
                                else toast(R.string.log_invalid_trace)
                            }
                        })
                }
            }
        }
    }

    @Composable
    private fun DialogText(text: String) {
        Text(text, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            fontSize = 14.sp, lineHeight = 20.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }

    private fun levelValues() = listOf(getString(R.string.log_all_levels)) + resources.getStringArray(R.array.log_level_names)
    private fun moduleValues() = listOf(getString(R.string.log_all_modules)) + resources.getStringArray(R.array.log_modules)
    private fun choices(title: Int, labels: List<String>, selected: Int? = null, onSelect: (Int) -> Unit) {
        dialog = LogDialog.Choices(title, labels, selected, onSelect)
    }

    private fun syncToggles() {
        loggingEnabled = AppLogger.enabled
        detailedEnabled = AppLogger.detailed
        activeSession = ProblemSessions.active
        renderHealth()
    }
    private fun showSettings() { syncToggles(); dialog = LogDialog.Settings }
    private fun showStatus() { renderHealth(); dialog = LogDialog.Status }
    private fun capture() {
        if (SystemLogCapture.status.value.running) SystemLogCapture.stop()
        else if (!AppLogger.enabled) toast(R.string.log_enable_first)
        else dialog = LogDialog.Capture
    }

    private fun captureDescription(status: CaptureStatus): String {
        val message = when (status.result) {
            "starting" -> R.string.log_capture_starting
            "running" -> R.string.log_capture_running
            "complete" -> R.string.log_capture_complete
            "stopped" -> R.string.log_capture_stopped
            "idle" -> R.string.log_capture_idle
            else -> R.string.log_capture_failure_detail
        }
        return if (message == R.string.log_capture_failure_detail)
            getString(message, captureReason(status.result), status.exitCode?.toString() ?: "—", status.count, status.discardedLines)
        else getString(message, status.count)
    }
    private fun captureReason(reason: String): String = getString(when (reason) {
        "permission_missing" -> R.string.log_reason_permission
        "service_timeout" -> R.string.log_reason_timeout
        "service_disconnected" -> R.string.log_reason_disconnected
        "protocol_unsupported" -> R.string.log_reason_protocol
        "root_unavailable" -> R.string.log_reason_root
        "command_failed" -> R.string.log_reason_command
        "output_limit", "event_limit" -> R.string.log_reason_limit
        "collector_busy" -> R.string.log_reason_busy
        else -> R.string.log_reason_closed
    })

    private fun renderHealth() {
        val health = AppLogger.health()
        val reason = getString(when (health.lastFailure) {
            "none" -> R.string.log_no_error
            "queue_full" -> R.string.log_reason_queue
            "encoding_failed" -> R.string.log_reason_encoding
            "write_rejected" -> R.string.log_reason_rejected
            else -> R.string.log_reason_io
        })
        healthDetails = getString(R.string.log_health, health.queued, health.dropped, health.failedWrites,
            reason + if (health.lastFailureAt > 0) " · ${time(health.lastFailureAt)}" else "")
        healthWarning = health.dropped > 0 || health.failedWrites > 0
        healthLabel = when {
            healthWarning -> R.string.log_health_warning
            !AppLogger.enabled -> R.string.log_health_paused
            else -> R.string.log_health_normal
        }
    }

    private fun sessionDescription(): String {
        val selected = selectedSession
        val active = activeSession
        return when {
            selected != null -> getString(R.string.log_problem_selected, selected.id.take(8), time(selected.startedAt),
                selected.endedAt?.let(::time) ?: getString(R.string.log_problem_recording))
            active != null -> getString(R.string.log_problem_active, active.id.take(8))
            else -> getString(R.string.log_problem_description)
        }
    }

    private fun recordProblem() {
        if (busy) return
        if (ProblemSessions.active != null) {
            choices(R.string.log_problem_finish, listOf(getString(R.string.log_share), getString(R.string.log_save))) { index ->
                if (!busy) lifecycleScope.launch {
                    updateBusyState(true)
                    try {
                        val finished = ProblemSessions.finish()
                        selectedSession = finished
                        if (finished != null) exportArchive(index == 0, finished.filter(), finished)
                    } catch (e: Exception) { showFailure(e) }
                    finally { updateBusyState(false); syncToggles(); reload() }
                }
            }
        } else lifecycleScope.launch {
            updateBusyState(true)
            try {
                if (!AppLogger.enabled) AppLogger.setEnabled(true)
                selectedSession = ProblemSessions.start()
                syncToggles()
            } catch (e: Exception) { showFailure(e) }
            finally { updateBusyState(false); reload() }
        }
    }

    private fun showHistory() {
        val history = ProblemSessions.history.value
        val labels = listOf(getString(R.string.log_all_records)) + history.map {
            "${time(it.startedAt)} · ${it.id.take(8)}" + if (it.endedAt == null) " · ${getString(R.string.log_problem_recording)}" else ""
        }
        choices(R.string.log_problem_history, labels, history.indexOfFirst { it.id == selectedSession?.id } + 1) {
            selectedSession = history.getOrNull(it - 1)
            reload()
        }
    }

    override fun onStop() { queryJob?.cancel(); super.onStop() }
    override fun onSaveInstanceState(outState: Bundle) {
        pendingArchive?.let { outState.putString("archive", it.absolutePath) }
        selectedSession?.let { outState.putString("session", it.id) }
        outState.putInt("level", levelIndex)
        outState.putInt("module", moduleIndex)
        outState.putInt("time", timeIndex)
        outState.putString("query", query)
        super.onSaveInstanceState(outState)
    }

    private fun currentFilter(): LogFilter {
        val since = when (timeIndex) {
            1 -> System.currentTimeMillis() - 60 * 60 * 1000L
            2 -> System.currentTimeMillis() - 24 * 60 * 60 * 1000L
            else -> Long.MIN_VALUE
        }
        val session = selectedSession
        return LogFilter(since = maxOf(since, session?.startedAt ?: Long.MIN_VALUE), until = session?.endedAt ?: Long.MAX_VALUE,
            level = LogLevel.entries.getOrNull(levelIndex - 1), module = LogModule.entries.getOrNull(moduleIndex - 1),
            query = query.trim(), session = session?.id)
    }

    private fun sourceFingerprint(): String = AppLogger.allStores().joinToString("|") { it.fingerprint() }
    private fun reload() { load(reset = true) }
    private fun load(reset: Boolean, debounce: Boolean = true) {
        if (busy) return
        queryJob?.cancel()
        val revision = ++generation
        val filter = currentFilter()
        val before = if (reset) null else records.lastOrNull()
        querying = true
        queryJob = lifecycleScope.launch {
            try {
                if (debounce) delay(250)
                val (pages, token) = runInterruptible(Dispatchers.IO) {
                    val token = sourceFingerprint()
                    AppLogger.allStores().map { it.page(filter, before, PAGE_SIZE) } to token
                }
                if (revision != generation) return@launch
                val page = pages.flatMap { it.records }.sortedWith(LogStore.NEWEST_FIRST).take(PAGE_SIZE)
                records = if (reset) page else records + page
                fingerprint = token
                hasMore = page.size == PAGE_SIZE
                val error = pages.mapNotNull { it.latestError }.minWithOrNull(LogStore.NEWEST_FIRST)
                storageSummary = getString(R.string.log_storage_compact, pages.sumOf { it.stats.first }, pages.sumOf { it.stats.second } / 1024)
                storageDetails = getString(R.string.log_summary, pages.sumOf { it.stats.first }, pages.sumOf { it.stats.second } / 1024,
                    error?.let { "${time(it.timestamp)} ${it.event}" } ?: getString(R.string.log_no_error))
                renderHealth()
            } catch (e: Exception) { showFailure(e) }
            finally { if (revision == generation) querying = false }
        }
    }

    private fun clearLogs() {
        if (busy) return
        lifecycleScope.launch {
            updateBusyState(true)
            try {
                ProblemSessions.finish("cleared")
                SystemLogCapture.stopAndJoin()
                AppLogger.ordered { AppLogger.allStores().forEach { it.clear() } }
                ProblemSessions.clear()
                withContext(Dispatchers.IO) {
                    File(cacheDir, "diagnostics").listFiles()?.forEach { if (it.isDirectory) it.deleteRecursively() else it.delete() }
                }
                pendingArchive = null
                selectedSession = null
                records = emptyList()
                hasMore = false
                toast(R.string.log_cleared)
            } catch (e: Exception) { showFailure(e) }
            finally { updateBusyState(false); syncToggles(); reload() }
        }
    }

    private fun chooseExport() {
        if (busy) return
        val problem = selectedSession ?: ProblemSessions.active
        val labels = listOfNotNull(if (problem != null) getString(R.string.log_export_problem) else null,
            getString(R.string.log_export_filter), getString(R.string.log_export_all), getString(R.string.log_export_trace))
        choices(R.string.log_export_scope, labels) { index ->
            when (index - if (problem != null) 1 else 0) {
                -1 -> chooseDestination(problem?.filter(), problem)
                0 -> chooseDestination(currentFilter(), selectedSession)
                1 -> chooseDestination(null)
                else -> { traceInput = ""; dialog = LogDialog.Trace }
            }
        }
    }

    private fun chooseDestination(filter: LogFilter?, problem: ProblemSession? = null) {
        choices(R.string.log_export, listOf(getString(R.string.log_share), getString(R.string.log_save))) { index ->
            if (!busy) lifecycleScope.launch {
                updateBusyState(true)
                try { exportArchive(index == 0, filter, problem) }
                catch (e: Exception) { showFailure(e) }
                finally { updateBusyState(false); reload() }
            }
        }
    }

    private suspend fun exportArchive(share: Boolean, filter: LogFilter?, problem: ProblemSession? = null) {
        val archive = LogArchive.export(cacheDir, filter, problem)
        if (share) {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", archive)
            val intent = Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.clipData = ClipData.newRawUri("logs", uri)
            startActivity(Intent.createChooser(intent, getString(R.string.log_share)))
        } else { pendingArchive = archive; saveArchive.launch(archive.name) }
    }

    private fun showFailure(error: Exception) {
        if (error is CancellationException) throw error
        toast(R.string.log_operation_failed)
    }
    private fun updateBusyState(value: Boolean) {
        busy = value
        if (value) {
            queryJob?.cancel()
            generation++
            querying = false
        }
    }

    private fun format(record: LogRecord): String = buildString {
        appendLine("${time(record.timestamp)} ${record.level} / ${record.module}")
        appendLine(record.event)
        record.trace?.let { appendLine("trace: $it") }
        record.session?.let { appendLine("problem: $it") }
        record.fields.forEach { (key, value) -> appendLine("$key: $value") }
        record.error?.let { appendLine(it) }
    }
    private fun time(timestamp: Long): String = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(timestamp))
    private fun toast(message: Int) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }

    private fun eventTitle(event: String): String {
        val title = when (event) {
            "process_started" -> R.string.log_event_process_started
            "logging_enabled" -> R.string.log_event_logging_enabled
            "diagnostics_enabled" -> R.string.log_event_diagnostics_enabled
            "diagnostics_disabled" -> R.string.log_event_diagnostics_disabled
            "problem_recording_started" -> R.string.log_event_problem_started
            "problem_recording_finished" -> R.string.log_event_problem_finished
            "action_triggered" -> R.string.log_event_action_triggered
            "action_dispatch_returned" -> R.string.log_event_action_dispatched
            "action_failed" -> R.string.log_event_action_failed
            "shortcut_parsed" -> R.string.log_event_shortcut_parsed
            "launch_requested" -> R.string.log_event_launch_requested
            "launch_request_submitted" -> R.string.log_event_launch_submitted
            "launch_permission_denied" -> R.string.log_event_permission_denied
            "activity_not_found" -> R.string.log_event_activity_missing
            "launch_intent_missing" -> R.string.log_event_intent_missing
            "uncaught_exception" -> R.string.log_event_uncaught_exception
            else -> return event
        }
        return getString(title)
    }

    companion object { private const val PAGE_SIZE = 100 }
}
