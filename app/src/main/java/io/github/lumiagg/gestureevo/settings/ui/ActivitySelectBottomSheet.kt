package io.github.lumiagg.gestureevo.settings.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleCoroutineScope
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.ui.miuix.MiuixCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class ActivityModel(
    val packageName: String,
    val activityName: String,
    val label: String,
    val appLabel: String,
    var isExported: Boolean
)

interface SelectActivityCallBack {
    fun selectCompleted(model: ActivityModel)
}

/**
 * Activity filter interface.
 * Returns a set of matching full Activity names for fast lookup.
 */
typealias ActivityFilter = (packageManager: PackageManager, targetPackage: String) -> Set<String>

class ActivitySelectBottomSheet(
    private val activity: Activity,
    private val packageName: String,
    private val coroutineScope: LifecycleCoroutineScope,
    private val selectActivityCallBack: SelectActivityCallBack,
    private val activityFilter: ActivityFilter? = null
) {
    @OptIn(ExperimentalFoundationApi::class)
    fun show() {
        showMiuixSheet(activity, dismissOnOutside = false) { dismiss ->
            var fullActivityList by remember { mutableStateOf<List<ActivityModel>>(emptyList()) }
            var visibleActivities by remember { mutableStateOf<List<ActivityModel>>(emptyList()) }
            var search by remember { mutableStateOf("") }
            var loading by remember { mutableStateOf(true) }
            val haptic = LocalHapticFeedback.current
            // Work is owned by the sheet; dismissing it cancels pending loading/search.
            LaunchedEffect(Unit) {
                try {
                    fullActivityList = withContext(Dispatchers.IO) {
                        val pm = activity.packageManager
                        if (activityFilter != null) loadFilteredActivities(pm) else loadPackageActivities(pm)
                    }
                } catch (e: PackageManager.NameNotFoundException) {
                    e.printStackTrace()
                } finally {
                    loading = false
                }
            }
            LaunchedEffect(search, fullActivityList) {
                delay(300)
                val query = search.trim().lowercase()
                visibleActivities = withContext(Dispatchers.Default) {
                    if (query.isEmpty()) fullActivityList else fullActivityList.filter { model ->
                        model.label.lowercase().contains(query) ||
                            model.appLabel.lowercase().contains(query) ||
                            model.activityName.lowercase().contains(query)
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                TextField(
                    value = search,
                    onValueChange = { search = it },
                    label = stringResource(R.string.app_select_name_hint),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp),
                )
                if (loading) androidx.compose.material3.LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(), color = MiuixTheme.colorScheme.primary,
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 500.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(visibleActivities, key = { it.activityName }) { model ->
                        // Keep the original mutable model: callers consume the manually overridden
                        // exported flag, not a copy reconstructed from PackageManager.
                        var isExported by remember(model) { mutableStateOf(model.isExported) }
                        MiuixCard {
                            Column(
                                Modifier.fillMaxWidth().combinedClickable(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        selectActivityCallBack.selectCompleted(model)
                                        dismiss()
                                    },
                                ).padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(model.label, modifier = Modifier.weight(1f), fontSize = 16.sp,
                                        lineHeight = 22.sp, fontWeight = FontWeight.Medium,
                                        color = MiuixTheme.colorScheme.onSurface)
                                    Text(
                                        stringResource(if (isExported) R.string.activity_exported_true else R.string.activity_exported_false),
                                        modifier = Modifier.heightIn(min = 44.dp)
                                            .background(MiuixTheme.colorScheme.secondaryContainer, RoundedCornerShape(6.dp))
                                            .combinedClickable(
                                                onClick = {},
                                                onLongClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    model.isExported = !model.isExported
                                                    isExported = model.isExported
                                                },
                                            ).padding(horizontal = 8.dp, vertical = 12.dp),
                                        fontSize = 12.sp, lineHeight = 16.sp,
                                        color = if (isExported) MiuixTheme.colorScheme.primary
                                            else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                                Text(stringResource(R.string.app_activity_label, model.appLabel, model.activityName),
                                    fontSize = 12.sp, lineHeight = 16.sp, fontFamily = FontFamily.Monospace,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            }
                        }
                    }
                }
            }
        }
    }
    private fun loadFilteredActivities(pm: PackageManager): List<ActivityModel> {
        val allowedActivities = activityFilter?.invoke(pm, packageName).orEmpty()
        if (allowedActivities.isEmpty()) return emptyList()

        val intents = listOf(
            Intent(Intent.ACTION_SEND).apply { type = "text/plain" },
            Intent(Intent.ACTION_SEND).apply { type = "image/*" },
            Intent(Intent.ACTION_VIEW)
        )

        val resolveList = LinkedHashMap<String, ActivityModel>()
        intents.forEach { intent ->
            pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
                .filter { it.activityInfo?.packageName == packageName }
                .filter { allowedActivities.contains(it.activityInfo?.name) }
                .forEach { resolveInfo ->
                    val model = createActivityModel(resolveInfo, pm)
                    resolveList[model.activityName] = model
                }
        }

        if (resolveList.isNotEmpty()) {
            return resolveList.values.sortedBy { !it.isExported }
        }

        return loadPackageActivities(pm).filter { allowedActivities.contains(it.activityName) }
    }

    private fun loadPackageActivities(pm: PackageManager): List<ActivityModel> {
        val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
        return packageInfo.activities?.map { activityInfo ->
            createActivityModel(activityInfo, pm)
        }?.sortedBy { !it.isExported } ?: emptyList()
    }

    private fun createActivityModel(activityInfo: ActivityInfo, pm: PackageManager): ActivityModel {
        val label = try {
            activityInfo.loadLabel(pm).toString()
        } catch (e: Exception) {
            activityInfo.name
        }
        val appLabel = try {
            activityInfo.applicationInfo.loadLabel(pm).toString()
        } catch (e: Exception) {
            activityInfo.packageName
        }

        return ActivityModel(
            packageName = activityInfo.packageName,
            activityName = activityInfo.name,
            label = label,
            appLabel = appLabel,
            isExported = activityInfo.exported
        )
    }

    private fun createActivityModel(resolveInfo: ResolveInfo, pm: PackageManager): ActivityModel {
        val activityInfo = resolveInfo.activityInfo
        val label = try {
            resolveInfo.loadLabel(pm)?.toString().orEmpty().ifEmpty {
                activityInfo.loadLabel(pm).toString()
            }
        } catch (e: Exception) {
            activityInfo.name
        }
        val appLabel = try {
            activityInfo.applicationInfo.loadLabel(pm).toString()
        } catch (e: Exception) {
            activityInfo.packageName
        }

        return ActivityModel(
            packageName = activityInfo.packageName,
            activityName = activityInfo.name,
            label = label,
            appLabel = appLabel,
            isExported = activityInfo.exported
        )
    }

}

// ============ Predefined Filters ============

object ActivityFilters {

    /**
     * Filters activities supporting text sharing.
     * Queries all activities capable of handling ACTION_SEND + text/plain.
     */
    fun textShareFilter(): ActivityFilter = { pm, targetPackage ->
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
        }
        pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .mapNotNull { it.activityInfo }
            .filter { it.packageName == targetPackage }
            .map { it.name }
            .toSet()
    }

    fun imageShareFilter(): ActivityFilter = { pm, targetPackage ->
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
        }
        pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .mapNotNull { it.activityInfo }
            .filter { it.packageName == targetPackage }
            .map { it.name }
            .toSet()
    }

    /**
     * Filters activities supporting specific Action and Type.
     */
    fun intentFilter(
        action: String,
        type: String? = null,
        data: android.net.Uri? = null
    ): ActivityFilter = { pm, targetPackage ->
        val intent = Intent(action).apply {
            type?.let { this.type = it }
            data?.let { this.data = it }
        }
        pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .mapNotNull { it.activityInfo }
            .filter { it.packageName == targetPackage }
            .map { it.name }
            .toSet()
    }

    /**
     * Filters activities supporting viewing specific MIME types.
     */
    fun viewTypeFilter(mimeType: String): ActivityFilter = { pm, targetPackage ->
        val intent = Intent(Intent.ACTION_VIEW).apply {
            type = mimeType
        }
        pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .mapNotNull { it.activityInfo }
            .filter { it.packageName == targetPackage }
            .map { it.name }
            .toSet()
    }

    /**
     * Filters activities supporting specific URL schemes.
     */
    fun urlSchemeFilter(scheme: String, host: String? = null): ActivityFilter =
        { pm, targetPackage ->
            val uriBuilder = android.net.Uri.Builder().scheme(scheme)
            host?.let { uriBuilder.authority(it) }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = uriBuilder.build()
            }
            pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
                .mapNotNull { it.activityInfo }
                .filter { it.packageName == targetPackage }
                .map { it.name }
                .toSet()
        }

    /**
     * Filters only exported activities.
     */
    fun exportedOnlyFilter(): ActivityFilter = { pm, targetPackage ->
        try {
            val packageInfo = pm.getPackageInfo(targetPackage, PackageManager.GET_ACTIVITIES)
            packageInfo.activities?.filter { it.exported }?.map { it.name }?.toSet() ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    /**
     * Custom name matching filter.
     */
    fun nameContainsFilter(keyword: String, ignoreCase: Boolean = true): ActivityFilter =
        { pm, targetPackage ->
            try {
                val packageInfo = pm.getPackageInfo(targetPackage, PackageManager.GET_ACTIVITIES)
                packageInfo.activities?.filter {
                    it.name.contains(keyword, ignoreCase)
                }?.map { it.name }?.toSet() ?: emptySet()
            } catch (e: Exception) {
                emptySet()
            }
        }

    /**
     * Combines multiple filters (intersection).
     */
    fun intersect(vararg filters: ActivityFilter): ActivityFilter = { pm, targetPackage ->
        if (filters.isEmpty()) {
            emptySet()
        } else {
            filters.map { it(pm, targetPackage) }
                .reduce { acc, set -> acc.intersect(set) }
        }
    }

    /**
     * Combines multiple filters (union).
     */
    fun union(vararg filters: ActivityFilter): ActivityFilter = { pm, targetPackage ->
        filters.flatMap { it(pm, targetPackage) }.toSet()
    }
}
