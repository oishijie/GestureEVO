package io.github.lumiagg.gestureevo.settings

import io.github.lumiagg.gestureevo.gesture.config.FreeTriggerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Business invariants that the Compose trigger editor must preserve. */
class TriggerConfigurationTest {
    @Test
    fun changingOneFlagPreservesOtherSettings() {
        val trigger = FreeTriggerConfig()
        trigger.setState(FreeTriggerConfig.FLAG_LANDSCAPE, true)
        trigger.setState(FreeTriggerConfig.FLAG_MIRROR, true)
        trigger.setState(FreeTriggerConfig.FLAG_PORTRAIT, false)
        assertTrue(trigger.getState(FreeTriggerConfig.FLAG_LANDSCAPE))
        assertTrue(trigger.getState(FreeTriggerConfig.FLAG_MIRROR))
        assertTrue(trigger.getState(FreeTriggerConfig.FLAG_BLACKLIST))
        assertFalse(trigger.getState(FreeTriggerConfig.FLAG_PORTRAIT))
    }

    @Test
    fun mirroredQuadrantsDoNotMutateSavedSelection() {
        val trigger = FreeTriggerConfig()
        val selection = listOf(true, false, false, true)
        trigger.setQuadrant(selection)
        assertEquals(selection, trigger.getQuadrant())
        assertEquals(listOf(false, true, true, false), trigger.getQuadrant(isMirror = true))
        assertEquals(selection, trigger.getQuadrant())
    }

    @Test
    fun whitelistAndBlacklistRemainIndependent() {
        val trigger = FreeTriggerConfig()
        trigger.setState(FreeTriggerConfig.FLAG_WHITELIST, true)
        trigger.whiteList = listOf("allowed", "blocked")
        trigger.blackList = listOf("blocked")
        assertTrue(trigger.isPackageAllowed("allowed", "self"))
        assertFalse(trigger.isPackageAllowed("blocked", "self"))
        assertFalse(trigger.isPackageAllowed("other", "self"))
        assertTrue(trigger.isPackageAllowed("self", "self"))
        trigger.setState(FreeTriggerConfig.FLAG_BLACKLIST, false)
        assertTrue(trigger.isPackageAllowed("blocked", "self"))
    }
}
