package io.github.lumiagg.gestureevo.settings

import io.github.lumiagg.gestureevo.settings.ui.parsePickerHexColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ColorParsingTest {
    @Test fun rgbIsOpaqueWithOrWithoutPrefix() {
        assertEquals(0xFF112233.toInt(), parsePickerHexColor("#112233"))
        assertEquals(0xFF112233.toInt(), parsePickerHexColor("112233"))
    }

    @Test fun argbRetainsAllAlphaBits() {
        assertEquals(0x00112233, parsePickerHexColor("#00112233"))
        assertEquals(0x80112233.toInt(), parsePickerHexColor("80112233"))
        assertEquals(0xFFFFFFFF.toInt(), parsePickerHexColor("FFFFFFFF"))
    }

    @Test fun whitespaceAndMixedCaseAreAccepted() {
        assertEquals(0xFFAABBCC.toInt(), parsePickerHexColor("  #aAbBcC\n"))
    }

    @Test fun malformedInputNeverBecomesAColor() {
        listOf(null, "", "  ", "#123", "12345", "1234567", "123456789", "##112233",
            "#GG1122", "-12345", "+12345", "12 3456", "0x112233").forEach { raw ->
            assertNull("Must reject: $raw", parsePickerHexColor(raw))
        }
    }
}
