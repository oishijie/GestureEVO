package io.github.lumiagg.gestureevo.settings

import io.github.lumiagg.gestureevo.gesture.config.FreeTriggerConfig
import io.github.lumiagg.gestureevo.settings.ui.freeTriggerSelectionOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FreeTriggerSelectionOptionsTest {
    @Test fun onlyMirroredTriggersExposeAnAdditionalChoice() {
        val normal = FreeTriggerConfig().apply { tag = "normal"; setState(FreeTriggerConfig.FLAG_MIRROR, false) }
        val mirrored = FreeTriggerConfig().apply { tag = "mirrored"; setState(FreeTriggerConfig.FLAG_MIRROR, true) }
        val options = freeTriggerSelectionOptions(listOf(normal, mirrored))
        assertEquals(listOf("normal", "mirrored", "mirrored"), options.map { it.tag })
        assertEquals(listOf(false, false, true), options.map { it.isMirror })
        assertFalse(normal.getState(FreeTriggerConfig.FLAG_MIRROR))
    }

    @Test fun emptyInputHasNoSyntheticTrigger() {
        assertEquals(emptyList<Any>(), freeTriggerSelectionOptions(emptyList()))
    }
}
