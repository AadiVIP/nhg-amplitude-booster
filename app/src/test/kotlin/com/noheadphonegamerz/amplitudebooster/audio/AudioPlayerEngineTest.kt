package com.noheadphonegamerz.amplitudebooster.audio

import com.noheadphonegamerz.amplitudebooster.dsp.DspConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch

/**
 * Regression tests for configuration updates on [AudioPlayerEngine].
 * Changing one setting must never reset another, regardless of update order.
 */
class AudioPlayerEngineTest {

    private fun defaultConfig() = DspConfig(
        gainDb = 0.0,
        bypass = false,
        limiterEnabled = true,
        limiterThresholdDb = -0.1,
        attackTimeMs = 1.0,
        releaseTimeMs = 60.0,
        sampleRate = 48000,
        channelCount = 2
    )

    @Test
    fun setGainThenLimiterThenBypassPreservesAllSettings() {
        val engine = AudioPlayerEngine(sampleRate = 48000)

        engine.setGainDb(6.0)
        engine.setLimiterEnabled(false)
        engine.setBypass(true)

        assertEquals(
            defaultConfig().copy(gainDb = 6.0, limiterEnabled = false, bypass = true),
            engine.processor.config
        )
    }

    @Test
    fun setBypassThenGainThenLimiterPreservesAllSettings() {
        val engine = AudioPlayerEngine(sampleRate = 48000)

        engine.setBypass(true)
        engine.setGainDb(3.0)
        engine.setLimiterEnabled(false)

        assertEquals(
            defaultConfig().copy(gainDb = 3.0, limiterEnabled = false, bypass = true),
            engine.processor.config
        )
    }

    @Test
    fun setLimiterThenBypassThenGainPreservesAllSettings() {
        val engine = AudioPlayerEngine(sampleRate = 48000)

        engine.setLimiterEnabled(false)
        engine.setBypass(true)
        engine.setGainDb(9.0)

        assertEquals(
            defaultConfig().copy(gainDb = 9.0, limiterEnabled = false, bypass = true),
            engine.processor.config
        )
    }

    @Test
    fun gainClampPreservesOtherSettings() {
        val engine = AudioPlayerEngine(sampleRate = 48000)

        engine.setLimiterEnabled(false)
        engine.setBypass(true)
        engine.setGainDb(99.0)

        assertEquals(12.0, engine.processor.config.gainDb, 0.0)
        assertFalse(engine.processor.config.limiterEnabled)
        assertTrue(engine.processor.config.bypass)
    }

    @Test
    fun concurrentSettersDoNotLoseOtherSettings() {
        val engine = AudioPlayerEngine(sampleRate = 48000)
        val start = CountDownLatch(1)

        val workers = listOf(
            Thread { start.await(); repeat(250) { engine.setGainDb(6.0) } },
            Thread { start.await(); repeat(250) { engine.setLimiterEnabled(false) } },
            Thread { start.await(); repeat(250) { engine.setBypass(true) } }
        )

        workers.forEach { it.start() }
        start.countDown()
        workers.forEach { it.join(5000) }

        assertTrue("Setter worker did not finish", workers.none { it.isAlive })
        assertEquals(6.0, engine.processor.config.gainDb, 0.0)
        assertFalse(engine.processor.config.limiterEnabled)
        assertTrue(engine.processor.config.bypass)
    }
}
