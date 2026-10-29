package com.noheadphonegamerz.amplitudebooster.dsp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Regression tests for cross-thread configuration updates on [DspProcessor].
 */
class DspProcessorConfigTest {

    @Test
    fun configUpdateAppliesToNextBufferAndPreservesUnrelatedSettings() {
        val processor = DspProcessor(DspConfig(gainDb = 0.0, limiterEnabled = true))
        val initial = processor.config

        processor.config = processor.config.copy(gainDb = 6.020599913279624)

        val updated = processor.config
        assertFalse(updated.bypass)
        assertTrue(updated.limiterEnabled)
        assertEquals(initial.limiterThresholdDb, updated.limiterThresholdDb, 0.0)
        assertEquals(initial.attackTimeMs, updated.attackTimeMs, 0.0)
        assertEquals(initial.releaseTimeMs, updated.releaseTimeMs, 0.0)
        assertEquals(initial.sampleRate, updated.sampleRate)
        assertEquals(initial.channelCount, updated.channelCount)

        val input = FloatArray(32) { if (it % 2 == 0) 0.1f else 0.05f }
        val output = FloatArray(input.size)
        val stats = processor.process(input, output, channels = 2)

        for (i in output.indices) {
            assertEquals(input[i] * 2.0, output[i].toDouble(), 1e-6)
        }
        assertEquals(2.0, stats.requestedLinearGain, 1e-6)
        assertEquals(2.0, stats.effectiveLinearGain, 1e-6)
        assertFalse(stats.limiterActive)
    }

    @Test
    fun omittedChannelsResolveFromCurrentConfigSnapshot() {
        val processor = DspProcessor(DspConfig(channelCount = 1))
        val input = shortArrayOf(1000, 2000, 3000, 4000)
        val monoOutput = ShortArray(input.size)

        val monoStats = processor.process(input, monoOutput)

        assertArrayEquals(input, monoOutput)
        assertEquals(4000.0 / 32767.0, monoStats.inputPeakL, 0.0)
        assertEquals(monoStats.inputPeakL, monoStats.inputPeakR, 0.0)

        processor.config = processor.config.copy(channelCount = 2)
        val stereoOutput = ShortArray(input.size)

        val stereoStats = processor.process(input, stereoOutput)

        assertArrayEquals(input, stereoOutput)
        assertEquals(3000.0 / 32767.0, stereoStats.inputPeakL, 0.0)
        assertEquals(4000.0 / 32767.0, stereoStats.inputPeakR, 0.0)
    }

    @Test
    fun configReferenceIsVolatile() {
        val field = DspProcessor::class.java.getDeclaredField("config")

        assertTrue(
            "config must be @Volatile so playback threads see published updates",
            Modifier.isVolatile(field.modifiers)
        )
    }
}
