package io.github.lumiagg.gestureevo.settings

import android.os.Bundle
import android.text.format.DateUtils
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.db.APPModel
import io.github.lumiagg.gestureevo.db.getAPPDao
import io.github.lumiagg.gestureevo.ui.compose.MiuixEvoTheme
import io.github.lumiagg.gestureevo.ui.miuix.MiuixCard
import io.github.lumiagg.gestureevo.ui.miuix.MiuixScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DataStatsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MiuixEvoTheme { StatsScreen(onBack = { onBackPressedDispatcher.onBackPressed() }) }
        }
    }

    private data class Stats(val totalOpenCount: Long, val openedAppCount: Int, val averageOpenCount: Double, val latestUsageTime: Long, val topOpened: List<APPModel>, val recentUsed: List<APPModel>)

    @Composable
    private fun StatsScreen(onBack: () -> Unit) {
        val context = LocalContext.current
        var stats by remember { mutableStateOf<Stats?>(null) }
        var refreshToken by remember { mutableStateOf(0) }
        var isRefreshing by remember { mutableStateOf(false) }
        LaunchedEffect(refreshToken) {
            isRefreshing = true
            stats = withContext(Dispatchers.IO) {
                val allApps = getAPPDao().getAll()
                val totalOpenCount = allApps.sumOf { it.openCount }
                val openedApps = allApps.filter { it.openCount > 0 }
                val recentUsedApps = allApps.filter { it.lastUsageTime > 0L }.sortedByDescending { it.lastUsageTime }.take(5)
                Stats(totalOpenCount, openedApps.size, if (openedApps.isEmpty()) 0.0 else totalOpenCount.toDouble() / openedApps.size, recentUsedApps.firstOrNull()?.lastUsageTime ?: 0L, openedApps.sortedByDescending { it.openCount }.take(5), recentUsedApps)
            }
            isRefreshing = false
        }
        val numberFormat = remember { NumberFormat.getIntegerInstance() }
        fun formatTime(timestamp: Long): String {
            if (timestamp <= 0L) return context.getString(R.string.data_stats_empty_value)
            val relative = DateUtils.getRelativeTimeSpanString(timestamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
            val absolute = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
            return context.getString(R.string.data_stats_last_active_format, relative, absolute)
        }
        MiuixScreen(title = stringResource(R.string.data_stats_title), onBack = onBack, actions = {
            IconButton(onClick = { if (!isRefreshing) refreshToken++ }) {
                Icon(painter = painterResource(R.drawable.baseline_refresh_24), contentDescription = stringResource(R.string.data_stats_refresh))
            }
        }) {
            item { MiuixCard { Column(Modifier.fillMaxWidth().padding(16.dp)) { Text(numberFormat.format(stats?.totalOpenCount ?: 0L), fontSize = 28.sp, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(4.dp)); Text(stringResource(R.string.data_stats_total_open_label), fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary) } } }
            item { MiuixCard { StatRow(stringResource(R.string.data_stats_tracked_apps_label), numberFormat.format(stats?.openedAppCount ?: 0)); StatRow(stringResource(R.string.data_stats_avg_open_label), stringResource(R.string.data_stats_avg_open_format, String.format(Locale.getDefault(), "%.1f", stats?.averageOpenCount ?: 0.0))); StatRow(stringResource(R.string.data_stats_last_active_label), if ((stats?.latestUsageTime ?: 0L) > 0L) formatTime(stats!!.latestUsageTime) else stringResource(R.string.data_stats_empty_value)) } }
            item { MiuixCard { Column(Modifier.fillMaxWidth().padding(16.dp)) { Text(stringResource(R.string.data_stats_top_opened_title), fontSize = 16.sp, fontWeight = FontWeight.Medium); Spacer(Modifier.height(12.dp)); val top = stats?.topOpened.orEmpty(); if (top.isEmpty()) Text(stringResource(R.string.data_stats_no_usage_data), fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary) else { val highest = top.first().openCount.coerceAtLeast(1); top.forEachIndexed { index, app -> Column(Modifier.padding(bottom = 12.dp)) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.data_stats_rank_format, index + 1), fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.width(28.dp)); Text(app.displayName, fontSize = 14.sp, modifier = Modifier.weight(1f)); Text(stringResource(R.string.data_stats_open_count_short_format, numberFormat.format(app.openCount)), fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }; Spacer(Modifier.height(6.dp)); LinearProgressIndicator(progress = { app.openCount.toFloat() / highest }, modifier = Modifier.fillMaxWidth()) } } } } } }
            item { MiuixCard { Column(Modifier.fillMaxWidth().padding(16.dp)) { Text(stringResource(R.string.data_stats_recent_title), fontSize = 16.sp, fontWeight = FontWeight.Medium); Spacer(Modifier.height(12.dp)); val recent = stats?.recentUsed.orEmpty(); if (recent.isEmpty()) Text(stringResource(R.string.data_stats_no_recent_data), fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary) else recent.forEach { app -> Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text(app.displayName, fontSize = 14.sp); Text(formatTime(app.lastUsageTime), fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary) } } } } }
        }
    }
    @Composable private fun StatRow(label: String, value: String) { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text(label, fontSize = 16.sp); Text(value, fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary) } }
    private val APPModel.displayName: String get() = name.ifBlank { pkgName }
}
