package com.noheadphonegamerz.amplitudebooster.audio

import org.junit.Assert.*
import org.junit.Test

class AudioEffectAdapterTest {

    private class FakeEffectBackend(
        var attachResult: AudioEffectAdapter.BackendAttachResult = AudioEffectAdapter.BackendAttachResult.Success(
            EffectType.LOUDNESS_ENHANCER,
            "Fake LoudnessEnhancer"
        ),
        var isEnabledValue: Boolean = true,
        var hasControlValue: Boolean = true
    ) : AudioEffectAdapter.EffectBackend {

        var lastAttachedGain: Double? = null
        var lastSetGain: Double? = null
        var releaseCount = 0
        var controlLostCallback: ((String) -> Unit)? = null

        override fun attach(gainDb: Double): AudioEffectAdapter.BackendAttachResult {
            lastAttachedGain = gainDb
            return attachResult
        }

        override fun setGain(gainDb: Double): Boolean {
            lastSetGain = gainDb
            return true
        }

        override fun isEnabled(): Boolean = isEnabledValue
        override fun hasControl(): Boolean = hasControlValue

        override fun release(reason: String) {
            releaseCount++
            isEnabledValue = false
        }

        override fun setOnControlLostListener(listener: (String) -> Unit) {
            controlLostCallback = listener
        }

        fun simulateControlLoss(reason: String) {
            controlLostCallback?.invoke(reason)
        }
    }

    @Test
    fun gainSanitizationClampsBounds() {
        val adapter = AudioEffectAdapter(backend = FakeEffectBackend())

        assertEquals(0.0, adapter.sanitizeGain(-5.0), 0.0)
        assertEquals(0.0, adapter.sanitizeGain(0.0), 0.0)
        assertEquals(6.0, adapter.sanitizeGain(6.0), 0.0)
        assertEquals(15.0, adapter.sanitizeGain(15.0), 0.0)
        assertEquals(15.0, adapter.sanitizeGain(99.0), 0.0)
    }

    @Test
    fun dbToLinearConversionIsAccurate() {
        assertEquals(1.0, AudioEffectAdapter.dbToLinear(0.0), 1e-4)
        assertEquals(2.0, AudioEffectAdapter.dbToLinear(6.0205999), 1e-4)
        assertEquals(4.0, AudioEffectAdapter.dbToLinear(12.0411998), 1e-4)
        assertEquals(10.0, AudioEffectAdapter.dbToLinear(20.0), 1e-4)
    }

    @Test
    fun dbToMillibelsConversionMatchesStandard() {
        assertEquals(0, AudioEffectAdapter.dbToMillibels(0.0))
        assertEquals(100, AudioEffectAdapter.dbToMillibels(1.0))
        assertEquals(600, AudioEffectAdapter.dbToMillibels(6.0))
        assertEquals(1250, AudioEffectAdapter.dbToMillibels(12.5))
        assertEquals(1500, AudioEffectAdapter.dbToMillibels(15.0))
    }

    @Test
    fun attachSuccessWithConfirmedReadbackTransitionsToActive() {
        val fake = FakeEffectBackend(isEnabledValue = true, hasControlValue = true)
        val adapter = AudioEffectAdapter(backend = fake)

        val state = adapter.attach(6.0)

        assertEquals(BoosterStatus.ACTIVE, state.status)
        assertEquals(6.0, state.gainDb, 0.0)
        assertEquals(EffectType.LOUDNESS_ENHANCER, state.activeEffectType)
        assertNull(state.lastError)
        assertTrue(state.statusDetail.contains("+6.0 dB"))
        assertTrue(adapter.isEnabled)
    }

    @Test
    fun attachFailsWhenReadbackIsNotConfirmed() {
        // Backend returns success on attach, but hasControl() is false
        val fake = FakeEffectBackend(isEnabledValue = true, hasControlValue = false)
        val adapter = AudioEffectAdapter(backend = fake)

        val state = adapter.attach(6.0)

        assertEquals(BoosterStatus.ERROR, state.status)
        assertEquals(EffectType.NONE, state.activeEffectType)
        assertNotNull(state.lastError)
        assertTrue(state.lastError!!.contains("hasControl=false"))
        assertFalse(adapter.isEnabled)
        // Ensure release was called when readback failed
        assertTrue(fake.releaseCount > 0)
    }

    @Test
    fun attachFailsWhenConstructorsFail() {
        val fake = FakeEffectBackend(
            attachResult = AudioEffectAdapter.BackendAttachResult.Failure(
                errorMessage = "UnsupportedOperationException: session 0 not permitted",
                exceptions = listOf("LE failed", "DP failed", "EQ failed")
            )
        )
        val adapter = AudioEffectAdapter(backend = fake)

        val state = adapter.attach(6.0)

        assertEquals(BoosterStatus.ERROR, state.status)
        assertEquals(EffectType.NONE, state.activeEffectType)
        assertNotNull(state.lastError)
        assertTrue(state.lastError!!.contains("session 0 not permitted"))
    }

    @Test
    fun controlLossTransitionsToErrorAndReleases() {
        val fake = FakeEffectBackend(isEnabledValue = true, hasControlValue = true)
        val adapter = AudioEffectAdapter(backend = fake)

        adapter.attach(6.0)
        assertEquals(BoosterStatus.ACTIVE, adapter.currentState.status)

        // Simulate external audio server or app taking control
        fake.simulateControlLoss("External equalizer took control")

        assertEquals(BoosterStatus.ERROR, adapter.currentState.status)
        assertEquals(EffectType.NONE, adapter.currentState.activeEffectType)
        assertTrue(adapter.currentState.statusDetail.contains("External equalizer took control"))
        assertTrue(fake.releaseCount > 0)
    }

    @Test
    fun setGainDbUpdatesBackendWhenActive() {
        val fake = FakeEffectBackend(isEnabledValue = true, hasControlValue = true)
        val adapter = AudioEffectAdapter(backend = fake)

        adapter.attach(3.0)
        assertEquals(3.0, fake.lastAttachedGain!!, 0.0)

        adapter.setGainDb(9.0)
        assertEquals(9.0, fake.lastSetGain!!, 0.0)
        assertEquals(9.0, adapter.currentState.gainDb, 0.0)
        assertEquals(BoosterStatus.ACTIVE, adapter.currentState.status)
    }

    @Test
    fun setGainDbWhenOffDoesNotCallBackendSetGain() {
        val fake = FakeEffectBackend()
        val adapter = AudioEffectAdapter(backend = fake)

        adapter.setGainDb(9.0)
        assertNull(fake.lastSetGain)
        assertEquals(9.0, adapter.currentState.gainDb, 0.0)
        assertEquals(BoosterStatus.OFF, adapter.currentState.status)
    }

    @Test
    fun releaseIsIdempotentAndResetsState() {
        val fake = FakeEffectBackend(isEnabledValue = true, hasControlValue = true)
        val adapter = AudioEffectAdapter(backend = fake)

        adapter.attach(6.0)
        assertEquals(BoosterStatus.ACTIVE, adapter.currentState.status)

        adapter.release("User stopped")
        assertEquals(BoosterStatus.OFF, adapter.currentState.status)
        assertEquals(EffectType.NONE, adapter.currentState.activeEffectType)

        // Second release must not throw or change state
        adapter.release("Repeat call")
        assertEquals(BoosterStatus.OFF, adapter.currentState.status)
    }

    @Test
    fun listenerReceivesStateUpdates() {
        val fake = FakeEffectBackend(isEnabledValue = true, hasControlValue = true)
        val adapter = AudioEffectAdapter(backend = fake)

        val history = mutableListOf<BoosterStatus>()
        adapter.onStateChanged = { history.add(it.status) }

        adapter.attach(6.0)
        adapter.release("Test")

        // Initial attach produces STARTING then ACTIVE; release produces OFF
        assertTrue(history.contains(BoosterStatus.STARTING))
        assertTrue(history.contains(BoosterStatus.ACTIVE))
        assertTrue(history.contains(BoosterStatus.OFF))
    }
}
