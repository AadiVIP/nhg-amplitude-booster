package com.noheadphonegamerz.amplitudebooster.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Automated Unit Test Suite validating the DSP requirements, including stereo-linked
 * limiting, PCM safety, and telemetry semantics.
 */
class DspProcessorTest {

    private val sampleRate = 48000
    private val maxFloatCeilingTolerance = 1e-6

    /**
     * 1. Zero gain reproduces the original PCM samples within expected numerical precision.
     */
    @Test
    fun testZeroGainBitPrecision() {
        val config = DspConfig(gainDb = 0.0, bypass = false, sampleRate = sampleRate)
        val processor = DspProcessor(config)

        // Test 16-bit PCM
        val inputShort = ShortArray(1024) { i ->
            // Diverse test values including min, max, zero, and mid-range
            when (i % 6) {
                0 -> 0
                1 -> 16384
                2 -> -16384
                3 -> 32767
                4 -> -32768
                else -> (sin(2.0 * PI * i / 100.0) * 20000.0).toInt().toShort()
            }
        }
        val outputShort = ShortArray(inputShort.size)
        processor.process(inputShort, outputShort, channels = 2)

        for (i in inputShort.indices) {
            assertEquals("Short PCM mismatch at index $i", inputShort[i], outputShort[i])
        }

        // Test Float PCM
        val inputFloat = FloatArray(1024) { i ->
            (sin(2.0 * PI * i / 100.0) * 0.7).toFloat()
        }
        val outputFloat = FloatArray(inputFloat.size)
        processor.process(inputFloat, outputFloat, channels = 2)

        for (i in inputFloat.indices) {
            assertEquals("Float PCM mismatch at index $i", inputFloat[i], outputFloat[i], 1e-5f)
        }
    }

    /**
     * 2. Positive gain scales every sample by the same scalar before limiting.
     */
    @Test
    fun testPositiveGainLinearScaling() {
        val gainDb = 6.0
        val config = DspConfig(gainDb = gainDb, sampleRate = sampleRate)
        val processor = DspProcessor(config)
        val expectedMultiplier = config.linearGain() // ~1.99526

        // Use low amplitude so limiter is completely inactive
        val inputFloat = FloatArray(256) { i ->
            ((i - 128) / 1000.0f) // range [-0.128, +0.127]
        }
        val outputFloat = FloatArray(inputFloat.size)
        val stats = processor.process(inputFloat, outputFloat, channels = 1)

        assertFalse("Limiter should not be active for small signals", stats.limiterActive)

        for (i in inputFloat.indices) {
            val expected = inputFloat[i] * expectedMultiplier
            assertEquals("Sample $i scaled incorrectly", expected, outputFloat[i].toDouble(), 0.002)
        }
    }

    /**
     * 3. A sine wave at 100 Hz and another at 1 kHz receive the same linear gain when neither triggers limiting.
     */
    @Test
    fun testFrequencyResponseUniformity100HzAnd1kHz() {
        val gainDb = 6.0
        val config = DspConfig(gainDb = gainDb, sampleRate = sampleRate)
        val processor = DspProcessor(config)

        val durationSec = 0.1
        val numSamples = (sampleRate * durationSec).toInt()

        val sine100Hz = generateSine(100.0, 0.2, numSamples)
        val sine1kHz = generateSine(1000.0, 0.2, numSamples)

        val out100Hz = FloatArray(numSamples)
        val out1kHz = FloatArray(numSamples)

        processor.reset()
        val stats100 = processor.process(sine100Hz, out100Hz, channels = 1)
        processor.reset()
        val stats1k = processor.process(sine1kHz, out1kHz, channels = 1)

        val gain100 = stats100.outputRmsL / stats100.inputRmsL
        val gain1k = stats1k.outputRmsL / stats1k.inputRmsL

        assertEquals("Gain at 100 Hz and 1 kHz must be identical", gain100, gain1k, 0.005)
    }

    /**
     * 4. Test several frequencies across the supported audio band.
     */
    @Test
    fun testMultiFrequencySweepAcrossAudioBand() {
        val gainDb = 4.0
        val config = DspConfig(gainDb = gainDb, sampleRate = sampleRate)
        val processor = DspProcessor(config)
        val expectedGain = config.linearGain()

        val testFrequencies = doubleArrayOf(20.0, 50.0, 100.0, 440.0, 1000.0, 5000.0, 10000.0, 15000.0, 20000.0)
        val numSamples = sampleRate / 2 // 0.5 second

        for (freq in testFrequencies) {
            val signal = generateSine(freq, 0.15, numSamples)
            val output = FloatArray(numSamples)
            processor.reset()
            val stats = processor.process(signal, output, channels = 1)

            val measuredGain = stats.outputRmsL / stats.inputRmsL
            assertEquals(
                "Frequency $freq Hz failed uniform gain check",
                expectedGain,
                measuredGain,
                0.01
            )
        }
    }

    /**
     * 5. Stereo channels receive identical gain without channel swapping.
     */
    @Test
    fun testStereoChannelsIdenticalGainAndNoSwapping() {
        val gainDb = 6.0
        val config = DspConfig(gainDb = gainDb, sampleRate = sampleRate, channelCount = 2)
        val processor = DspProcessor(config)
        val g = config.linearGain()

        val frames = 100
        val input = FloatArray(frames * 2) { i ->
            if (i % 2 == 0) 0.10f else 0.25f // L = 0.10, R = 0.25
        }
        val output = FloatArray(input.size)
        val stats = processor.process(input, output, channels = 2)

        for (frame in 0 until frames) {
            val outL = output[frame * 2]
            val outR = output[frame * 2 + 1]

            assertEquals("Left channel gain error", 0.10 * g, outL.toDouble(), 0.005)
            assertEquals("Right channel gain error", 0.25 * g, outR.toDouble(), 0.005)
            assertTrue("Left should be smaller than Right (no channel swapping)", outL < outR)
        }

        // Stereo gain ratio check
        val gainL = stats.outputRmsL / stats.inputRmsL
        val gainR = stats.outputRmsR / stats.inputRmsR
        assertEquals("Stereo balance must be preserved", gainL, gainR, 0.001)
    }

    /**
     * 6. Negative and positive samples retain the correct polarity.
     */
    @Test
    fun testPolarityPreservation() {
        val config = DspConfig(gainDb = 6.0, sampleRate = sampleRate)
        val processor = DspProcessor(config)

        val input = floatArrayOf(0.1f, -0.1f, 0.3f, -0.3f, 0.5f, -0.5f)
        val output = FloatArray(input.size)
        processor.process(input, output, channels = 1)

        for (i in input.indices) {
            if (input[i] > 0) {
                assertTrue("Positive sample became negative", output[i] > 0)
            } else if (input[i] < 0) {
                assertTrue("Negative sample became positive", output[i] < 0)
            }
        }
    }

    /**
     * 7. Silence remains silent.
     */
    @Test
    fun testSilenceRemainsSilent() {
        val config = DspConfig(gainDb = 12.0, sampleRate = sampleRate)
        val processor = DspProcessor(config)

        // Float silence
        val floatInput = FloatArray(512) { 0.0f }
        val floatOutput = FloatArray(512)
        val floatStats = processor.process(floatInput, floatOutput, channels = 2)

        assertEquals("Output peak must be 0 for silence", 0.0, floatStats.outputPeakL, 0.0)
        assertEquals("Output RMS must be 0 for silence", 0.0, floatStats.outputRmsL, 0.0)
        for (v in floatOutput) {
            assertEquals(0.0f, v, 0.0f)
        }

        // Short silence
        val shortInput = ShortArray(512) { 0 }
        val shortOutput = ShortArray(512)
        val shortStats = processor.process(shortInput, shortOutput, channels = 2)

        assertEquals("Output peak must be 0 for short silence", 0.0, shortStats.outputPeakL, 0.0)
        for (v in shortOutput) {
            assertEquals(0.toShort(), v)
        }
    }

    /**
     * 8. Minimum and maximum valid PCM values do not cause overflow.
     */
    @Test
    fun testMinMaxPcmNoOverflow() {
        val config = DspConfig(gainDb = 12.0, sampleRate = sampleRate)
        val processor = DspProcessor(config)

        val extremeValues = shortArrayOf(
            Short.MIN_VALUE, Short.MAX_VALUE,
            (Short.MIN_VALUE + 1).toShort(), (Short.MAX_VALUE - 1).toShort(),
            -32000, 32000
        )
        val output = ShortArray(extremeValues.size)
        processor.process(extremeValues, output, channels = 1)

        for (i in extremeValues.indices) {
            val inVal = extremeValues[i]
            val outVal = output[i]

            // Ensure no integer wrap-around (positive becoming negative or vice versa)
            if (inVal > 0) {
                assertTrue("Extreme positive wrapped to negative: $inVal -> $outVal", outVal > 0)
            } else if (inVal < 0) {
                assertTrue("Extreme negative wrapped to positive: $inVal -> $outVal", outVal < 0)
            }
        }
    }

    /**
     * 9. NaN and infinity are handled safely in float input.
     */
    @Test
    fun testNaNAndInfinityFloatSafety() {
        val config = DspConfig(gainDb = 6.0, sampleRate = sampleRate)
        val processor = DspProcessor(config)

        val malformedInput = floatArrayOf(
            Float.NaN,
            Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY,
            0.5f,
            Float.NaN
        )
        val output = FloatArray(malformedInput.size)

        // Must not crash or propagate NaN/Inf
        val stats = processor.process(malformedInput, output, channels = 1)

        for (i in output.indices) {
            assertFalse("Output contains NaN at index $i", output[i].isNaN())
            assertFalse("Output contains Infinite at index $i", output[i].isInfinite())
            assertTrue("Output exceeds permissible range at index $i: ${output[i]}", output[i] in -1.0f..1.0f)
        }
    }

    /**
     * 10. Limiting prevents digital clipping and honors the configured ceiling.
     */
    @Test
    fun testLimitingPreventsDigitalClipping() {
        // High gain (+12 dB = ~4x) on large signal (0.8) -> would reach 3.2 without limiting
        val config = DspConfig(gainDb = 12.0, limiterEnabled = true, limiterThresholdDb = -0.1, sampleRate = sampleRate)
        val processor = DspProcessor(config)
        val ceiling = config.limiterThresholdLinear()

        val numSamples = 2048
        val inputSine = generateSine(440.0, 0.8, numSamples)
        val outputSine = FloatArray(numSamples)

        val stats = processor.process(inputSine, outputSine, channels = 1)

        assertTrue("Limiter must be active for 0.8 signal amplified by +12dB", stats.limiterActive)
        assertTrue("Limiter must reduce gain", stats.maxGainReductionDb < -1.0)
        assertTrue("Limited frame count must be positive", stats.limitedFrameCount > 0)

        // Strict verification: no sample may exceed full scale or the configured ceiling
        for (i in outputSine.indices) {
            assertTrue("Sample $i clipped above 1.0f: ${outputSine[i]}", outputSine[i] <= 1.0f)
            assertTrue("Sample $i clipped below -1.0f: ${outputSine[i]}", outputSine[i] >= -1.0f)
            assertTrue(
                "Sample $i exceeds configured float ceiling: ${outputSine[i]}",
                abs(outputSine[i].toDouble()) <= ceiling + maxFloatCeilingTolerance
            )
        }

        // Test 16-bit PCM clipping protection
        val inputShort = ShortArray(numSamples) { i ->
            (sin(2.0 * PI * 440.0 * i / sampleRate) * 28000.0).toInt().toShort()
        }
        val outputShort = ShortArray(numSamples)
        processor.reset()
        val shortStats = processor.process(inputShort, outputShort, channels = 1)

        assertTrue("Limiter should be active on large 16-bit signal", shortStats.limiterActive)
        val maxQuantizedCeiling = ceil(ceiling * 32768.0).toInt()
        for (i in outputShort.indices) {
            assertTrue("16-bit sample clipped: ${outputShort[i]}", outputShort[i] in -32768..32767)
            assertTrue(
                "16-bit sample exceeds quantized ceiling: ${outputShort[i]}",
                abs(outputShort[i].toInt()) <= maxQuantizedCeiling
            )
        }
    }

    /**
     * 11. The bypass path is genuinely unprocessed.
     */
    @Test
    fun testBypassPathUnprocessed() {
        val config = DspConfig(gainDb = 12.0, bypass = true, sampleRate = sampleRate)
        val processor = DspProcessor(config)

        val input = FloatArray(100) { i -> ((i - 50) / 100.0f) }
        val output = FloatArray(input.size)
        processor.process(input, output, channels = 1)

        for (i in input.indices) {
            assertEquals("Bypass modified sample $i", input[i], output[i], 0.0f)
        }
    }

    /**
     * 12. Processing introduces no unintended DC offset or channel imbalance.
     */
    @Test
    fun testNoUnintendedDcOffsetOrChannelImbalance() {
        val config = DspConfig(gainDb = 6.0, sampleRate = sampleRate, channelCount = 2)
        val processor = DspProcessor(config)

        val numSamples = 4800
        val input = FloatArray(numSamples * 2) { i ->
            val frame = i / 2
            val sine = (sin(2.0 * PI * 1000.0 * frame / sampleRate) * 0.2).toFloat()
            sine // identical on L and R
        }
        val output = FloatArray(input.size)
        processor.process(input, output, channels = 2)

        var sumL = 0.0
        var sumR = 0.0
        val frames = numSamples

        for (f in 0 until frames) {
            sumL += output[f * 2]
            sumR += output[f * 2 + 1]
        }

        val dcOffsetL = sumL / frames
        val dcOffsetR = sumR / frames

        assertEquals("Left DC offset must be near 0", 0.0, dcOffsetL, 1e-4)
        assertEquals("Right DC offset must be near 0", 0.0, dcOffsetR, 1e-4)
        assertEquals("Left and Right must have 0 channel imbalance", dcOffsetL, dcOffsetR, 1e-6)
    }

    /**
     * Test A (Float PCM): a large left peak must reduce both channels by one common gain.
     */
    @Test
    fun testAsymmetricStereoPeaksFloat() {
        assertAsymmetricStereoFloat(largeOnLeft = true)
    }

    /**
     * Test A (Short PCM): the linked gain must also be bit-shared after quantization.
     */
    @Test
    fun testAsymmetricStereoPeaksShort() {
        assertAsymmetricStereoShort(largeOnLeft = true)
    }

    /**
     * Test B (Float PCM): reversing the large peak must behave symmetrically.
     */
    @Test
    fun testReverseChannelRolesFloat() {
        assertAsymmetricStereoFloat(largeOnLeft = false)
    }

    /**
     * Test B (Short PCM): reversed short PCM roles must remain symmetric.
     */
    @Test
    fun testReverseChannelRolesShort() {
        assertAsymmetricStereoShort(largeOnLeft = false)
    }

    /**
     * Test C (Float PCM): positive/negative peaks near full scale stay linked and bounded.
     */
    @Test
    fun testNearCeilingSamplesFloat() {
        val config = DspConfig(gainDb = 6.0, sampleRate = sampleRate, channelCount = 2)
        val ceiling = config.limiterThresholdLinear()
        val g = config.linearGain()
        val processor = DspProcessor(config)

        val input = floatArrayOf(
            1.0f, 0.25f,
            -1.0f, -0.25f,
            0.99f, 0.0f,
            -0.99f, 0.0f,
            0.9f, -0.225f,
            -0.9f, 0.225f
        )
        val output = FloatArray(input.size)
        val stats = processor.process(input, output, channels = 2)

        assertTrue("Near-full-scale samples must engage the limiter", stats.limiterActive)

        for (frame in 0 until input.size / 2) {
            val inL = input[frame * 2].toDouble()
            val inR = input[frame * 2 + 1].toDouble()
            val outL = output[frame * 2].toDouble()
            val outR = output[frame * 2 + 1].toDouble()

            assertTrue(
                "Frame $frame: left exceeds ceiling ($outL)",
                abs(outL) <= ceiling + maxFloatCeilingTolerance
            )
            assertTrue(
                "Frame $frame: right exceeds ceiling ($outR)",
                abs(outR) <= ceiling + maxFloatCeilingTolerance
            )

            if (inL != 0.0 && inR != 0.0) {
                assertEquals(
                    "Frame $frame: stereo ratio changed",
                    inL / inR,
                    outL / outR,
                    1e-3
                )
                val gainL = outL / (inL * g)
                val gainR = outR / (inR * g)
                assertEquals("Frame $frame: channels received different gain", gainL, gainR, 1e-5)
            }

            if (inL == 0.0) assertEquals("Zero left channel must remain zero", 0.0, outL, 0.0)
            if (inR == 0.0) assertEquals("Zero right channel must remain zero", 0.0, outR, 0.0)
            if (inL > 0.0) assertTrue("Left polarity lost at frame $frame", outL > 0.0)
            if (inL < 0.0) assertTrue("Left polarity lost at frame $frame", outL < 0.0)
            if (inR > 0.0) assertTrue("Right polarity lost at frame $frame", outR > 0.0)
            if (inR < 0.0) assertTrue("Right polarity lost at frame $frame", outR < 0.0)
        }

        assertTrue("Stats peak must stay at or below the ceiling", stats.outputPeakL <= ceiling + maxFloatCeilingTolerance)
        assertTrue("Stats peak must stay at or below the ceiling", stats.outputPeakR <= ceiling + maxFloatCeilingTolerance)
    }

    /**
     * Test C (Short PCM): near-full-scale signed samples stay linked and within the quantized ceiling.
     */
    @Test
    fun testNearCeilingSamplesShort() {
        val config = DspConfig(gainDb = 6.0, sampleRate = sampleRate, channelCount = 2)
        val ceiling = config.limiterThresholdLinear()
        val maxQuantizedCeiling = ceil(ceiling * 32768.0).toInt()
        val g = config.linearGain()
        val processor = DspProcessor(config)

        val input = shortArrayOf(
            32767, 8192,
            -32768, -8192,
            32000, 0,
            -32000, 0,
            32767, -8192,
            -32768, 8192
        )
        val output = ShortArray(input.size)
        val stats = processor.process(input, output, channels = 2)

        assertTrue("Near-full-scale short samples must engage the limiter", stats.limiterActive)

        for (frame in 0 until input.size / 2) {
            val inL = input[frame * 2].toInt()
            val inR = input[frame * 2 + 1].toInt()
            val outL = output[frame * 2].toInt()
            val outR = output[frame * 2 + 1].toInt()

            assertTrue("Frame $frame: left exceeds quantized ceiling", abs(outL) <= maxQuantizedCeiling)
            assertTrue("Frame $frame: right exceeds quantized ceiling", abs(outR) <= maxQuantizedCeiling)

            if (inL != 0 && inR != 0) {
                val expectedRatio = inL.toDouble() / inR
                assertEquals(
                    "Frame $frame: short stereo ratio changed",
                    expectedRatio,
                    outL.toDouble() / outR,
                    abs(expectedRatio) * 0.01 + 0.01
                )
                val gainL = outL / (inL * g)
                val gainR = outR / (inR * g)
                assertEquals("Frame $frame: quantized channels received different gain", gainL, gainR, 0.002)
            }

            if (inL == 0) assertEquals("Zero left channel must remain zero", 0, outL)
            if (inR == 0) assertEquals("Zero right channel must remain zero", 0, outR)
            if (inL > 0) assertTrue("Left polarity lost at frame $frame", outL > 0)
            if (inL < 0) assertTrue("Left polarity lost at frame $frame", outL < 0)
            if (inR > 0) assertTrue("Right polarity lost at frame $frame", outR > 0)
            if (inR < 0) assertTrue("Right polarity lost at frame $frame", outR < 0)
        }

        assertTrue("Stats peak must stay near the quantized ceiling", stats.outputPeakL <= ceiling + 1.0 / 32767.0)
        assertTrue("Stats peak must stay near the quantized ceiling", stats.outputPeakR <= ceiling + 1.0 / 32767.0)
    }

    /**
     * Test D (Float PCM): buffer boundaries must not change the sample stream while
     * the shared envelope state is retained. Only per-buffer telemetry may differ.
     */
    @Test
    fun testBlockBoundaryContinuityFloat() {
        val config = DspConfig(gainDb = 12.0, sampleRate = sampleRate, channelCount = 2)
        val frames = 512
        val input = FloatArray(frames * 2) { i ->
            val frame = i / 2
            if (i % 2 == 0) {
                (sin(2.0 * PI * 997.0 * frame / sampleRate) * 0.72).toFloat()
            } else {
                (sin(2.0 * PI * 331.0 * frame / sampleRate) * 0.06).toFloat()
            }
        }

        val continuousOutput = FloatArray(input.size)
        DspProcessor(config).process(input, continuousOutput, channels = 2)

        val chunkedOutput = FloatArray(input.size)
        val chunkedProcessor = DspProcessor(config)
        var offset = 0
        val blockFrames = 37
        while (offset < input.size) {
            val end = minOf(offset + blockFrames * 2, input.size)
            val blockInput = input.copyOfRange(offset, end)
            val blockOutput = FloatArray(blockInput.size)
            chunkedProcessor.process(blockInput, blockOutput, channels = 2)
            blockOutput.copyInto(chunkedOutput, offset)
            offset = end
        }

        for (i in input.indices) {
            assertEquals(
                "Buffer split changed float sample $i",
                continuousOutput[i],
                chunkedOutput[i],
                0.0f
            )
        }
    }

    /**
     * Test D (Short PCM): split-buffer output must match continuous output exactly.
     */
    @Test
    fun testBlockBoundaryContinuityShort() {
        val config = DspConfig(gainDb = 12.0, sampleRate = sampleRate, channelCount = 2)
        val frames = 256
        val input = ShortArray(frames * 2) { i ->
            val frame = i / 2
            if (i % 2 == 0) {
                (sin(2.0 * PI * 997.0 * frame / sampleRate) * 28000.0).toInt().toShort()
            } else {
                (sin(2.0 * PI * 331.0 * frame / sampleRate) * 2000.0).toInt().toShort()
            }
        }

        val continuousOutput = ShortArray(input.size)
        DspProcessor(config).process(input, continuousOutput, channels = 2)

        val chunkedOutput = ShortArray(input.size)
        val chunkedProcessor = DspProcessor(config)
        var offset = 0
        val blockFrames = 37
        while (offset < input.size) {
            val end = minOf(offset + blockFrames * 2, input.size)
            val blockInput = input.copyOfRange(offset, end)
            val blockOutput = ShortArray(blockInput.size)
            chunkedProcessor.process(blockInput, blockOutput, channels = 2)
            blockOutput.copyInto(chunkedOutput, offset)
            offset = end
        }

        for (i in input.indices) {
            assertEquals(
                "Buffer split changed short sample $i",
                continuousOutput[i],
                chunkedOutput[i]
            )
        }
    }

    /**
     * Test E: mono input keeps uniform gain at low level and ceiling protection at high level.
     */
    @Test
    fun testMonoUniformGainAndClipProtection() {
        val quietConfig = DspConfig(gainDb = 6.0, sampleRate = sampleRate, channelCount = 1)
        val g = quietConfig.linearGain()
        val quietProcessor = DspProcessor(quietConfig)
        val frames = 512
        val quietInput = generateSine(440.0, 0.1, frames)
        val quietOutput = FloatArray(frames)
        val quietStats = quietProcessor.process(quietInput, quietOutput, channels = 1)

        assertFalse("Quiet mono signal must not limit", quietStats.limiterActive)
        for (i in quietInput.indices) {
            assertEquals(
                "Mono sample $i did not receive uniform gain",
                quietInput[i].toDouble() * g,
                quietOutput[i].toDouble(),
                1e-6
            )
        }
        assertEquals("Mono right peak must mirror left", quietStats.outputPeakL, quietStats.outputPeakR, 0.0)
        assertEquals("Mono right RMS must mirror left", quietStats.outputRmsL, quietStats.outputRmsR, 0.0)

        val loudConfig = DspConfig(gainDb = 12.0, sampleRate = sampleRate, channelCount = 1)
        val loudProcessor = DspProcessor(loudConfig)
        val maxQuantizedCeiling = ceil(loudConfig.limiterThresholdLinear() * 32768.0).toInt()
        val loudInput = ShortArray(frames) { i ->
            (sin(2.0 * PI * 440.0 * i / sampleRate) * 28000.0).toInt().toShort()
        }
        val loudOutput = ShortArray(frames)
        val loudStats = loudProcessor.process(loudInput, loudOutput, channels = 1)

        assertTrue("Loud mono signal must engage the limiter", loudStats.limiterActive)
        for (i in loudOutput.indices) {
            assertTrue(
                "Mono sample $i exceeds quantized ceiling: ${loudOutput[i]}",
                abs(loudOutput[i].toInt()) <= maxQuantizedCeiling
            )
        }
        assertEquals("Mono right peak must mirror left", loudStats.outputPeakL, loudStats.outputPeakR, 0.0)
    }

    /**
     * Test F: bypass reproduces mono and stereo PCM unchanged for both sample formats.
     */
    @Test
    fun testBypassReproducesValidPcmUnchanged() {
        val config = DspConfig(gainDb = 12.0, bypass = true, sampleRate = sampleRate, channelCount = 2)
        val processor = DspProcessor(config)

        val floatInput = FloatArray(256) { i ->
            when (i % 5) {
                0 -> 0.0f
                1 -> 0.5f
                2 -> -0.75f
                3 -> 1.0f
                else -> -1.0f
            }
        }
        val floatOutput = FloatArray(floatInput.size)
        val floatStats = processor.process(floatInput, floatOutput, channels = 2)

        for (i in floatInput.indices) {
            assertEquals("Bypass modified float sample $i", floatInput[i], floatOutput[i], 0.0f)
        }
        assertFalse("Bypass must not report limiter activity", floatStats.limiterActive)
        assertEquals("Bypass maximum reduction must be zero", 0.0, floatStats.maxGainReductionDb, 0.0)
        assertEquals("Bypass effective gain must be unity", 1.0, floatStats.effectiveLinearGain, 0.0)
        assertEquals("Bypass must not count limited frames", 0L, floatStats.limitedFrameCount)

        val shortInput = shortArrayOf(
            0, 32767, -32768, 32767, 12345, -12345,
            1, -1, Short.MIN_VALUE, Short.MAX_VALUE
        )
        val shortOutput = ShortArray(shortInput.size)
        processor.process(shortInput, shortOutput, channels = 2)

        for (i in shortInput.indices) {
            assertEquals("Bypass modified short sample $i", shortInput[i], shortOutput[i])
        }
    }

    /**
     * Telemetry must describe the actual buffer output and distinguish the current
     * gain factor from the most negative factor reached during the buffer.
     */
    @Test
    fun testStatisticsDescribeProcessedBuffer() {
        val config = DspConfig(
            gainDb = 12.0,
            limiterEnabled = true,
            limiterThresholdDb = -0.1,
            attackTimeMs = 1.0,
            releaseTimeMs = 60.0,
            sampleRate = sampleRate,
            channelCount = 2
        )
        val processor = DspProcessor(config)
        val g = config.linearGain()
        val ceiling = config.limiterThresholdLinear()

        val frames = 1024
        val loudFrames = 64
        val input = FloatArray(frames * 2) { i ->
            val frame = i / 2
            val value = if (frame < loudFrames) 0.8 else 0.01
            if (i % 2 == 0) value.toFloat() else (value * 0.25).toFloat()
        }
        val output = FloatArray(input.size)
        val stats = processor.process(input, output, channels = 2)

        assertTrue("Loud buffer must report limiter activity", stats.limiterActive)
        assertTrue("Buffer must have meaningful attenuation", stats.maxGainReductionDb < -3.0)
        assertTrue(
            "Current reduction must be less severe than the buffer minimum",
            stats.currentGainReductionDb > stats.maxGainReductionDb
        )
        assertTrue("Release state must still be reducing gain", stats.currentGainReductionDb < 0.0)
        assertTrue("All frames should be limited in this test", stats.limitedFrameCount > loudFrames)
        assertTrue("Cumulative count cannot exceed processed frames", stats.limitedFrameCount <= frames)

        val lastFrame = frames - 1
        val lastGainL = output[lastFrame * 2] / (input[lastFrame * 2] * g)
        val lastGainR = output[lastFrame * 2 + 1] / (input[lastFrame * 2 + 1] * g)
        assertEquals("Final frame must use one linked gain", lastGainL, lastGainR, 1e-5)
        assertEquals(
            "currentGainReductionDb must describe the final frame",
            stats.currentGainReductionDb,
            20.0 * log10(lastGainL),
            1e-5
        )

        val minFactorFromStats = 10.0.pow(stats.maxGainReductionDb / 20.0)
        assertEquals(
            "effectiveLinearGain must equal requested gain times the minimum factor",
            g * minFactorFromStats,
            stats.effectiveLinearGain,
            1e-9
        )
        assertEquals(
            "Minimum attenuation must come from the linked ceiling guard",
            20.0 * log10(ceiling / (0.8 * g)),
            stats.maxGainReductionDb,
            1e-6
        )
        assertEquals(
            "Buffer peak must reach the linked ceiling",
            ceiling,
            stats.outputPeakL,
            1e-9
        )

        var peakL = 0.0
        var peakR = 0.0
        var sumSqL = 0.0
        var sumSqR = 0.0
        for (frame in 0 until frames) {
            val outL = output[frame * 2].toDouble()
            val outR = output[frame * 2 + 1].toDouble()
            if (abs(outL) > peakL) peakL = abs(outL)
            if (abs(outR) > peakR) peakR = abs(outR)
            sumSqL += outL * outL
            sumSqR += outR * outR
        }
        assertEquals("Output peak L must describe the buffer", peakL, stats.outputPeakL, 1e-6)
        assertEquals("Output peak R must describe the buffer", peakR, stats.outputPeakR, 1e-6)
        assertEquals("Output RMS L must describe the buffer", sqrt(sumSqL / frames), stats.outputRmsL, 1e-6)
        assertEquals("Output RMS R must describe the buffer", sqrt(sumSqR / frames), stats.outputRmsR, 1e-6)
    }

    /**
     * Disabling the limiter must not fall back to independent per-channel clipping.
     * A stateless common full-scale guard attenuates over-range frames instead.
     */
    @Test
    fun testLimiterDisabledAsymmetricStereoPeaksFloat() {
        assertLimiterDisabledAsymmetricFloat(largeOnLeft = true)
    }

    @Test
    fun testLimiterDisabledReverseChannelRolesFloat() {
        assertLimiterDisabledAsymmetricFloat(largeOnLeft = false)
    }

    @Test
    fun testLimiterDisabledAsymmetricStereoPeaksShort() {
        assertLimiterDisabledAsymmetricShort(largeOnLeft = true)
    }

    @Test
    fun testLimiterDisabledReverseChannelRolesShort() {
        assertLimiterDisabledAsymmetricShort(largeOnLeft = false)
    }

    /**
     * Finite out-of-range float inputs stay finite and bounded through the disabled-mode
     * guard. Non-finite inputs become 0.0, including in the unchanged bypass path.
     */
    @Test
    fun testLimiterDisabledOutOfRangeFloatInputsAreFiniteAndLinked() {
        val config = DspConfig(
            gainDb = 12.0,
            limiterEnabled = false,
            sampleRate = sampleRate,
            channelCount = 2
        )
        val g = config.linearGain()
        val input = floatArrayOf(
            2.0f, 1.0f,
            -2.0f, -1.0f,
            Float.NaN, 0.5f,
            Float.POSITIVE_INFINITY, 0.25f,
            Float.NEGATIVE_INFINITY, -0.25f,
            0.0f, 0.0f
        )
        val output = FloatArray(input.size)
        val stats = DspProcessor(config).process(input, output, channels = 2)

        assertFalse("The full-scale safety guard must not report limiter activity", stats.limiterActive)
        assertEquals("Only the three over-range frames must be attenuated", 3L, stats.limitedFrameCount)
        assertEquals("Samples processed must describe the buffer", input.size.toLong(), stats.samplesProcessed)
        assertEquals(
            "Maximum reduction must come from the 2.0 peak",
            20.0 * log10(1.0 / (2.0 * g)),
            stats.maxGainReductionDb,
            1e-6
        )
        assertEquals("Final silent frame must have unity gain", 0.0, stats.currentGainReductionDb, 0.0)
        assertEquals("Effective gain must include the full-scale guard", 0.5, stats.effectiveLinearGain, 1e-9)

        val expected = doubleArrayOf(
            1.0, 0.5,
            -1.0, -0.5,
            0.0, 1.0,
            0.0, 0.25 * g,
            0.0, -0.25 * g,
            0.0, 0.0
        )
        for (i in output.indices) {
            val value = output[i].toDouble()
            assertFalse("Output contains NaN at index $i", value.isNaN())
            assertFalse("Output contains infinity at index $i", value.isInfinite())
            assertTrue(
                "Output exceeds full scale at index $i: $value",
                abs(value) <= 1.0 + maxFloatCeilingTolerance
            )
            assertEquals("Out-of-range input mismatch at index $i", expected[i], value, 1e-6)
        }

        assertEquals("Left output peak must reach full scale", 1.0, stats.outputPeakL, maxFloatCeilingTolerance)
        assertTrue("Right output peak must stay bounded", stats.outputPeakR <= 1.0 + maxFloatCeilingTolerance)

        // Bypass behavior is unchanged: finite samples pass through unaltered,
        // non-finite samples are sanitized to 0.0, and no ceiling is claimed.
        val bypassConfig = DspConfig(
            gainDb = 12.0,
            bypass = true,
            limiterEnabled = false,
            sampleRate = sampleRate,
            channelCount = 2
        )
        val bypassOutput = FloatArray(input.size)
        DspProcessor(bypassConfig).process(input, bypassOutput, channels = 2)
        for (i in input.indices) {
            val expectedBypass = if (input[i].isNaN() || input[i].isInfinite()) 0.0f else input[i]
            assertEquals("Bypass changed sample $i", expectedBypass, bypassOutput[i], 0.0f)
        }
    }

    private fun assertLimiterDisabledAsymmetricFloat(largeOnLeft: Boolean) {
        val config = DspConfig(
            gainDb = 12.0,
            limiterEnabled = false,
            sampleRate = sampleRate,
            channelCount = 2
        )
        val g = config.linearGain()
        val ceiling = config.limiterThresholdLinear()
        val large = 0.5f
        val small = 0.01f
        val frames = 64

        val input = FloatArray(frames * 2)
        for (frame in 0 until frames) {
            val silent = frame % 8 == 5
            val quiet = if (silent || frame % 4 == 3) 0.0f else small
            val loud = if (silent) 0.0f else large
            input[frame * 2] = if (largeOnLeft) loud else quiet
            input[frame * 2 + 1] = if (largeOnLeft) quiet else loud
        }

        val output = FloatArray(input.size)
        val stats = DspProcessor(config).process(input, output, channels = 2)

        val expectedMinFactor = 1.0 / (large.toDouble() * g)
        val expectedMaxReductionDb = 20.0 * log10(expectedMinFactor)
        val limitedFrames = (0 until frames).count { it % 8 != 5 }

        assertFalse("Disabled limiter must not report limiter activity", stats.limiterActive)
        assertEquals("Safety guard must count every over-range frame", limitedFrames.toLong(), stats.limitedFrameCount)
        assertEquals(
            "Maximum reduction must describe the full-scale guard",
            expectedMaxReductionDb,
            stats.maxGainReductionDb,
            1e-6
        )
        assertEquals(
            "Final frame must use the full-scale guard",
            expectedMaxReductionDb,
            stats.currentGainReductionDb,
            1e-6
        )
        assertEquals(
            "Effective gain must include the full-scale guard",
            1.0 / large.toDouble(),
            stats.effectiveLinearGain,
            1e-9
        )
        assertEquals("Samples processed must describe the buffer", input.size.toLong(), stats.samplesProcessed)

        for (frame in 0 until frames) {
            val inL = input[frame * 2].toDouble()
            val inR = input[frame * 2 + 1].toDouble()
            val outL = output[frame * 2].toDouble()
            val outR = output[frame * 2 + 1].toDouble()

            assertFalse("Output contains NaN at frame $frame", outL.isNaN() || outR.isNaN())
            assertFalse("Output contains infinity at frame $frame", outL.isInfinite() || outR.isInfinite())
            assertTrue(
                "Left exceeds full scale at frame $frame: $outL",
                abs(outL) <= 1.0 + maxFloatCeilingTolerance
            )
            assertTrue(
                "Right exceeds full scale at frame $frame: $outR",
                abs(outR) <= 1.0 + maxFloatCeilingTolerance
            )

            if (inL == 0.0 && inR == 0.0) {
                assertEquals("Silent left channel must remain zero", 0.0, outL, 0.0)
                assertEquals("Silent right channel must remain zero", 0.0, outR, 0.0)
                continue
            }

            val loudOut = if (largeOnLeft) outL else outR
            val quietIn = if (largeOnLeft) inR else inL
            val quietOut = if (largeOnLeft) outR else outL

            assertEquals("Loud channel must reach full scale", 1.0, abs(loudOut), maxFloatCeilingTolerance)
            assertTrue(
                "Disabled limiter must target full scale, not the limiter threshold",
                abs(loudOut) > ceiling
            )

            if (quietIn == 0.0) {
                assertEquals("Zero-valued channel must remain zero", 0.0, quietOut, 0.0)
            } else {
                assertEquals(
                    "Quiet channel must receive the same linked attenuation",
                    quietIn / large.toDouble(),
                    abs(quietOut),
                    1e-9
                )
                assertEquals(
                    "Stereo ratio changed at frame $frame",
                    inL / inR,
                    outL / outR,
                    1e-4
                )
            }
        }

        assertEquals(
            "Loud output peak must reach full scale",
            1.0,
            if (largeOnLeft) stats.outputPeakL else stats.outputPeakR,
            maxFloatCeilingTolerance
        )
        assertEquals(
            "Quiet output peak must reflect the linked attenuation",
            small.toDouble() / large,
            if (largeOnLeft) stats.outputPeakR else stats.outputPeakL,
            1e-6
        )
    }

    private fun assertLimiterDisabledAsymmetricShort(largeOnLeft: Boolean) {
        val config = DspConfig(
            gainDb = 12.0,
            limiterEnabled = false,
            sampleRate = sampleRate,
            channelCount = 2
        )
        val g = config.linearGain()
        val ceiling = config.limiterThresholdLinear()
        val maxQuantizedCeiling = ceil(ceiling * 32768.0).toInt()
        val large = (0.5 * 32767.0).roundToInt().toShort()
        val small = (0.01 * 32767.0).roundToInt().toShort()
        val largeNorm = large.toDouble() / 32767.0
        val smallNorm = small.toDouble() / 32767.0
        val expectedQuiet = (smallNorm / largeNorm * 32767.0).roundToInt()
        val frames = 64

        val input = ShortArray(frames * 2)
        for (frame in 0 until frames) {
            val silent = frame % 8 == 5
            val quiet = if (silent || frame % 4 == 3) 0 else small
            val loud = if (silent) 0 else large
            input[frame * 2] = if (largeOnLeft) loud else quiet
            input[frame * 2 + 1] = if (largeOnLeft) quiet else loud
        }

        val output = ShortArray(input.size)
        val stats = DspProcessor(config).process(input, output, channels = 2)

        val expectedMinFactor = 1.0 / (largeNorm * g)
        val expectedMaxReductionDb = 20.0 * log10(expectedMinFactor)
        val limitedFrames = (0 until frames).count { it % 8 != 5 }

        assertFalse("Disabled limiter must not report limiter activity", stats.limiterActive)
        assertEquals("Safety guard must count every over-range frame", limitedFrames.toLong(), stats.limitedFrameCount)
        assertEquals(
            "Maximum reduction must describe the full-scale guard",
            expectedMaxReductionDb,
            stats.maxGainReductionDb,
            1e-6
        )
        assertEquals(
            "Final frame must use the full-scale guard",
            expectedMaxReductionDb,
            stats.currentGainReductionDb,
            1e-6
        )
        assertEquals(
            "Effective gain must include the full-scale guard",
            1.0 / largeNorm,
            stats.effectiveLinearGain,
            1e-9
        )

        val expectedRatio = if (largeOnLeft) {
            large.toDouble() / small.toDouble()
        } else {
            small.toDouble() / large.toDouble()
        }
        for (frame in 0 until frames) {
            val inL = input[frame * 2].toInt()
            val inR = input[frame * 2 + 1].toInt()
            val outL = output[frame * 2].toInt()
            val outR = output[frame * 2 + 1].toInt()

            assertTrue("Left exceeds 16-bit full scale at frame $frame", abs(outL) <= Short.MAX_VALUE.toInt())
            assertTrue("Right exceeds 16-bit full scale at frame $frame", abs(outR) <= Short.MAX_VALUE.toInt())

            if (inL == 0 && inR == 0) {
                assertEquals("Silent left channel must remain zero", 0, outL)
                assertEquals("Silent right channel must remain zero", 0, outR)
                continue
            }

            val loudOut = if (largeOnLeft) outL else outR
            val quietIn = if (largeOnLeft) inR else inL
            val quietOut = if (largeOnLeft) outR else outL

            assertEquals("Loud channel must reach 16-bit full scale", 32767, abs(loudOut))
            assertTrue(
                "Disabled limiter must target full scale, not the limiter threshold",
                abs(loudOut) > maxQuantizedCeiling
            )

            if (quietIn == 0) {
                assertEquals("Zero-valued channel must remain zero", 0, quietOut)
            } else {
                assertEquals("Quiet channel must receive the linked attenuation", expectedQuiet, abs(quietOut))
                assertEquals(
                    "Frame $frame: quantized channels received different gain",
                    loudOut / (large * g),
                    quietOut / (small * g),
                    0.002
                )
                assertEquals(
                    "Frame $frame: quantized stereo ratio changed",
                    expectedRatio,
                    outL.toDouble() / outR.toDouble(),
                    abs(expectedRatio) * 0.01
                )
            }
        }

        assertEquals(
            "Loud output peak must reach digital full scale",
            1.0,
            if (largeOnLeft) stats.outputPeakL else stats.outputPeakR,
            0.0
        )
        assertEquals(
            "Quiet output peak must reflect the linked attenuation",
            expectedQuiet / 32767.0,
            if (largeOnLeft) stats.outputPeakR else stats.outputPeakL,
            1e-9
        )
    }

    private fun assertAsymmetricStereoFloat(largeOnLeft: Boolean) {
        val config = DspConfig(gainDb = 12.0, sampleRate = sampleRate, channelCount = 2)
        val g = config.linearGain()
        val ceiling = config.limiterThresholdLinear()
        val large = 0.5f
        val small = 0.01f
        val inL = if (largeOnLeft) large else small
        val inR = if (largeOnLeft) small else large

        val frames = 64
        val input = FloatArray(frames * 2) { i -> if (i % 2 == 0) inL else inR }
        val output = FloatArray(input.size)
        val stats = DspProcessor(config).process(input, output, channels = 2)

        assertTrue("Limiter must engage for asymmetric peaks", stats.limiterActive)
        assertTrue("Linked limiter must attenuate the frame", stats.maxGainReductionDb < -1.0)

        val expectedRatio = inL.toDouble() / inR.toDouble()
        for (frame in 0 until frames) {
            val yL = output[frame * 2].toDouble()
            val yR = output[frame * 2 + 1].toDouble()
            val gainL = yL / (inL * g)
            val gainR = yR / (inR * g)

            assertEquals("Frame $frame: channels received different gain", gainL, gainR, 1e-5)
            assertEquals("Frame $frame: stereo ratio changed", expectedRatio, yL / yR, 1e-3)
            assertTrue("Frame $frame: left exceeds ceiling", abs(yL) <= ceiling + maxFloatCeilingTolerance)
            assertTrue("Frame $frame: right exceeds ceiling", abs(yR) <= ceiling + maxFloatCeilingTolerance)

            val quietChannelOutput = if (largeOnLeft) yR else yL
            assertTrue(
                "Quiet channel was independently saturated",
                abs(quietChannelOutput) < ceiling * 0.1
            )
        }

        assertEquals("Every frame must use the linked limiter", frames.toLong(), stats.limitedFrameCount)
    }

    private fun assertAsymmetricStereoShort(largeOnLeft: Boolean) {
        val config = DspConfig(gainDb = 12.0, sampleRate = sampleRate, channelCount = 2)
        val g = config.linearGain()
        val ceiling = config.limiterThresholdLinear()
        val maxQuantizedCeiling = ceil(ceiling * 32768.0).toInt()
        val large = (0.5 * 32767.0).roundToInt().toShort()
        val small = (0.01 * 32767.0).roundToInt().toShort()
        val inL = if (largeOnLeft) large else small
        val inR = if (largeOnLeft) small else large

        val frames = 64
        val input = ShortArray(frames * 2) { i -> if (i % 2 == 0) inL else inR }
        val output = ShortArray(input.size)
        val stats = DspProcessor(config).process(input, output, channels = 2)

        assertTrue("Limiter must engage for asymmetric peaks", stats.limiterActive)
        assertTrue("Linked limiter must attenuate the frame", stats.maxGainReductionDb < -1.0)

        val expectedRatio = inL.toDouble() / inR.toDouble()
        for (frame in 0 until frames) {
            val yL = output[frame * 2].toInt()
            val yR = output[frame * 2 + 1].toInt()
            val gainL = yL / (inL * g)
            val gainR = yR / (inR * g)

            assertEquals("Frame $frame: quantized channels received different gain", gainL, gainR, 0.01)
            assertEquals(
                "Frame $frame: quantized stereo ratio changed",
                expectedRatio,
                yL.toDouble() / yR.toDouble(),
                abs(expectedRatio) * 0.01
            )
            assertTrue("Frame $frame: left exceeds quantized ceiling", abs(yL) <= maxQuantizedCeiling)
            assertTrue("Frame $frame: right exceeds quantized ceiling", abs(yR) <= maxQuantizedCeiling)

            val quietChannelOutput = if (largeOnLeft) yR else yL
            assertTrue(
                "Quiet channel was independently saturated",
                abs(quietChannelOutput) < maxQuantizedCeiling / 10
            )
        }

        assertEquals("Every frame must use the linked limiter", frames.toLong(), stats.limitedFrameCount)
    }

    private fun generateSine(freq: Double, amplitude: Double, length: Int): FloatArray {
        return FloatArray(length) { i ->
            (sin(2.0 * PI * freq * i / sampleRate) * amplitude).toFloat()
        }
    }
}
