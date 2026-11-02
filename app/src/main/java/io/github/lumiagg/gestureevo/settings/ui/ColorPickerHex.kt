package io.github.lumiagg.gestureevo.settings.ui

/** Six digits mean opaque RGB; eight digits are ARGB, matching the picker contract.
 * Alpha-disabled selection is normalized by the picker when applying this result.
 * Kept Android-free so local tests exercise the production parser, not a copy.
 */
internal fun parsePickerHexColor(rawText: String?): Int? {
    if (rawText.isNullOrBlank()) return null
    val digits = rawText.trim().removePrefix("#")
    if (digits.length != 6 && digits.length != 8) return null
    if (digits.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null
    val value = digits.toLongOrNull(16)?.toInt() ?: return null
    return if (digits.length == 6) value or -0x1000000 else value
}
