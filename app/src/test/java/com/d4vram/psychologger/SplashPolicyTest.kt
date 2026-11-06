package com.d4vram.psychologger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SplashPolicyTest {
    @Test fun showsFirstFourLaunchesThenDisables() {
        var launches = 0
        var done = false
        val disabledAt = mutableListOf<Int>()
        for (open in 1..6) {
            val s = SplashPolicy.onLaunch(launches, done)
            launches = s.launches; done = s.autoDone
            if (s.disableNow) disabledAt += open
        }
        assertEquals(listOf(5), disabledAt)
    }

    @Test fun manualToggleCancelsAutomation() {
        val s = SplashPolicy.onLaunch(launches = 4, autoDone = true)
        assertFalse(s.disableNow)
        assertTrue(s.autoDone)
    }
}
