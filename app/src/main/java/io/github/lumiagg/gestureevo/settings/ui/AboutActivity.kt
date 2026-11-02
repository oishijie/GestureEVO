package io.github.lumiagg.gestureevo.settings.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.ui.compose.MiuixEvoTheme
import io.github.lumiagg.gestureevo.ui.miuix.MiuixCard
import io.github.lumiagg.gestureevo.ui.miuix.MiuixScreen
import top.yukonga.miuix.kmp.preference.ArrowPreference

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
            MiuixEvoTheme {
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


@Composable
private fun AboutScreen(
    onBack: () -> Unit,
    openLink: (String) -> Unit,
    onLicenses: () -> Unit,
) {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }

    MiuixScreen(
        title = stringResource(R.string.settings_about_title),
        onBack = onBack,
    ) {
        item {
            MiuixCard {
                ArrowPreference(
                    title = stringResource(R.string.app_name),
                    summary = version,
                    onClick = { openLink(RELEASES_URL) },
                )
            }
        }
        item {
            MiuixCard {
                ArrowPreference(
                    title = stringResource(R.string.about_group_project),
                    summary = stringResource(R.string.about_maintainer_name),
                    onClick = {},
                )
                ArrowPreference(
                    title = stringResource(R.string.about_repository_title),
                    onClick = { openLink(REPO_URL) },
                )
                ArrowPreference(
                    title = stringResource(R.string.about_upstream_title),
                    onClick = { openLink(UPSTREAM_URL) },
                )
                ArrowPreference(
                    title = stringResource(R.string.about_contributors),
                    onClick = {},
                )
            }
        }
        item {
            MiuixCard {
                ArrowPreference(
                    title = stringResource(R.string.about_contact),
                    onClick = { openLink(ISSUES_URL) },
                )
                ArrowPreference(
                    title = stringResource(R.string.about_privacy_policy),
                    onClick = { openLink(PRIVACY_POLICY_URL) },
                )
                ArrowPreference(
                    title = stringResource(R.string.about_user_agreement),
                    onClick = { openLink(USER_AGREEMENT_URL) },
                )
                ArrowPreference(
                    title = stringResource(R.string.about_open_source_licenses),
                    onClick = onLicenses,
                )
            }
        }
    }
}
