package io.github.lumiagg.gestureevo.settings.ui

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.utils.DynamicColorUtils

private const val PROJECT_LICENSE_ASSET = "licenses/project_license.txt"
private const val OPEN_SOURCE_LICENSES_ASSET = "licenses/open_source_notices.txt"

/** 展示项目自身许可与第三方开源许可全文。 */
fun showOpenSourceLicenses(activity: AppCompatActivity) {
    val notice = runCatching {
        listOf(PROJECT_LICENSE_ASSET, OPEN_SOURCE_LICENSES_ASSET).joinToString("\n\n") { asset ->
            activity.assets.open(asset).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
    }.getOrElse {
        Toast.makeText(
            activity,
            activity.getString(R.string.about_open_source_licenses_load_failed),
            Toast.LENGTH_SHORT
        ).show()
        return
    }

    val dialog = BottomSheetDialog(activity, R.style.ThemeOverlay_BottomSheetDialog_Scrollable)
    dialog.setContentView(R.layout.open_source_licenses_bottomsheet)
    dialog.findViewById<TextView>(R.id.open_source_licenses_notice)?.text = notice

    dialog.setOnShowListener { shownDialog ->
        val bottomSheet = (shownDialog as BottomSheetDialog)
            .findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) as FrameLayout?

        bottomSheet?.let { sheet ->
            DynamicColorUtils.applyBottomSheetSurface(shownDialog)
            sheet.layoutParams.height = (activity.window.decorView.height * 0.85f).toInt()
            BottomSheetBehavior.from(sheet).apply {
                state = BottomSheetBehavior.STATE_EXPANDED
                isDraggable = true
                skipCollapsed = true
                isFitToContents = true
                isHideable = true
            }
        }
    }
    dialog.show()
}
