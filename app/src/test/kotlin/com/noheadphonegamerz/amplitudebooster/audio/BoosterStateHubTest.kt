package com.noheadphonegamerz.amplitudebooster.audio

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class BoosterStateHubTest {

    @Before
    fun setup() {
        BoosterStateHub.clearLogs()
        BoosterStateHub.updateState(
            BoosterState(
                status = BoosterStatus.OFF,
                gainDb = 0.0,
                activeEffectType = EffectType.NONE,
                statusDetail = "Reset for test"
            )
        )
    }

    @Test
    fun defaultStateIsOffWithZeroDb() {
        val state = BoosterStateHub.currentState()
        assertEquals(BoosterStatus.OFF, state.status)
        assertEquals(0.0, state.gainDb, 0.0)
        assertFalse(state.isActive)
        assertEquals(1.0, state.linearMultiplier, 1e-4)
    }

    @Test
    fun updateGainReflectsInState() {
        BoosterStateHub.updateGain(8.5)
        val state = BoosterStateHub.currentState()
        assertEquals(8.5, state.gainDb, 0.0)
    }

    @Test
    fun updateStatusReflectsInStateAndLogs() {
        BoosterStateHub.updateStatus(
            status = BoosterStatus.ACTIVE,
            detail = "Attached on session 0",
            error = null
        )
        val state = BoosterStateHub.currentState()
        assertEquals(BoosterStatus.ACTIVE, state.status)
        assertEquals("Attached on session 0", state.statusDetail)
        assertNull(state.lastError)

        val logs = BoosterStateHub.getLogs()
        assertTrue(logs.any { it.contains("Attached on session 0") })
    }

    @Test
    fun errorStateCapturesLastError() {
        BoosterStateHub.updateStatus(
            status = BoosterStatus.ERROR,
            detail = "Attachment failed",
            error = "SecurityException"
        )
        val state = BoosterStateHub.currentState()
        assertEquals(BoosterStatus.ERROR, state.status)
        assertEquals("SecurityException", state.lastError)
    }

    @Test
    fun linearMultiplierCalculationOnState() {
        val state0 = BoosterState(gainDb = 0.0)
        assertEquals(1.0, state0.linearMultiplier, 1e-4)

        val state6 = BoosterState(gainDb = 6.0)
        assertEquals(1.995, state6.linearMultiplier, 1e-3)

        val state12 = BoosterState(gainDb = 12.0)
        assertEquals(3.981, state12.linearMultiplier, 1e-3)
    }
}
