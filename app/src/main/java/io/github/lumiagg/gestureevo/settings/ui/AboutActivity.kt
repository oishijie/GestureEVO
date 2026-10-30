package io.github.lumiagg.gestureevo.settings.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.ui.compose.EvoTheme
import io.github.lumiagg.gestureevo.ui.compose.RowSpec
import io.github.lumiagg.gestureevo.ui.compose.SectionCard
import io.github.lumiagg.gestureevo.ui.compose.SectionHeader

/**
 * 关于页（Compose 版，二级页迁移模板）。
 *
 * 页面骨架：LargeTopAppBar（大标题折叠 + 圆形返回钮）+ LazyColumn
 * （版本卡 + SectionHeader/SectionCard 分组）。其他列表型二级页按此模板迁移。
 */
private const val RELEASES_URL = "https://github.com/oishijie/GestureEVO/releases"
private const val ISSUES_URL = "https://github.com/oishijie/GestureEVO/issues"
private const val REPO_URL = "https://github.com/oishijie/GestureEVO"
private const val UPSTREAM_URL = "https://github.com/LumiaGG/GestureEVO"
private const val PRIVACY_POLICY_URL =
    "https://github.com/LumiaGG/GestureEVO/blob/main/PRIVACY_POLICY.md"
private const val USER_AGREEMENT_URL =
    "https://github.com/LumiaGG/GestureEVO/blob/main/USER_AGREEMENT.md"

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EvoTheme {
                AboutScreen(
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                    openLink = ::openLink,
                    onLicenses = { showOpenSourceLicenses(this) },
                )
            }
        }
    }

    private fun versionName(): String = runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName ?: ""
    }.getOrDefault("")

    private fun openLink(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, R.string.about_link_no_app, Toast.LENGTH_SHORT).show()
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutScreen(
    onBack: () -> Unit,
    openLink: (String) -> Unit,
    onLicenses: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings_about_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_arrow_back_24),
                            contentDescription = stringResource(R.string.settings_about_title),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SectionCard(
                    rows = listOf(
                        RowSpec(
                            iconRes = R.drawable.ic_baseline_insert_link_24,
                            title = stringResource(R.string.app_name),
                            subtitle = version,
                        ) { openLink(RELEASES_URL) },
                    )
                )
            }
            item { SectionHeader(stringResource(R.string.about_group_project)) }
            item {
                SectionCard(
                    rows = listOf(
                        RowSpec(
                            iconRes = R.drawable.baseline_info_24,
                            title = stringResource(R.string.about_maintainer_title),
                            subtitle = stringResource(R.string.about_maintainer_name),
                            showChevron = false,
                        ) {},
                        RowSpec(
                            iconRes = R.drawable.ic_baseline_insert_link_24,
                            title = stringResource(R.string.about_repository_title),
                        ) { openLink(REPO_URL) },
                        RowSpec(
                            iconRes = R.drawable.ic_baseline_insert_link_24,
                            title = stringResource(R.string.about_upstream_title),
                        ) { openLink(UPSTREAM_URL) },
                        RowSpec(
                            iconRes = R.drawable.baseline_menu_24,
                            title = stringResource(R.string.about_contributors),
                            showChevron = false,
                        ) {},
                    )
                )
            }
            item { SectionHeader(stringResource(R.string.about_group_legal)) }
            item {
                SectionCard(
                    rows = listOf(
                        RowSpec(
                            iconRes = R.drawable.baseline_edit_24,
                            title = stringResource(R.string.about_contact),
                        ) { openLink(ISSUES_URL) },
                        RowSpec(
                            iconRes = R.drawable.icon_lock,
                            title = stringResource(R.string.about_privacy_policy),
                        ) { openLink(PRIVACY_POLICY_URL) },
                        RowSpec(
                            iconRes = R.drawable.baseline_select_all_24,
                            title = stringResource(R.string.about_user_agreement),
                        ) { openLink(USER_AGREEMENT_URL) },
                        RowSpec(
                            iconRes = R.drawable.baseline_menu_24,
                            title = stringResource(R.string.about_open_source_licenses),
                        ) { onLicenses() },
                    )
                )
            }
        }
    }

}
