package io.github.lumiagg.gestureevo.settings

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.ui.compose.MiuixEvoTheme
import io.github.lumiagg.gestureevo.ui.miuix.MiuixCard
import io.github.lumiagg.gestureevo.ui.miuix.MiuixScreen
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

class SettingsSearchActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MiuixEvoTheme {
                SearchScreen(
                    onBack = { finish() },
                    onOpen = { entry ->
                        startActivity(SettingsSearchNavigator.buildIntent(this, entry))
                        finish()
                    },
                )
            }
        }
    }

    @Composable
    private fun SearchScreen(
        onBack: () -> Unit,
        onOpen: (SettingsSearchEntry) -> Unit,
    ) {
        val context = LocalContext.current
        var query by remember { mutableStateOf("") }
        val entries = remember(query) { SettingsSearchRegistry.search(context, query) }

        MiuixScreen(
            title = stringResource(R.string.settings_search_hint),
            onBack = onBack,
        ) {
            item {
                MiuixCard {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            label = stringResource(R.string.settings_search_hint),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        )
                    }
                }
            }
            if (entries.isEmpty()) {
                item {
                    MiuixCard {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.settings_search_empty_title),
                                fontSize = 14.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            } else {
                item {
                    MiuixCard {
                        entries.forEach { entry ->
                            ArrowPreference(
                                title = stringResource(entry.titleResId),
                                summary = entry.summaryResId?.let { stringResource(it) }
                                    ?.ifBlank { null }
                                    ?: stringResource(entry.pageTitleResId),
                                onClick = { onOpen(entry) },
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
