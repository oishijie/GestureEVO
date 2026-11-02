package io.github.lumiagg.gestureevo.settings.ui

import android.app.Activity
import android.content.Context
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.LifecycleCoroutineScope
import com.drake.brv.BindingAdapter
import com.google.android.material.textview.MaterialTextView
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.db.APPModel
import io.github.lumiagg.gestureevo.gesture.ui.RoundImageTextView
import io.github.lumiagg.gestureevo.db.getAPPDao
import io.github.lumiagg.gestureevo.ui.miuix.MiuixCard
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.delay
import io.github.lumiagg.gestureevo.utils.MoshiUtils
import io.github.lumiagg.gestureevo.utils.app.appDisplayNameFromDatabase
import io.github.lumiagg.gestureevo.utils.icon.IconHelper
import io.github.lumiagg.gestureevo.utils.readKey
import io.github.lumiagg.gestureevo.utils.saveKey
import io.github.lumiagg.gestureevo.utils.settingsStore
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

data class DomainAppModel(
    val domain: String,
    val packageName: String,
    val appName: String,
    val appIcon: Drawable?
)

object LinkOpenDomainUtils {
    fun normalizeDomainInput(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        return try {
            val host = URL(normalizeUrlForParsing(trimmed)).host.orEmpty()
            normalizeHost(host)
        } catch (_: Exception) {
            null
        }
    }

    fun matches(url: String, domain: String): Boolean {
        val host = extractHost(url) ?: return false
        return host == domain || host.endsWith(".$domain")
    }

    private fun extractHost(url: String): String? {
        return try {
            normalizeHost(URL(normalizeUrlForParsing(url)).host.orEmpty())
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeUrlForParsing(raw: String): String {
        return when {
            raw.startsWith("http://", ignoreCase = true) ||
                raw.startsWith("https://", ignoreCase = true) -> raw

            raw.startsWith("//") -> "https:$raw"
            else -> "https://$raw"
        }
    }

    private fun normalizeHost(host: String): String? {
        val normalized = host.trim().trim('.').lowercase().removePrefix("www.")
        return normalized.takeIf { it.contains('.') && !it.contains(' ') }
    }
}

interface DefaultLinkOpenCallBack {
    suspend fun onDomainAppListChanged(domainAppList: List<DomainAppModel>) {}
}

class DefaultLinkOpenBottomSheet(
    private val activity: Activity,
    private val coroutineScope: LifecycleCoroutineScope,
    private val defaultLinkOpenCallBack: DefaultLinkOpenCallBack,
    private val domainAppList: MutableList<DomainAppModel> = mutableListOf()
) {

    private var dismissSheet: (() -> Unit)? = null
    private var dismissRequested by mutableStateOf(false)
    private var revision by mutableStateOf(0)

    fun show() {
        dismissRequested = false
        domainAppList.sortBy { it.domain.lowercase() }
        showMiuixSheet(activity) { dismiss ->
            dismissSheet = dismiss
            LaunchedEffect(dismissRequested) { if (dismissRequested) dismiss() }
            @Suppress("UNUSED_VARIABLE") val currentRevision = revision
            var editor by remember { mutableStateOf<Pair<DomainAppModel?, Int>?>(null) }
            var deleting by remember { mutableStateOf<DomainAppModel?>(null) }
            var message by remember { mutableStateOf<Pair<Int, Int>?>(null) }
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                LinkSheetTitle(stringResource(R.string.link_open_default_title))
                Text(stringResource(R.string.link_open_description),
                    modifier = Modifier.padding(bottom = 12.dp), fontSize = 12.sp,
                    lineHeight = 16.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 500.dp)) {
                    itemsIndexed(domainAppList.toList()) { position, item ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            LinkAppIcon(item.appIcon)
                            Column(Modifier.weight(1f)) {
                                ArrowPreference(title = item.domain,
                                    summary = activity.getString(R.string.link_open_app_summary, item.appName),
                                    onClick = { showSelectAppDialog(item.domain, item, position) })
                            }
                            IconButton(onClick = { editor = item to position }, modifier = Modifier.size(44.dp)) {
                                Icon(painterResource(R.drawable.baseline_edit_24),
                                    stringResource(R.string.link_open_action_edit_domain))
                            }
                            IconButton(onClick = { deleting = item }, modifier = Modifier.size(44.dp)) {
                                Icon(painterResource(R.drawable.ic_delete),
                                    stringResource(R.string.link_open_action_delete))
                            }
                        }
                    }
                }
                TextButton(text = stringResource(R.string.default_link_open_add_domain),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp).heightIn(min = 44.dp),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = { editor = null to -1 })
            }
            editor?.let { (item, position) ->
                var input by rememberSaveable(item, position) { mutableStateOf(item?.domain.orEmpty()) }
                LinkSheetDialog(
                    title = stringResource(if (item == null) R.string.link_open_add_domain_title else R.string.link_open_edit_domain_title),
                    confirm = stringResource(R.string.link_open_next_step), onDismiss = { editor = null },
                    onConfirm = {
                        val normalized = LinkOpenDomainUtils.normalizeDomainInput(input)
                        editor = null
                        when {
                            normalized == null -> message = R.string.link_open_invalid_domain_title to R.string.link_open_invalid_domain_message
                            isDomainExists(normalized, position) -> message = R.string.link_open_domain_exists_title to R.string.link_open_domain_exists_message
                            item != null && position in domainAppList.indices -> {
                                domainAppList[position] = item.copy(domain = normalized)
                                persistAndRefresh()
                            }
                            else -> showSelectAppDialog(normalized)
                        }
                    }) {
                    TextField(value = input, onValueChange = { input = it },
                        label = stringResource(R.string.link_open_domain_hint), singleLine = true,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                }
            }
            deleting?.let { item ->
                LinkSheetDialog(title = stringResource(R.string.link_open_delete_title),
                    confirm = stringResource(R.string.link_open_action_delete), onDismiss = { deleting = null },
                    onConfirm = {
                        domainAppList.removeAll { it.domain == item.domain }
                        deleting = null
                        persistAndRefresh()
                    }) {
                    LinkSheetSummary(activity.getString(R.string.link_open_delete_message, item.domain))
                }
            }
            message?.let { (title, body) ->
                LinkSheetDialog(title = stringResource(title), confirm = stringResource(R.string.link_open_ok),
                    onDismiss = { message = null }, onConfirm = { message = null }, showCancel = false) {
                    LinkSheetSummary(stringResource(body))
                }
            }
        }
    }

    private fun showSelectAppDialog(
        domain: String,
        originalDomainApp: DomainAppModel? = null,
        position: Int = -1
    ) {
        LinkHandlerSelectAPPBottomSheet(activity, coroutineScope, object : SelectAPPCallBack {
            override fun selectCompleted(model: APPModel) {
                coroutineScope.launch(Dispatchers.IO) {
                    val updatedDomainApp = DomainAppModel(domain, model.pkgName, model.name,
                        IconHelper.getIcon(activity, model.pkgName))
                    withContext(Dispatchers.Main) {
                        if (originalDomainApp != null && position in domainAppList.indices) {
                            domainAppList[position] = updatedDomainApp
                        } else {
                            domainAppList.add(updatedDomainApp)
                        }
                        persistAndRefresh()
                    }
                }
            }
        }).show()
    }

    private fun persistAndRefresh() {
        domainAppList.sortBy { it.domain.lowercase() }
        revision++
        coroutineScope.launch {
            defaultLinkOpenCallBack.onDomainAppListChanged(domainAppList)
        }
    }

    private fun isDomainExists(domain: String, excludePosition: Int = -1): Boolean =
        domainAppList.withIndex().any { (index, item) ->
            index != excludePosition && item.domain.equals(domain, ignoreCase = true)
        }

    fun dismiss() {
        dismissRequested = true
        dismissSheet?.invoke()
        dismissSheet = null
    }
}

@Composable
private fun LinkSheetTitle(title: String) {
    Text(title, modifier = Modifier.padding(top = 24.dp, bottom = 12.dp),
        fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.onSurface)
}

@Composable
private fun LinkSheetSummary(summary: String) {
    Text(summary, fontSize = 14.sp, lineHeight = 20.sp,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
}

@Composable
private fun LinkSheetDialog(
    title: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit,
    showCancel: Boolean = true, content: @Composable () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        MiuixCard {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                LinkSheetTitle(title)
                content()
                Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    if (showCancel) TextButton(text = stringResource(R.string.link_open_cancel),
                        modifier = Modifier.weight(1f).heightIn(min = 44.dp), onClick = onDismiss)
                    TextButton(text = confirm, modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                        colors = ButtonDefaults.textButtonColorsPrimary(), onClick = onConfirm)
                }
            }
        }
    }
}

@Composable
private fun LinkAppIcon(drawable: Drawable?) {
    val bitmap = remember(drawable) { drawable?.toBitmap(48, 48)?.asImageBitmap() }
    bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.size(44.dp)) }
}
@JsonClass(generateAdapter = true)
data class StoreDomainApp(
    val pkg: String,
    val domain: String
)

class DomainAppManager(private val context: Context) {
    companion object {
        private const val DOMAIN_APP_LIST = "domain_app_list_simple"
    }

    suspend fun saveDomainAppList(domainAppList: List<DomainAppModel>) {
        val storeList = domainAppList.map {
            StoreDomainApp(pkg = it.packageName, domain = it.domain)
        }
        context.settingsStore.saveKey(DOMAIN_APP_LIST, MoshiUtils.toJson(storeList))
    }

    suspend fun loadDomainAppList(): MutableList<DomainAppModel> {
        val json = context.settingsStore.readKey(DOMAIN_APP_LIST, "[]")
        val storeList = MoshiUtils.toModel<List<StoreDomainApp>>(json).orEmpty()
        return storeList.mapNotNull { storeDomainApp ->
            val normalizedDomain = LinkOpenDomainUtils.normalizeDomainInput(storeDomainApp.domain)
                ?: return@mapNotNull null
            DomainAppModel(
                domain = normalizedDomain,
                packageName = storeDomainApp.pkg,
                appName = appDisplayNameFromDatabase(context, storeDomainApp.pkg).orEmpty(),
                appIcon = IconHelper.getIcon(context, storeDomainApp.pkg)
            )
        }.toMutableList()
    }

    suspend fun findMatchingDomainApp(originalUrl: String): DomainAppModel? {
        return loadDomainAppList()
            .filter { LinkOpenDomainUtils.matches(originalUrl, it.domain) }
            .maxByOrNull { it.domain.length }
    }

    suspend fun processLinkRedirect(originalUrl: String): Pair<String, String> {
        val matchingDomainApp = findMatchingDomainApp(originalUrl) ?: return "" to originalUrl
        return matchingDomainApp.packageName to originalUrl
    }
}

class DefaultLinkOpenManager(
    private val activity: Activity,
    private val coroutineScope: LifecycleCoroutineScope
) {
    private val domainAppManager = DomainAppManager(activity)

    suspend fun showDefaultLinkOpenSettings() {
        val domainAppList = domainAppManager.loadDomainAppList()

        DefaultLinkOpenBottomSheet(
            activity = activity,
            coroutineScope = coroutineScope,
            defaultLinkOpenCallBack = object : DefaultLinkOpenCallBack {
                override suspend fun onDomainAppListChanged(domainAppList: List<DomainAppModel>) {
                    domainAppManager.saveDomainAppList(domainAppList)
                }
            },
            domainAppList = domainAppList
        ).show()
    }
}

class LinkHandlerSelectAPPBottomSheet(
    activity: Activity,
    coroutineScope: LifecycleCoroutineScope,
    selectAPPCallBack: SelectAPPCallBack
) : BaseSelectAPPBottomSheet(activity, coroutineScope, selectAPPCallBack) {

    override fun show() {
        // Retain the BaseSelectAPP subtype/binding hook, without opening its XML window.
        showMiuixSheet(activity, dismissOnOutside = false) { dismiss ->
            var query by rememberSaveable { mutableStateOf("") }
            var selectedFlag by rememberSaveable { mutableStateOf(appFlags) }
            var apps by remember { mutableStateOf<List<APPModel>>(emptyList()) }
            var loading by remember { mutableStateOf(true) }
            var submitted by remember { mutableStateOf(false) }
            LaunchedEffect(selectedFlag) {
                loading = true
                appFlags = selectedFlag
                fullAppList = withContext(Dispatchers.IO) { getAPPDao().getAll(getAppFlag()) }
                apps = withContext(Dispatchers.Default) { filterApps(query.trim().lowercase()) }
                loading = false
            }
            LaunchedEffect(query, loading) {
                if (!loading) {
                    delay(300)
                    apps = withContext(Dispatchers.Default) { filterApps(query.trim().lowercase()) }
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                LinkSheetTitle(stringResource(R.string.app_select_name_hint))
                TextField(value = query, onValueChange = { query = it }, singleLine = true,
                    label = stringResource(R.string.app_select_name_hint),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp))
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    listOf(APPModel.FLAG_USER to R.string.app_select_user_apps,
                        APPModel.FLAG_SYSTEM to R.string.app_select_system_apps).forEach { (flag, label) ->
                        TextButton(text = stringResource(label),
                            modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                            colors = if (selectedFlag == flag) ButtonDefaults.textButtonColorsPrimary()
                                else ButtonDefaults.textButtonColors(),
                            onClick = { selectedFlag = flag })
                    }
                }
                if (loading) androidx.compose.material3.LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(), color = MiuixTheme.colorScheme.primary)
                LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 500.dp)) {
                    itemsIndexed(apps) { _, app ->
                        val bitmap by produceState<ImageBitmap?>(null, app.pkgName) {
                            value = withContext(Dispatchers.IO) {
                                IconHelper.getIcon(activity, app.pkgName)?.toBitmap(48, 48)?.asImageBitmap()
                            }
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.size(44.dp)) }
                            Column(Modifier.weight(1f)) {
                                ArrowPreference(title = app.name, summary = app.pkgName,
                                    enabled = !loading && !submitted,
                                    onClick = {
                                        if (!submitted) {
                                            submitted = true
                                            selectAPPCallBack.selectCompleted(app)
                                            dismiss()
                                        }
                                    })
                            }
                        }
                    }
                }
                TextButton(text = stringResource(R.string.link_open_cancel),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp).heightIn(min = 44.dp),
                    onClick = dismiss)
            }
        }
    }

    override fun onBindItem(holder: BindingAdapter.BindingViewHolder) {
        holder.findView<MaterialTextView>(R.id.name).text = holder.getModel<APPModel>().name
        holder.findView<RoundImageTextView>(R.id.icon).setImage(
            IconHelper.getIcon(holder.context, holder.getModel<APPModel>().pkgName)
        )
    }
}
