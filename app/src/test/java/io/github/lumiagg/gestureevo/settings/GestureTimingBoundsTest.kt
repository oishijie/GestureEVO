package io.github.lumiagg.gestureevo.settings

import io.github.lumiagg.gestureevo.gesture.config.GestureTimingConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class GestureTimingBoundsTest {
    @Test fun explicitValuesRemainUnchangedInsideBounds() {
        val config = GestureTimingConfig(longPressTimeoutMs = 500, doubleTapTimeoutMs = 300, pauseTimeoutMs = 220)
        assertEquals(500L, config.resolvedLongPressTimeoutMs())
        assertEquals(300L, config.resolvedDoubleTapTimeoutMs())
        assertEquals(220L, config.resolvedPauseTimeoutMs())
    }

    @Test fun importedPositiveValuesAreClamped() {
        val low = GestureTimingConfig(longPressTimeoutMs = 1, doubleTapTimeoutMs = 1, pauseTimeoutMs = 1)
        assertEquals(200L, low.resolvedLongPressTimeoutMs())
        assertEquals(120L, low.resolvedDoubleTapTimeoutMs())
        assertEquals(10L, low.resolvedPauseTimeoutMs())
        val high = GestureTimingConfig(longPressTimeoutMs = 10000, doubleTapTimeoutMs = 10000, pauseTimeoutMs = 10000)
        assertEquals(1200L, high.resolvedLongPressTimeoutMs())
        assertEquals(600L, high.resolvedDoubleTapTimeoutMs())
        assertEquals(600L, high.resolvedPauseTimeoutMs())
    }

    @Test fun pauseZeroAndNegativeUseDefault() {
        assertEquals(220L, GestureTimingConfig().resolvedPauseTimeoutMs())
        assertEquals(220L, GestureTimingConfig(pauseTimeoutMs = -1).resolvedPauseTimeoutMs())
    }
}
