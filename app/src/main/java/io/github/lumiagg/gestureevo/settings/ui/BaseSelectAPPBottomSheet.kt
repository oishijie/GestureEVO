package io.github.lumiagg.gestureevo.settings.ui

import android.app.Activity
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.RecyclerView
import com.drake.brv.BindingAdapter
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.db.APPModel
import io.github.lumiagg.gestureevo.db.getAPPDao
import io.github.lumiagg.gestureevo.utils.icon.IconHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

interface SelectAPPCallBack {
    fun selectCompleted(model: APPModel) {}
}

abstract class BaseSelectAPPBottomSheet(
    protected val activity: Activity,
    protected val coroutineScope: LifecycleCoroutineScope,
    protected val selectAPPCallBack: SelectAPPCallBack
) {
    // Source-compatible legacy extension surface. The Compose path never creates these Views
    // or invokes the XML/binding hooks (LinkHandler also supplies its own Compose show()).
    protected lateinit var bottomSheetDialog: BottomSheetDialog
    protected lateinit var recyclerView: RecyclerView
    protected var fullAppList: List<APPModel> = emptyList()
    protected var searchJob: Job? = null
    protected var bindingAdapter: BindingAdapter? = null
    protected var appFlags = APPModel.FLAG_USER

    private var dismissCurrentSheet: (() -> Unit)? = null
    private var generation = 0
    private var reloadRevision by mutableStateOf(0)
    protected val isSheetActive: Boolean get() = dismissCurrentSheet != null &&
        !activity.isFinishing && !activity.isDestroyed

    open fun show() {
        dismissCurrentSheet?.invoke()
        val session = ++generation
        showMiuixSheet(activity, dismissOnOutside = false) { dismiss ->
            DisposableEffect(session) {
                dismissCurrentSheet = dismiss
                onDispose {
                    if (generation == session) dismissCurrentSheet = null
                    searchJob?.cancel()
                }
            }
            var query by rememberSaveable { mutableStateOf("") }
            var flag by rememberSaveable { mutableStateOf(appFlags) }
            var apps by remember { mutableStateOf<List<APPModel>>(emptyList()) }
            var loaded by remember { mutableStateOf<List<APPModel>>(emptyList()) }
            var loading by remember { mutableStateOf(true) }
            var filtering by remember { mutableStateOf(false) }
            var selecting by remember { mutableStateOf(false) }
            var error by remember { mutableStateOf<String?>(null) }
            val scope = rememberCoroutineScope()

            LaunchedEffect(flag, reloadRevision) {
                loading = true
                apps = emptyList()
                error = null
                appFlags = flag
                try {
                    val requestedFlag = getAppFlag()
                    val result = withContext(Dispatchers.IO) { loadAppModels(requestedFlag) }
                    fullAppList = result
                    loaded = result
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    fullAppList = emptyList()
                    loaded = emptyList()
                    error = failure.localizedMessage.orEmpty()
                } finally {
                    loading = false
                }
            }
            // Changing filter/query cancels the previous effect, so an old IO/search result
            // cannot replace the latest list; the query is reapplied after every reload.
            LaunchedEffect(query, loaded, loading) {
                if (loading) return@LaunchedEffect
                filtering = true
                try {
                    val text = query.trim().lowercase()
                    if (text.isNotEmpty()) delay(300)
                    apps = withContext(Dispatchers.Default) { filterApps(text) }
                } finally {
                    filtering = false
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                Text(stringResource(R.string.app_select_name_hint),
                    modifier = Modifier.padding(top = 24.dp, bottom = 12.dp),
                    fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
                TextField(value = query, onValueChange = { query = it }, singleLine = true,
                    label = stringResource(R.string.app_select_name_hint),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp))
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    listOf(APPModel.FLAG_USER to R.string.app_select_user_apps,
                        APPModel.FLAG_SYSTEM to R.string.app_select_system_apps).forEach { (value, label) ->
                        TextButton(text = stringResource(label),
                            modifier = Modifier.weight(1f).heightIn(min = 44.dp), enabled = !selecting,
                            colors = if (flag == value) ButtonDefaults.textButtonColorsPrimary()
                                else ButtonDefaults.textButtonColors(), onClick = { flag = value })
                    }
                }
                error?.let {
                    Text(stringResource(R.string.shortcut_load_failed, it),
                        fontSize = 12.sp, lineHeight = 16.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
                if (loading || filtering || selecting) {
                    androidx.compose.material3.LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(), color = MiuixTheme.colorScheme.primary)
                }
                LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 500.dp)) {
                    // Include index: legacy shortcut providers may expose multiple activities
                    // for the same package, and APPModel can also represent multiple users.
                    itemsIndexed(apps, key = { index, app -> "${app.pkgName}:${app.userId}:$index" }) { _, app ->
                        AppRow(app, enabled = !loading && !filtering && !selecting) {
                            if (!selecting && !loading && !filtering && generation == session) {
                                selecting = true
                                scope.launch {
                                    try {
                                        selectApp(app)
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (failure: Exception) {
                                        error = failure.localizedMessage.orEmpty()
                                    } finally {
                                        selecting = false
                                    }
                                }
                            }
                        }
                    }
                }
                TextButton(text = stringResource(android.R.string.cancel),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp).heightIn(min = 44.dp),
                    onClick = { dismissSheet() })
            }
        }
    }

    /** Runs on IO; subclasses return data only, never access Views or Compose state here. */
    protected open suspend fun loadAppModels(flag: Int): List<APPModel> = getAPPDao().getAll(flag)

    /** Runs in the composition's cancellable scope on Main. Multi-select overrides stay open. */
    protected open suspend fun selectApp(model: APPModel) { onItemClick(model) }

    protected fun dismissSheet() {
        val dismiss = dismissCurrentSheet
        dismissCurrentSheet = null
        dismiss?.invoke()
    }

    protected open fun selectedIcon(model: APPModel): Drawable? = null

    @Composable
    protected open fun AppRow(model: APPModel, enabled: Boolean, onClick: () -> Unit) {
        val bitmap by produceState<ImageBitmap?>(null, model.pkgName, model.userId) {
            value = withContext(Dispatchers.IO) {
                try {
                    IconHelper.getIcon(activity.applicationContext, model.pkgName)
                        ?.toBitmap(64, 64)?.asImageBitmap()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) { null }
            }
        }
        val overlay = selectedIcon(model)
        val badge = remember(overlay) { overlay?.toBitmap(48, 48)?.asImageBitmap() }
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.size(44.dp)) }
                badge?.let { Image(it, contentDescription = null, modifier = Modifier.size(44.dp)) }
            }
            Column(Modifier.weight(1f)) {
                ArrowPreference(title = model.name, summary = model.pkgName,
                    enabled = enabled, onClick = onClick)
            }
        }
    }

    // Retained protected legacy signatures; no XML/RecyclerView code runs in show().
    protected open fun initBottomSheet() {}
    protected open fun setupBottomSheetBehavior() {}
    protected open fun setupRecyclerView() {}
    protected open fun setupFilter() {}
    protected open fun setupSearch() {}
    protected open fun loadApps() { reloadRevision++ }
    protected open fun showDialog() {}
    protected abstract fun onBindItem(holder: BindingAdapter.BindingViewHolder)
    protected open fun getGridSpanCount(): Int = 3
    protected open fun getItemLayoutId(): Int = R.layout.app_select_list_item
    protected open fun getAppFlag(): Int = appFlags
    protected open fun onItemClick(model: APPModel) {
        selectAPPCallBack.selectCompleted(model)
        dismissSheet()
    }
    protected open fun filterApps(searchText: String): List<APPModel> = fullAppList.filter { app ->
        app.name.lowercase().contains(searchText) ||
            app.pkgName.lowercase().contains(searchText) ||
            app.pinyinFK.lowercase().contains(searchText) ||
            app.initialsFK.lowercase().contains(searchText) ||
            app.pinyinDK.lowercase().contains(searchText) ||
            app.initialsDK.lowercase().contains(searchText) ||
            app.pinyinT9.contains(searchText) || app.initialsT9.contains(searchText)
    }
    protected open fun customizeRecyclerView(adapter: BindingAdapter) {}
}
