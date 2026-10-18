package io.github.lumiagg.gestureevo.settings.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.databinding.ActivityAboutBinding

/**
 * 关于页：项目归属、反馈入口与法律条款。
 *
 * 页头与其余二级页完全一致：CollapsingToolbarLayout 展开态大标题 + 圆形返回钮，
 * 分组统一用 [SettingsSectionCard]（白卡细描边 + 扁平行 + 行间分隔线）。
 */
class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        installPageHeader(
            this,
            binding.toolbar,
            binding.toolbarLayout,
            R.string.settings_about_title
        )

        binding.aboutVersion.apply {
            text = getString(R.string.about_version_label, versionName())
            setOnClickListener { openLink(RELEASES_URL) }
        }

        binding.aboutMaintainer.setOnClickListener { openLink(OWNER_URL) }
        binding.aboutRepository.setOnClickListener { openLink(REPO_URL) }
        binding.aboutUpstream.setOnClickListener { openLink(UPSTREAM_URL) }
        binding.aboutFeedback.setOnClickListener { openLink(ISSUES_URL) }
        binding.aboutPrivacyPolicy.setOnClickListener { openLink(PRIVACY_POLICY_URL) }
        binding.aboutUserAgreement.setOnClickListener { openLink(USER_AGREEMENT_URL) }
        binding.aboutOpenSourceLicenses.setOnClickListener { showOpenSourceLicenses(this) }
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

    private companion object {
        /** 当前维护者主页。 */
        const val OWNER_URL = "https://github.com/oishijie"

        /** 当前仓库。 */
        const val REPO_URL = "https://github.com/oishijie/GestureEVO"

        const val RELEASES_URL = "$REPO_URL/releases"
        const val ISSUES_URL = "$REPO_URL/issues"

        /** 上游原项目。 */
        const val UPSTREAM_URL = "https://github.com/LumiaGG/GestureEVO"

        /** 隐私政策与用户协议沿用上游仓库的文件。 */
        const val PRIVACY_POLICY_URL =
            "https://github.com/LumiaGG/GestureEVO/blob/main/PRIVACY_POLICY.md"
        const val USER_AGREEMENT_URL =
            "https://github.com/LumiaGG/GestureEVO/blob/main/USER_AGREEMENT.md"
    }
}
