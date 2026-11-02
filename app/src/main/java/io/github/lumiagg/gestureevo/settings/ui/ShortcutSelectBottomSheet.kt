package io.github.lumiagg.gestureevo.settings.ui

import androidx.compose.foundation.Image
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import io.github.lumiagg.gestureevo.ui.miuix.MiuixCard
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.db.getAPPDao
import io.github.lumiagg.gestureevo.gesture.config.GestureActionType
import io.github.lumiagg.gestureevo.utils.ActionProxy
import io.github.lumiagg.gestureevo.utils.icon.IconHelper
import io.github.lumiagg.gestureevo.utils.uriBuilder
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder

@JsonClass(generateAdapter = true)
data class ShortcutResponse(
    @Json(name = "name") val name: String,
    @Json(name = "pkg") val packageName: String,
    @Json(name = "uploadedBy") val uploadedBy: String,
    @Json(name = "scheme") val scheme: String,
    @Json(name = "introduction") val introduction: String
)

data class ShortcutModel(
    val name: String,
    val packageName: String,
    val uploadedBy: String,
    var scheme: String,
    val introduction: String,
    var appName: String = "",
    var installed: Boolean = false
)

interface SelectShortcutCallback {
    fun selectCompleted(model: ShortcutModel) {}
    fun onError(message: String) {}
}

class LocalUserShortcutSheet(
    private val activity: AppCompatActivity,
    private val coroutineScope: LifecycleCoroutineScope,
    private val selectShortcutCallback: SelectShortcutCallback
) {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    companion object {
        private const val LOCAL_SHORTCUTS_ASSET = "shortcuts/other_user_shortcuts.json"
    }

    fun show() {
        showMiuixSheet(activity, dismissOnOutside = false) { dismiss ->
            var fullShortcutList by remember { mutableStateOf<List<ShortcutModel>>(emptyList()) }
            var visibleShortcuts by remember { mutableStateOf<List<ShortcutModel>>(emptyList()) }
            var search by remember { mutableStateOf("") }
            var loading by remember { mutableStateOf(true) }
            LaunchedEffect(Unit) {
                try {
                    fullShortcutList = withContext(Dispatchers.IO) {
                        val shortcuts = loadLocalShortcutResponses().map(::convertToModel)
                        enrichShortcutInfo(shortcuts)
                        // Retain the asset order; only installed applications are selectable.
                        shortcuts.filter { it.installed }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    e.printStackTrace()
                    selectShortcutCallback.onError(activity.getString(R.string.shortcut_load_failed, e.message ?: ""))
                } finally {
                    loading = false
                }
            }
            LaunchedEffect(search, fullShortcutList) {
                delay(300)
                val query = search.trim().lowercase()
                visibleShortcuts = withContext(Dispatchers.Default) {
                    if (query.isEmpty()) fullShortcutList else fullShortcutList.filter { shortcut ->
                        shortcut.appName.lowercase().contains(query) ||
                            shortcut.name.lowercase().contains(query) ||
                            shortcut.introduction.lowercase().contains(query)
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                TextField(value = search, onValueChange = { search = it },
                    label = stringResource(R.string.app_select_name_hint), singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp))
                if (loading) androidx.compose.material3.LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(), color = MiuixTheme.colorScheme.primary,
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 500.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Asset entries may share names or URIs, so do not impose a new uniqueness rule.
                    items(visibleShortcuts) { model ->
                        ShortcutItem(model, dismiss)
                    }
                }
            }
        }
    }

    @Composable
    private fun ShortcutItem(model: ShortcutModel, dismiss: () -> Unit) {
        var icon by remember(model.packageName) { mutableStateOf<ImageBitmap?>(null) }
        LaunchedEffect(model.packageName) {
            icon = withContext(Dispatchers.IO) {
                IconHelper.getIcon(activity, model.packageName)?.toBitmap(48, 48)?.asImageBitmap()
            }
        }
        MiuixCard {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    icon?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.size(24.dp)) }
                    Text(model.appName, modifier = Modifier.weight(1f), maxLines = 1,
                        overflow = TextOverflow.Ellipsis, fontSize = 16.sp, lineHeight = 22.sp,
                        fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.onSurface)
                }
                Text(stringResource(R.string.shortcut_added_by, model.uploadedBy),
                    fontSize = 12.sp, lineHeight = 16.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Text(model.name, modifier = Modifier.padding(top = 4.dp), fontSize = 16.sp,
                    lineHeight = 22.sp, color = MiuixTheme.colorScheme.primary)
                if (model.introduction.isNotEmpty()) Text(model.introduction, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, fontSize = 14.sp, lineHeight = 20.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Text(model.scheme, maxLines = 1, overflow = TextOverflow.MiddleEllipsis,
                    fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    TextButton(text = stringResource(R.string.shortcut_preview),
                        modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                        onClick = {
                            val link = uriBuilder(GestureActionType.OTHER_SHORTCUT)
                                .appendQueryParameter("pkg", model.packageName)
                                .appendQueryParameter("uri", model.scheme)
                                .appendQueryParameter("newWindow", true.toString())
                                .build()
                            activity.lifecycleScope.launch { ActionProxy(activity, link.toString()).execute() }
                        })
                    TextButton(text = stringResource(R.string.shortcut_install),
                        modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        onClick = {
                            selectShortcutCallback.selectCompleted(model)
                            dismiss()
                        })
                }
            }
        }
    }
    private suspend fun loadLocalShortcutResponses(): List<ShortcutResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val jsonString = activity.assets.open(LOCAL_SHORTCUTS_ASSET).bufferedReader().use {
                    it.readText()
                }
                val adapter = moshi.adapter<List<ShortcutResponse>>(
                    Types.newParameterizedType(List::class.java, ShortcutResponse::class.java)
                )
                adapter.fromJson(jsonString) ?: emptyList()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    selectShortcutCallback.onError(
                        activity.getString(R.string.shortcut_load_failed, e.message ?: "")
                    )
                }
                emptyList()
            }
        }
    }

    private fun convertToModel(response: ShortcutResponse) = ShortcutModel(
        name = response.name,
        packageName = response.packageName,
        uploadedBy = response.uploadedBy,
        scheme = response.scheme,
        introduction = response.introduction
    )

    private fun parUri(scheme: String): String {
        val uri = scheme.toUri()
        uri.getQueryParameter("url")?.let { encodedUrl ->
            try {
                val decodedUrl = URLDecoder.decode(encodedUrl, "UTF-8")
                return decodedUrl
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return ""
    }

    private suspend fun enrichShortcutInfo(shortcuts: List<ShortcutModel>) {
        shortcuts.forEach { shortcut ->
            val appModel = getAPPDao().findByPkg(shortcut.packageName)
            shortcut.appName = appModel?.name ?: shortcut.packageName
            shortcut.installed = appModel != null
            shortcut.scheme = parUri(shortcut.scheme)
        }
    }

}
