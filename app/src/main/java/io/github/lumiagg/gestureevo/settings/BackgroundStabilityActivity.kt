package io.github.lumiagg.gestureevo.settings

import io.github.lumiagg.gestureevo.settings.ui.installPageHeader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.ImageViewCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.color.MaterialColors
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.databinding.ActivityBackgroundStabilityBinding
import io.github.lumiagg.gestureevo.databinding.ItemSetupCheckRowBinding
import io.github.lumiagg.gestureevo.utils.readKey
import io.github.lumiagg.gestureevo.utils.saveKey
import io.github.lumiagg.gestureevo.utils.settingsStore
import kotlinx.coroutines.launch

class BackgroundStabilityActivity : AppCompatActivity() {

    companion object {
        const val HIDE_TASK = "key_hide_task"
    }

    private lateinit var binding: ActivityBackgroundStabilityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityBackgroundStabilityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.adbCommandText.text = getString(R.string.background_stability_adb_command, packageName)

        setupToolbar()
        setupButtons()
        setupPermissionCard()
        SettingsSearchNavigator.focusAnchorIfNeeded(binding.root, intent)
    }

    override fun onResume() {
        super.onResume()
        // 用户可能刚在系统设置里改完权限，回来必须重新检测。
        refreshPermissions()
    }

    private fun setupToolbar() {
        installPageHeader(this, binding.toolbar, binding.toolbarLayout, R.string.background_stability_title)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupButtons() {
        binding.copyAdbButton.setOnClickListener {
            copyAdbCommand()
        }

        lifecycleScope.launch {
            binding.hideTaskButton.isChecked = settingsStore.readKey(HIDE_TASK, true)
        }
        binding.hideTaskButton.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                settingsStore.saveKey(HIDE_TASK, isChecked)
            }
        }
    }

    // ========== 关键权限卡（原「设置检查」并入本页） ==========

    private fun setupPermissionCard() {
        val container = binding.setupCheckList
        container.removeAllViews()
        SetupChecklist.items.forEach { item ->
            val rowBinding = ItemSetupCheckRowBinding.inflate(layoutInflater, container, false)
            rowBinding.checkRowIcon.setImageResource(item.icon)
            rowBinding.checkRowChevron.tint(chevronColor())
            rowBinding.checkRowTitle.setText(item.title)
            rowBinding.checkRowDesc.setText(item.description)
            rowBinding.root.setOnClickListener { openPermissionSettings(item) }
            container.addView(
                rowBinding.root,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = 8.dp }
            )
        }
    }

    private fun refreshPermissions() {
        var granted = 0
        var checkable = 0
        SetupChecklist.items.forEachIndexed { index, item ->
            val state = item.isGranted(this)
            val row = binding.setupCheckList.getChildAt(index) ?: return@forEachIndexed
            val rowBinding = ItemSetupCheckRowBinding.bind(row)

            if (state == null) {
                // 无法自动检测（如厂商自启动管理页）：中性徽章，不计入进度。
                rowBinding.checkRowStatus.setText(R.string.setup_check_status_manual)
                rowBinding.checkRowStatus.backgroundTintList = ColorStateList.valueOf(
                    manualBadgeColor()
                )
                rowBinding.checkRowStatus.setTextColor(manualBadgeTextColor())
                rowBinding.checkRowIcon.tint(manualIconColor())
                return@forEachIndexed
            }

            checkable++
            if (state) granted++
            rowBinding.checkRowStatus.setText(
                if (state) R.string.setup_check_status_granted else R.string.setup_check_status_pending
            )
            // 徽章与图标用 View 级 tint，避免改动共享的 drawable 常量状态。
            rowBinding.checkRowStatus.backgroundTintList = ColorStateList.valueOf(
                if (state) grantedBadgeColor() else pendingBadgeColor()
            )
            rowBinding.checkRowStatus.setTextColor(
                if (state) grantedBadgeTextColor() else pendingBadgeTextColor()
            )
            rowBinding.checkRowIcon.tint(if (state) grantedIconColor() else pendingIconColor())
        }

        val pending = checkable - granted
        binding.setupProgressSummary.text = if (pending == 0) {
            getString(R.string.setup_check_progress_all_done)
        } else {
            getString(R.string.setup_check_progress_pending, granted, pending)
        }
        binding.setupProgressFill.post {
            val trackWidth = binding.setupProgressTrack.width
            binding.setupProgressFill.layoutParams = binding.setupProgressFill.layoutParams.apply {
                // 分母只算可检测项；自启动这类无法检测的不拖进度。
                width = if (checkable == 0) trackWidth else trackWidth * granted / checkable
            }
            binding.setupProgressFill.requestLayout()
        }
    }

    private fun openPermissionSettings(item: SetupCheckItem) {
        try {
            startActivity(item.settingsIntent(this))
        } catch (_: Exception) {
            // 个别厂商系统没有对应设置页，退回应用详情页。
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", packageName, null),
                    )
                )
            } catch (_: Exception) {
                Toast.makeText(this, R.string.setup_check_open_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun themeColor(attrId: Int, fallback: Int): Int =
        MaterialColors.getColor(binding.root, attrId, fallback)

    // colorPrimary 是 AndroidX Core 定义的属性，必须走 android.R.attr；
    // com.google.android.material.R.attr 里没有它（曾在此编译失败）。
    private fun grantedBadgeColor() = themeColor(
        android.R.attr.colorPrimary, Color.GRAY
    )

    private fun grantedBadgeTextColor() = themeColor(
        com.google.android.material.R.attr.colorOnPrimary, Color.WHITE
    )

    private fun pendingBadgeColor() = themeColor(
        com.google.android.material.R.attr.colorSurfaceVariant, Color.LTGRAY
    )

    private fun pendingBadgeTextColor() = themeColor(
        com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY
    )

    private fun grantedIconColor() = grantedBadgeColor()

    private fun pendingIconColor() = pendingBadgeTextColor()

    // 无法检测项的中性配色：灰底 + 反色字，与「已开启/未开启」明确区分。
    private fun manualBadgeColor() = themeColor(
        com.google.android.material.R.attr.colorOutline, Color.GRAY
    )

    private fun manualBadgeTextColor() = themeColor(
        com.google.android.material.R.attr.colorSurface, Color.WHITE
    )

    private fun manualIconColor() = manualBadgeTextColor()

    private fun chevronColor() = pendingBadgeTextColor()

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    private fun ImageView.tint(color: Int) {
        ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(color))
    }

    private fun copyAdbCommand() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(
            getString(R.string.background_adb_clipboard_label),
            getString(R.string.background_adb_command, packageName)
        )
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, getString(R.string.background_adb_copied), Toast.LENGTH_SHORT).show()
    }
}
