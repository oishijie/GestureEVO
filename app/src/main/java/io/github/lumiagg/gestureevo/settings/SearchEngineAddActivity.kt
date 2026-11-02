package io.github.lumiagg.gestureevo.settings

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsService
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.createBitmap
import androidx.lifecycle.lifecycleScope
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.db.APPModel
import io.github.lumiagg.gestureevo.gesture.config.IconType
import io.github.lumiagg.gestureevo.gesture.ui.RoundImageTextView
import io.github.lumiagg.gestureevo.search.config.EngineType
import io.github.lumiagg.gestureevo.search.config.SearchEngine
import io.github.lumiagg.gestureevo.search.config.SearchEngineDataManager
import io.github.lumiagg.gestureevo.search.setEngineIcon
import io.github.lumiagg.gestureevo.settings.ui.ActivityFilter
import io.github.lumiagg.gestureevo.settings.ui.ActivityFilters
import io.github.lumiagg.gestureevo.settings.ui.ActivityModel
import io.github.lumiagg.gestureevo.settings.ui.DefaultSelectAPPBottomSheet
import io.github.lumiagg.gestureevo.settings.ui.SelectAPPCallBack
import io.github.lumiagg.gestureevo.settings.ui.SelectActivityBottomSheet
import io.github.lumiagg.gestureevo.settings.ui.SelectActivityCallBack
import io.github.lumiagg.gestureevo.settings.ui.showEditTextBottomSheet
import io.github.lumiagg.gestureevo.ui.compose.MiuixEvoTheme
import io.github.lumiagg.gestureevo.ui.miuix.MiuixCard
import io.github.lumiagg.gestureevo.ui.miuix.MiuixScreen
import io.github.lumiagg.gestureevo.utils.icon.IconFileUtils
import io.github.lumiagg.gestureevo.utils.icon.IconHelper
import io.github.lumiagg.gestureevo.utils.icon.IconImageIO
import io.github.lumiagg.gestureevo.utils.readKey
import io.github.lumiagg.gestureevo.utils.settingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

class SearchEngineAddActivity : AppCompatActivity() {
    private var editMode = false
    // SearchEngine is mutable; invalidate readers even when a callback edits the same model.
    private var searchEngine by mutableStateOf(SearchEngine(), neverEqualPolicy())
    private val iconFileUtils = IconFileUtils(this)
    private var showImageBadge by mutableStateOf(true)
    private var showIconPicker by mutableStateOf(false)
    private var customTabsSupported by mutableStateOf(false)
    private var busy by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        editMode = intent.getBooleanExtra("editMode", false)
        val draft = savedInstanceState?.getString("engine_draft")
        val initialJson = draft ?: if (editMode) intent.getStringExtra("engine") else null
        searchEngine = initialJson?.let(SearchEngine::toModel) ?: SearchEngine().apply {
            tag = SearchEngineDataManager.buildTag()
        }
        showIconPicker = savedInstanceState?.getBoolean("icon_picker_open") ?: false
        if (searchEngine.engineType == EngineType.DIRECT_LINK) updateDirectLinkCustomTabsSwitch()
        setContent {
            MiuixEvoTheme {
                Column(Modifier.fillMaxWidth().imePadding()) {
                    EngineScreen()
                }
                if (showIconPicker) IconSourceDialog()
            }
        }
        lifecycleScope.launch {
            showImageBadge = settingsStore.readKey(SearchEngineSettingsActivity.KEY_SHOW_IMAGE_BADGE, true)
        }
    }

    private fun updateEngine(change: SearchEngine.() -> Unit) {
        searchEngine = searchEngine.apply(change)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("engine_draft", searchEngine.toJson())
        outState.putBoolean("icon_picker_open", showIconPicker)
        super.onSaveInstanceState(outState)
    }

    @Composable
    private fun EngineScreen() {
        MiuixScreen(
            title = stringResource(if (editMode) R.string.search_engine_edit_page_title else R.string.search_engine_add_page_title),
            onBack = { onBackPressedDispatcher.onBackPressed() },
        ) {
            item {
                MiuixCard {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionTitle(R.string.search_engine_add_identity_title)
                        Description(R.string.search_engine_add_identity_desc)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            val iconLabel = stringResource(R.string.icon_select_title)
                            AndroidView(
                                factory = { context -> RoundImageTextView(context) },
                                update = { view -> updateEngineIconPreview(view) },
                                modifier = Modifier.size(64.dp)
                                    .semantics { contentDescription = iconLabel }
                                    .clickable(role = Role.Button) { showIconPicker = true },
                            )
                            TextField(
                                value = searchEngine.name.orEmpty(),
                                onValueChange = { value -> updateEngine { name = value } },
                                label = stringResource(R.string.search_engine_add_name_hint),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            item {
                MiuixCard {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        SectionTitle(R.string.search_engine_add_mode_title)
                    }
                    Column(Modifier.selectableGroup()) {
                        EngineType.entries.forEach { type ->
                            val selected = searchEngine.engineType == type
                            Row(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                    .selectable(selected = selected, role = Role.RadioButton, onClick = {
                                        updateEngine { engineType = type }
                                        if (type == EngineType.DIRECT_LINK) updateDirectLinkCustomTabsSwitch()
                                    })
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(stringResource(modeLabel(type)), modifier = Modifier.weight(1f), fontSize = 16.sp)
                                RadioButton(selected = selected, onClick = null)
                            }
                        }
                    }
                }
            }
            item { MiuixCard { EngineConfiguration() } }
            item {
                MiuixCard {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        ActionButton(R.string.search_engine_add_share_config, Modifier.weight(1f), enabled = !busy) { shareEngineConfig() }
                        ActionButton(R.string.search_engine_add_save, Modifier.weight(1f), primary = true, enabled = !busy) { saveSearchEngine() }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    @Composable
    private fun EngineConfiguration() {
        val type = searchEngine.engineType
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (type) {
                EngineType.DIRECT_LINK -> {
                    SectionTitle(R.string.se_direct_link_title)
                    Description(R.string.se_direct_link_example)
                    Description(R.string.se_direct_link_placeholder)
                }
                EngineType.JUMP_TO_ACTIVITY -> {
                    SectionTitle(R.string.se_jump_activity_title)
                    Description(R.string.se_jump_activity_description)
                    Description(R.string.se_jump_activity_target_question)
                }
                EngineType.EXTERN_JUMP_LINK -> {
                    SectionTitle(R.string.se_extern_jump_title)
                    Description(R.string.se_extern_jump_description)
                    Description(R.string.se_extern_jump_source_app_question)
                }
                EngineType.SHARE_TO_APP -> {
                    SectionTitle(R.string.se_share_title)
                    Description(R.string.se_share_description)
                }
                EngineType.SHARE_IMAGE_TO_APP -> {
                    SectionTitle(R.string.se_share_image_title)
                    Description(R.string.se_share_image_description)
                }
            }
        }
        when (type) {
            EngineType.DIRECT_LINK -> {
                ConfigRow(R.string.se_direct_link_set_link, searchEngine.searchLink) { editLink(external = false) }
                Column(Modifier.padding(horizontal = 16.dp)) { Description(R.string.se_direct_link_choose_app) }
                ConfigRow(R.string.se_direct_link_select_app, searchEngine.targetPackage) { selectPackage(external = false) }
                if (customTabsSupported) {
                    SwitchPreference(
                        title = stringResource(R.string.se_direct_link_use_custom_tabs),
                        checked = searchEngine.useCustomTabs,
                        onCheckedChange = { checked -> updateEngine { useCustomTabs = checked } },
                    )
                }
            }
            EngineType.JUMP_TO_ACTIVITY -> {
                ConfigRow(R.string.se_jump_activity_select_activity, targetActivityLabel()) { selectActivity() }
                AutoEnterPreference()
            }
            EngineType.EXTERN_JUMP_LINK -> {
                ConfigRow(R.string.se_extern_jump_select_source_app, searchEngine.externJumpPackage) { selectPackage(external = true) }
                ConfigRow(R.string.se_extern_jump_set_link, searchEngine.externJumpLink) { editLink(external = true) }
                Column(Modifier.padding(horizontal = 16.dp)) { Description(R.string.se_extern_jump_target_activity_question) }
                ConfigRow(R.string.se_extern_jump_select_target_activity, targetActivityLabel()) { selectActivity() }
                AutoEnterPreference()
            }
            EngineType.SHARE_TO_APP -> ConfigRow(R.string.se_share_select_activity, targetActivityLabel()) {
                selectActivity(ActivityFilters.textShareFilter())
            }
            EngineType.SHARE_IMAGE_TO_APP -> ConfigRow(R.string.se_share_image_select_activity, targetActivityLabel()) {
                selectActivity(ActivityFilters.imageShareFilter())
            }
        }
    }

    @Composable
    private fun SectionTitle(resource: Int) {
        Text(stringResource(resource), fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
    }

    @Composable
    private fun Description(resource: Int) {
        Text(stringResource(resource), fontSize = 12.sp, lineHeight = 16.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }

    @Composable
    private fun ConfigRow(title: Int, value: String?, onClick: () -> Unit) {
        ArrowPreference(
            title = stringResource(title),
            summary = value?.takeIf { it.isNotBlank() } ?: stringResource(R.string.se_direct_link_not_set),
            onClick = onClick,
        )
    }

    @Composable
    private fun AutoEnterPreference() {
        SwitchPreference(
            title = stringResource(R.string.se_extern_jump_auto_enter),
            checked = searchEngine.autoInputEnter,
            onCheckedChange = { checked -> updateEngine { autoInputEnter = checked } },
        )
    }

    @Composable
    private fun ActionButton(label: Int, modifier: Modifier = Modifier, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
        TextButton(
            text = stringResource(label),
            onClick = onClick,
            modifier = modifier.heightIn(min = 48.dp),
            enabled = enabled,
            colors = if (primary) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors(),
        )
    }

    @Composable
    private fun IconSourceDialog() {
        Dialog(onDismissRequest = { showIconPicker = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            MiuixCard(Modifier.padding(horizontal = 12.dp)) {
                Column(Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState())) {
                    Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp)) {
                        SectionTitle(R.string.icon_select_title)
                    }
                    ArrowPreference(title = stringResource(R.string.icon_select_gallery), onClick = {
                        showIconPicker = false
                        selectImageLauncher.launch(IconEditorRequest(imageBadge = imageBadgeEnabled()))
                    })
                    ArrowPreference(title = stringResource(R.string.icon_select_app_icon), onClick = {
                        showIconPicker = false
                        DefaultSelectAPPBottomSheet(this@SearchEngineAddActivity, lifecycleScope, object : SelectAPPCallBack {
                            override fun selectCompleted(model: APPModel) {
                                selectImageLauncher.launch(IconEditorRequest(appPackage = model.pkgName, imageBadge = imageBadgeEnabled()))
                            }
                        }).show()
                    })
                    ArrowPreference(title = stringResource(R.string.icon_select_text_icon), onClick = {
                        showIconPicker = false
                        selectTextIconLauncher.launch(TextIconEditorRequest(
                            text = if (searchEngine.iconType == IconType.TEXT) searchEngine.textIcon.orEmpty() else "",
                            imageBadge = imageBadgeEnabled(),
                        ))
                    })
                    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 24.dp)) {
                        ActionButton(android.R.string.cancel, Modifier.weight(1f)) { showIconPicker = false }
                    }
                }
            }
        }
    }

    private fun modeLabel(type: EngineType): Int = when (type) {
        EngineType.DIRECT_LINK -> R.string.search_engine_tab_direct_link
        EngineType.SHARE_TO_APP -> R.string.search_engine_tab_share_to_app
        EngineType.SHARE_IMAGE_TO_APP -> R.string.search_engine_tab_share_image_to_app
        EngineType.JUMP_TO_ACTIVITY -> R.string.search_engine_tab_jump_activity
        EngineType.EXTERN_JUMP_LINK -> R.string.search_engine_tab_extern_jump
    }

    private fun targetActivityLabel(): String? = if (searchEngine.targetPackage.isNullOrBlank()) null else {
        getString(R.string.package_activity_label, searchEngine.targetPackage, searchEngine.targetActivity.orEmpty())
    }

    private fun editLink(external: Boolean) {
        showEditTextBottomSheet(
            activity = this,
            coroutineScope = lifecycleScope,
            title = getString(if (external) R.string.search_engine_extern_jump_title else R.string.search_engine_direct_link_title),
            hint = getString(if (external) R.string.search_engine_extern_jump_hint else R.string.search_engine_direct_link_hint),
            defaultText = if (external) searchEngine.externJumpLink else searchEngine.searchLink,
            onConfirm = { input ->
                if (input.isNotEmpty()) updateEngine {
                    if (external) externJumpLink = input else searchLink = input
                }
            },
        )
    }

    private fun selectPackage(external: Boolean) {
        DefaultSelectAPPBottomSheet(this, lifecycleScope, object : SelectAPPCallBack {
            override fun selectCompleted(model: APPModel) {
                updateEngine { if (external) externJumpPackage = model.pkgName else targetPackage = model.pkgName }
                if (!external) updateDirectLinkCustomTabsSwitch()
                autoPopulateIcon(model.pkgName)
            }
        }).show()
    }

    private fun selectActivity(filter: ActivityFilter? = null) {
        SelectActivityBottomSheet(this, lifecycleScope, object : SelectActivityCallBack {
            override fun selectCompleted(model: ActivityModel) {
                updateEngine {
                    targetPackage = model.packageName
                    targetActivity = model.activityName
                }
                autoPopulateIcon(model.packageName)
            }
        }, filter).show()
    }

    private fun autoPopulateIcon(packageName: String) {
        if (searchEngine.iconType == IconType.OTHER) {
            IconHelper.getIcon(applicationContext, packageName)?.let(::updateAndSaveIcon)
        }
    }

    private fun updateDirectLinkCustomTabsSwitch() {
        customTabsSupported = supportsCustomTabs(searchEngine.targetPackage)
        if (!customTabsSupported) updateEngine { useCustomTabs = false }
    }

    private fun supportsCustomTabs(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        val serviceIntent = Intent(CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION).apply { setPackage(packageName) }
        return packageManager.queryIntentServices(serviceIntent, 0).isNotEmpty()
    }

    private fun imageBadgeEnabled() = searchEngine.engineType == EngineType.SHARE_IMAGE_TO_APP && showImageBadge

    private val selectTextIconLauncher = registerForActivityResult(TextIconEditorContract()) { text ->
        if (text != null) {
            iconFileUtils.clear()
            updateEngine {
                textIcon = text
                iconType = IconType.TEXT
                icon = null
            }
        }
    }

    private val selectImageLauncher = registerForActivityResult(IconEditorContract()) { path ->
        if (path != null) lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) { IconImageIO.read(path) }
            if (bitmap != null) {
                iconFileUtils.setImgBitmap(bitmap)
                iconFileUtils.imgPath = path
                updateEngine {
                    iconType = IconType.URI
                    icon = path
                }
            } else {
                Toast.makeText(this@SearchEngineAddActivity, R.string.icon_editor_load_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateAndSaveIcon(drawable: Drawable) {
        val bitmap = createBitmap(drawable.intrinsicWidth.coerceAtLeast(1), drawable.intrinsicHeight.coerceAtLeast(1))
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        iconFileUtils.clear().setImgBitmap(bitmap)
        // Give an automatically populated icon a durable path immediately, so drafts restore it too.
        if (iconFileUtils.saveIcon()) {
            updateEngine { iconType = IconType.URI; icon = iconFileUtils.imgPath }
        } else {
            updateEngine { iconType = IconType.URI; icon = null }
        }
    }

    private fun updateEngineIconPreview(view: RoundImageTextView) {
        val engine = searchEngine
        if (engine.iconType == IconType.URI && engine.icon.isNullOrBlank() && iconFileUtils.getImgBitmap() != null) {
            view.setImage(iconFileUtils.getImgBitmap())
        } else if (engine.iconType == IconType.URI || engine.iconType == IconType.TEXT ||
            !engine.targetPackage.isNullOrEmpty() || !engine.name.isNullOrEmpty()) {
            setEngineIcon(applicationContext, view, engine, showImageBadge)
        } else {
            view.setImage(R.drawable.baseline_image_24)
        }
        view.setBadge(if (imageBadgeEnabled()) R.drawable.baseline_image_24 else null)
    }

    private fun checkedEngineSnapshot(): SearchEngine? {
        val engineName = searchEngine.name.orEmpty().trim()
        if (engineName.isEmpty()) {
            Toast.makeText(this, R.string.search_engine_name_required, Toast.LENGTH_SHORT).show()
            return null
        }
        val valid = when (searchEngine.engineType) {
            EngineType.DIRECT_LINK -> !searchEngine.searchLink.isNullOrEmpty()
            EngineType.JUMP_TO_ACTIVITY -> !searchEngine.targetPackage.isNullOrEmpty() && !searchEngine.targetActivity.isNullOrEmpty()
            EngineType.EXTERN_JUMP_LINK -> !searchEngine.externJumpLink.isNullOrEmpty() && !searchEngine.externJumpPackage.isNullOrEmpty()
            EngineType.SHARE_TO_APP, EngineType.SHARE_IMAGE_TO_APP -> !searchEngine.targetPackage.isNullOrEmpty()
        }
        if (!valid) {
            Toast.makeText(this, R.string.search_engine_config_incomplete, Toast.LENGTH_SHORT).show()
            return null
        }
        updateEngine { name = engineName }
        // Read Compose state on Main, and hand IO an independent model rather than a live draft.
        return SearchEngine.toModel(searchEngine.toJson())
    }

    private fun saveSearchEngine() {
        if (busy) return
        val engine = checkedEngineSnapshot() ?: return
        busy = true
        lifecycleScope.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    if (iconFileUtils.saveIcon()) engine.icon = iconFileUtils.imgPath
                    if (editMode) SearchEngineDataManager.updateSearchEngine(applicationContext, engine)
                    else { SearchEngineDataManager.addSearchEngine(applicationContext, engine); true }
                }
                Toast.makeText(this@SearchEngineAddActivity,
                    if (!success) R.string.search_engine_update_failed
                    else if (editMode) R.string.search_engine_update_success else R.string.search_engine_add_success,
                    Toast.LENGTH_SHORT).show()
                Log.d(TAG, "saveSearchEngine: ${engine.toJson()}")
                if (success) finish()
            } finally {
                busy = false
            }
        }
    }

    private fun shareEngineConfig() {
        if (busy) return
        val engine = checkedEngineSnapshot() ?: return
        lifecycleScope.launch {
            val json = withContext(Dispatchers.IO) { SearchEngine.toExportJsonList(listOf(engine)) }
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText(getString(R.string.search_engine_clipboard_label), json))
            Toast.makeText(this@SearchEngineAddActivity, R.string.search_engine_clipboard_copied, Toast.LENGTH_SHORT).show()
            Log.d(TAG, "shareSearchEngine: ${engine.toJson()}")
        }
    }

    companion object {
        const val TAG = "SearchEngineAddActivity"
    }
}
