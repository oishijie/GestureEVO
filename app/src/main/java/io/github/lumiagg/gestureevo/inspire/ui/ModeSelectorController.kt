package io.github.lumiagg.gestureevo.inspire.ui

import android.widget.TextView
import io.github.lumiagg.gestureevo.inspire.InspireTokenContentMode

internal class ModeSelectorController(
    private val btnModeOcr: TextView,
    private val btnModeAccessibility: TextView,
    private val canUseMode: (InspireTokenContentMode) -> Boolean,
    private val currentModeProvider: () -> InspireTokenContentMode,
) {
    fun update() {
        val hasOcr = canUseMode(InspireTokenContentMode.OCR)
        val hasAccessibility = canUseMode(InspireTokenContentMode.ACCESSIBILITY)
        btnModeOcr.post {
            btnModeOcr.isEnabled = hasOcr
            btnModeAccessibility.isEnabled = hasAccessibility
            applyButtonState(
                btnModeOcr,
                isSelected = currentModeProvider().isOCR,
                isEnabled = hasOcr,
            )
            applyButtonState(
                btnModeAccessibility,
                isSelected = !currentModeProvider().isOCR,
                isEnabled = hasAccessibility,
            )
        }
    }

    private fun applyButtonState(view: TextView, isSelected: Boolean, isEnabled: Boolean) {
        view.isSelected = isSelected
        view.isEnabled = isEnabled
        view.alpha = if (isEnabled) 1f else DISABLED_ALPHA
    }

    private companion object {
        const val DISABLED_ALPHA = 0.45f
    }
}
