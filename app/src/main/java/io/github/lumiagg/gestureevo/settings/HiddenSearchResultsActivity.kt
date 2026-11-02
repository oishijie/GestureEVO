package io.github.lumiagg.gestureevo.settings

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.db.HiddenSearchResultModel
import io.github.lumiagg.gestureevo.db.getHiddenSearchResultDao
import io.github.lumiagg.gestureevo.search.typeLabelRes
import io.github.lumiagg.gestureevo.ui.compose.MiuixEvoTheme
import io.github.lumiagg.gestureevo.ui.miuix.MiuixCard
import io.github.lumiagg.gestureevo.ui.miuix.MiuixScreen
import io.github.lumiagg.gestureevo.utils.icon.IconHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 已隐藏的搜索结果：在所有查询中被隐藏的结果可在此恢复显示。
 *
 * 从搜索页菜单进入；列表随数据库实时刷新。
 */
class HiddenSearchResultsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MiuixEvoTheme {
                HiddenResultsScreen(onBack = { onBackPressedDispatcher.onBackPressed() })
            }
        }
    }

    private fun restoreHiddenResult(result: HiddenSearchResultModel) {
        lifecycleScope.launch {
            try {
                getHiddenSearchResultDao().delete(result.resultKey)
                Toast.makeText(
                    this@HiddenSearchResultsActivity,
                    R.string.hidden_search_result_restored,
                    Toast.LENGTH_SHORT,
                ).show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    this@HiddenSearchResultsActivity,
                    R.string.hidden_search_result_update_failed,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    @Composable
    private fun HiddenResultsScreen(onBack: () -> Unit) {
        val context = LocalContext.current
        var results by remember { mutableStateOf<List<HiddenSearchResultModel>>(emptyList()) }

        LaunchedEffect(Unit) {
            getHiddenSearchResultDao().observeAll().collect { list ->
                results = list
            }
        }

        MiuixScreen(
            title = stringResource(R.string.hidden_search_results_title),
            onBack = onBack,
        ) {
            if (results.isEmpty()) {
                item {
                    MiuixCard {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.hidden_search_results_empty),
                                fontSize = 14.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            } else {
                item {
                    MiuixCard {
                        results.forEach { model ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                val icon = remember(model.resultKey) {
                                    if (model.resultKey.startsWith("app:")) {
                                        val packageName =
                                            model.resultKey.substringAfter(':').substringAfter(':')
                                        runCatching {
                                            IconHelper.getIcon(context, packageName)
                                        }.getOrNull()
                                    } else {
                                        null
                                    }
                                }
                                if (icon != null) {
                                    androidx.compose.foundation.Image(
                                        bitmap = remember(icon) { icon.toBitmap().asImageBitmap() },
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp).clip(CircleShape),
                                        contentScale = ContentScale.Fit,
                                    )
                                } else {
                                    Text(
                                        text = model.displayName.take(1),
                                        fontSize = 16.sp,
                                        modifier = Modifier.size(32.dp),
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(text = model.displayName, fontSize = 16.sp)
                                    Text(
                                        text = stringResource(model.typeLabelRes()),
                                        fontSize = 12.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                                IconButton(onClick = { restoreHiddenResult(model) }) {
                                    Icon(
                                        imageVector = ImageVector.vectorResource(
                                            R.drawable.undo_24px
                                        ),
                                        contentDescription = stringResource(
                                            R.string.hidden_search_result_restore_description,
                                            model.displayName,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
