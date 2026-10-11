package com.dizzyvy.rift.ui.player

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepeatModePresentationTest {
    @Test
    fun repeatModeLabelsAndIndicatorMatchTheActiveMode() {
        assertEquals("Repeat off", repeatModePresentation(Player.REPEAT_MODE_OFF).contentDescription)
        assertFalse(repeatModePresentation(Player.REPEAT_MODE_OFF).showsOneIndicator)

        assertEquals("Repeat all", repeatModePresentation(Player.REPEAT_MODE_ALL).contentDescription)
        assertFalse(repeatModePresentation(Player.REPEAT_MODE_ALL).showsOneIndicator)

        assertEquals("Repeat one", repeatModePresentation(Player.REPEAT_MODE_ONE).contentDescription)
        assertTrue(repeatModePresentation(Player.REPEAT_MODE_ONE).showsOneIndicator)
    }

    @Test
    fun repeatModeCycleRemainsOffAllOneOff() {
        assertEquals(Player.REPEAT_MODE_ALL, nextRepeatMode(Player.REPEAT_MODE_OFF))
        assertEquals(Player.REPEAT_MODE_ONE, nextRepeatMode(Player.REPEAT_MODE_ALL))
        assertEquals(Player.REPEAT_MODE_OFF, nextRepeatMode(Player.REPEAT_MODE_ONE))
    }
}
