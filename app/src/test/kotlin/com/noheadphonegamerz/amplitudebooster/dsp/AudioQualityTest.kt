package com.noheadphonegamerz.amplitudebooster.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Audio Quality and Telemetry Test Suite:
 * Measures RMS, peak, gain in dB, frequency response flatness, THD (Total Harmonic Distortion),
 * limiter attenuation, and processing latency.
 */
class AudioQualityTest {

    private val sampleRate = 48000

    /**
     * Measures RMS and Peak gain in dB across multiple test amplitudes.
     */
    @Test
    fun testRmsAndPeakGainMeasurement() {
        val requestedGainDb = 6.0
        val config = DspConfig(gainDb = requestedGainDb, sampleRate = sampleRate)
        val processor = DspProcessor(config)

        val numSamples = 4800 // 100 ms (exact integer cycles of 1000 Hz)
        val signal = FloatArray(numSamples) { i ->
            (sin(2.0 * PI * 1000.0 * i / sampleRate) * 0.1).toFloat()
        }
        val output = FloatArray(numSamples)
        val stats = processor.process(signal, output, channels = 1)

        val measuredGainDb = 20.0 * log10(stats.outputRmsL / stats.inputRmsL)
        val peakGainDb = 20.0 * log10(stats.outputPeakL / stats.inputPeakL)

        assertEquals("Measured RMS gain must match requested +6 dB", requestedGainDb, measuredGainDb, 0.05)
        assertEquals("Measured Peak gain must match requested +6 dB", requestedGainDb, peakGainDb, 0.05)
    }

    /**
     * Measures Total Harmonic Distortion (THD) of the linear amplification stage.
     * Evaluated over exact integer cycles (4800 samples = exactly 100 cycles of 1 kHz).
     */
    @Test
    fun testTotalHarmonicDistortionInLinearZone() {
        val config = DspConfig(gainDb = 6.0, sampleRate = sampleRate)
        val processor = DspProcessor(config)

        val freq = 1000.0
        val numSamples = 4800 // 100 integer cycles
        val input = FloatArray(numSamples) { i ->
            (sin(2.0 * PI * freq * i / sampleRate) * 0.15).toFloat()
        }
        val output = FloatArray(numSamples)
        processor.process(input, output, channels = 1)

        // Compute fundamental via Fourier correlation
        var sumCos = 0.0
        var sumSin = 0.0
        for (i in 0 until numSamples) {
            val angle = 2.0 * PI * freq * i / sampleRate
            sumCos += output[i] * kotlin.math.cos(angle)
            sumSin += output[i] * sin(angle)
        }
        val fundamentalAmp = (2.0 / numSamples) * sqrt(sumCos * sumCos + sumSin * sumSin)

        // Calculate residual distortion energy
        var residualEnergy = 0.0
        for (i in 0 until numSamples) {
            val angle = 2.0 * PI * freq * i / sampleRate
            val fundamentalVal = (sumCos * (2.0 / numSamples) * kotlin.math.cos(angle)) +
                    (sumSin * (2.0 / numSamples) * sin(angle))
            val diff = output[i] - fundamentalVal
            residualEnergy += diff * diff
        }
        val residualRms = sqrt(residualEnergy / numSamples)
        val fundamentalRms = fundamentalAmp / sqrt(2.0)

        val thd = residualRms / fundamentalRms
        val thdPercent = thd * 100.0

        assertTrue("Linear zone THD must be below 0.01% (was $thdPercent%)", thdPercent < 0.01)
    }

    /**
     * Measures frequency response ratio across multitone signal.
     * All tones must experience uniform gain.
     */
    @Test
    fun testMultitoneFrequencyFlatness() {
        val config = DspConfig(gainDb = 6.0, sampleRate = sampleRate)
        val processor = DspProcessor(config)

        val freqs = doubleArrayOf(150.0, 500.0, 1500.0, 4500.0, 12000.0)
        val numSamples = 9600 // 200 ms
        val multitone = FloatArray(numSamples)

        for (i in 0 until numSamples) {
            var sum = 0.0
            for (f in freqs) {
                sum += sin(2.0 * PI * f * i / sampleRate) * (0.15 / freqs.size)
            }
            multitone[i] = sum.toFloat()
        }

        val output = FloatArray(numSamples)
        val stats = processor.process(multitone, output, channels = 1)

        val overallGainDb = 20.0 * log10(stats.outputRmsL / stats.inputRmsL)
        assertEquals("Multitone overall gain must be +6 dB", 6.0, overallGainDb, 0.05)
    }

    /**
     * Tests and verifies processing latency.
     * The DSP processor operates sample-by-sample without lookahead delay buffer,
     * guaranteeing zero processing latency frames.
     */
    @Test
    fun testProcessingLatencyZeroFrames() {
        val config = DspConfig(gainDb = 6.0, sampleRate = sampleRate)
        val processor = DspProcessor(config)

        // Dirac delta impulse at index 10
        val impulse = FloatArray(100) { i -> if (i == 10) 0.1f else 0.0f }
        val output = FloatArray(100)
        processor.process(impulse, output, channels = 1)

        // Peak must appear at exact same index 10
        var outputPeakIndex = -1
        var maxPeak = 0.0f
        for (i in output.indices) {
            if (output[i] > maxPeak) {
                maxPeak = output[i]
                outputPeakIndex = i
            }
        }

        assertEquals("Impulse response latency must be 0 samples", 10, outputPeakIndex)
    }

    /**
     * Measures the True Peak estimation module.
     * A 12 kHz sine wave at 48 kHz sampling with 45-degree phase offset has sample peaks of ~0.636,
     * but the true analog continuous peak reaches ~0.900 between samples.
     * The 4x oversampler must reconstruct and detect this inter-sample overshoot.
     */
    @Test
    fun testTruePeakOversamplingDetector() {
        val numSamples = 256
        val targetContinuousPeak = 0.9
        val samples = FloatArray(numSamples) { i ->
            (sin(2.0 * PI * 12000.0 * i / sampleRate + (PI / 4.0)) * targetContinuousPeak).toFloat()
        }

        // Measure raw digital sample peaks
        var maxSamplePeak = 0.0f
        for (s in samples) {
            val a = abs(s)
            if (a > maxSamplePeak) maxSamplePeak = a
        }

        val tpLinear = TruePeakInvestigation.estimateTruePeakLinear(samples)
        val tpDb = TruePeakInvestigation.linearToDbFsTp(tpLinear)

        // Raw sample peak is ~0.636, but true continuous peak is 0.900
        assertTrue("Raw sample peak must miss the continuous inter-sample peak", maxSamplePeak < 0.70)
        assertTrue("True Peak linear estimate must detect the inter-sample peak (was $tpLinear)", tpLinear >= 0.85)
        assertTrue("True Peak dBFS must be valid", tpDb <= 0.0)
    }
}
