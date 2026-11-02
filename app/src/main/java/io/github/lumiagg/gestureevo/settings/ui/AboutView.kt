package io.github.lumiagg.gestureevo.settings.ui

import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lumiagg.gestureevo.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val PROJECT_LICENSE_ASSET = "licenses/project_license.txt"
private const val OPEN_SOURCE_LICENSES_ASSET = "licenses/open_source_notices.txt"

/** Shows complete license texts without blocking the UI thread on asset IO. */
fun showOpenSourceLicenses(activity: AppCompatActivity) {
    showMiuixSheet(activity) { dismiss ->
        var notice by remember { mutableStateOf("") }
        LaunchedEffect(Unit) {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    listOf(PROJECT_LICENSE_ASSET, OPEN_SOURCE_LICENSES_ASSET).joinToString("\n\n") { asset ->
                        activity.assets.open(asset).bufferedReader(Charsets.UTF_8).use { it.readText() }
                    }
                }
            }
            loaded.onSuccess { notice = it }.onFailure {
                Toast.makeText(activity, R.string.about_open_source_licenses_load_failed, Toast.LENGTH_SHORT).show()
                dismiss()
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.about_open_source_licenses),
                modifier = Modifier.padding(top = 24.dp), fontSize = 17.sp,
                lineHeight = 22.sp, fontWeight = FontWeight.Medium)
            Text(stringResource(R.string.about_open_source_licenses_subtitle), fontSize = 14.sp,
                lineHeight = 20.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Column(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 500.dp)
                .verticalScroll(rememberScrollState())) {
                SelectionContainer {
                    Text(notice, fontSize = 14.sp, lineHeight = 21.sp,
                        color = MiuixTheme.colorScheme.onSurface)
                }
            }
            TextButton(text = stringResource(R.string.screen_search_close),
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp).heightIn(min = 44.dp),
                onClick = dismiss)
        }
    }
}
