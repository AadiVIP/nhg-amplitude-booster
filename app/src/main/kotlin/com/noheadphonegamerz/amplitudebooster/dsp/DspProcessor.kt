package com.noheadphonegamerz.amplitudebooster.dsp

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * High-performance, memory-efficient DSP processing engine for uniform
 * audio amplitude boosting with clipping protection.
 *
 * The linear gain stage is:
 * y[n] = g * x[n]
 *
 * Where g = 10^(gainDb / 20).
 *
 * The limiter computes one gain-reduction factor from the louder of the two
 * post-gain channels and applies that same factor to both channels. No
 * per-channel nonlinear stage is used, so a stereo frame keeps its original
 * left/right ratio through gain reduction and quantization.
 *
 * When limiterEnabled is false, the envelope limiter is bypassed. A stateless
 * full-scale guard still applies one common attenuation factor to any frame
 * whose amplified linked peak exceeds 1.0. This prevents independent clipping
 * and overflow while preserving the stereo ratio; it does not make the output
 * transparent, and [DspStatistics.limiterActive] remains false for this guard.
 *
 * [config] is safely published for cross-thread updates. Each processing call
 * snapshots it once, so a buffer is always rendered with one coherent
 * configuration even if the UI updates settings concurrently.
 */
class DspProcessor(config: DspConfig = DspConfig()) {

    @Volatile
    var config: DspConfig = config

    // Shared limiter state. It is intentionally retained between process() calls
    // so that adjacent buffers do not reset attack/release ballistics.
    private var envelope: Double = 0.0
    private var totalLimitedFrames: Long = 0L

    /**
     * Resets the limiter envelope and cumulative statistics.
     */
    fun reset() {
        envelope = 0.0
        totalLimitedFrames = 0L
    }

    /**
     * Processes signed 16-bit PCM audio samples.
     *
     * @param input Interleaved 16-bit PCM source samples.
     * @param output Destination buffer for processed samples (can be the same as input).
     * @param channels Number of channels (1 for mono, 2 for stereo). Defaults to the
     * channel count from the configuration snapshot taken for this call.
     * @return DspStatistics describing the processed buffer.
     */
    fun process(input: ShortArray, output: ShortArray, channels: Int = USE_CONFIG_CHANNELS): DspStatistics {
        val cfg = config
        require(input.size == output.size) { "Input and output buffers must have the same size" }
        val channelCount = if (channels == USE_CONFIG_CHANNELS) cfg.channelCount else channels
        require(channelCount in 1..2) { "Only mono (1) and stereo (2) are supported" }

        val g = cfg.linearGain()
        val bypass = cfg.bypass || cfg.gainDb == 0.0
        val limiterEnabled = cfg.limiterEnabled
        val ceiling = cfg.limiterThresholdLinear()
        val sampleRate = cfg.sampleRate.toDouble()

        val dt = 1.0 / sampleRate
        val alphaAttack = 1.0 - exp(-dt / max(1e-5, cfg.attackTimeMs * 1e-3))
        val alphaRelease = 1.0 - exp(-dt / max(1e-5, cfg.releaseTimeMs * 1e-3))

        var inPeakL = 0.0
        var inPeakR = 0.0
        var inSumSqL = 0.0
        var inSumSqR = 0.0

        var outPeakL = 0.0
        var outPeakR = 0.0
        var outSumSqL = 0.0
        var outSumSqR = 0.0

        var minGainFactor = 1.0
        var lastGainFactor = 1.0
        var bufferLimitedFrames = 0L

        val totalFrames = input.size / channelCount

        for (frame in 0 until totalFrames) {
            val idx = frame * channelCount

            // Read & normalize 16-bit PCM [-32768, 32767] -> [-1.0, +1.0]
            val rawL = input[idx].toInt()
            val xL = if (rawL < 0) rawL / 32768.0 else rawL / 32767.0

            val rawR = if (channelCount == 2) input[idx + 1].toInt() else rawL
            val xR = if (channelCount == 2) {
                if (rawR < 0) rawR / 32768.0 else rawR / 32767.0
            } else xL

            // Track input metering
            val absInL = abs(xL)
            val absInR = abs(xR)
            if (absInL > inPeakL) inPeakL = absInL
            if (absInR > inPeakR) inPeakR = absInR
            inSumSqL += xL * xL
            inSumSqR += xR * xR

            // Bit-exact bypass at zero gain or explicit bypass
            if (bypass) {
                output[idx] = input[idx]
                if (channelCount == 2) output[idx + 1] = input[idx + 1]

                if (absInL > outPeakL) outPeakL = absInL
                if (absInR > outPeakR) outPeakR = absInR
                outSumSqL += xL * xL
                outSumSqR += xR * xR
                continue
            }

            // Requested uniform linear gain
            val ampL = xL * g
            val ampR = xR * g

            // One linked gain factor for the whole stereo frame
            val linkedPeak = max(abs(ampL), abs(ampR))
            val gainFactor = linkedLimiterGain(
                linkedPeak = linkedPeak,
                limiterEnabled = limiterEnabled,
                ceiling = ceiling,
                alphaAttack = alphaAttack,
                alphaRelease = alphaRelease
            )

            if (gainFactor < minGainFactor) minGainFactor = gainFactor
            lastGainFactor = gainFactor
            if (gainFactor < 1.0) bufferLimitedFrames++

            // Identical multiplier on both channels: stereo balance is preserved
            val yL = ampL * gainFactor
            val yR = ampR * gainFactor

            // Safe conversion back to 16-bit signed PCM
            val sampleOutL = quantize16(yL)
            output[idx] = sampleOutL

            val normOutL = normalizeShort(sampleOutL)
            val absOutL = abs(normOutL)
            if (absOutL > outPeakL) outPeakL = absOutL
            outSumSqL += normOutL * normOutL

            if (channelCount == 2) {
                val sampleOutR = quantize16(yR)
                output[idx + 1] = sampleOutR

                val normOutR = normalizeShort(sampleOutR)
                val absOutR = abs(normOutR)
                if (absOutR > outPeakR) outPeakR = absOutR
                outSumSqR += normOutR * normOutR
            }
        }

        totalLimitedFrames += bufferLimitedFrames
        if (channelCount == 1) outPeakR = outPeakL

        val count = max(1, totalFrames).toDouble()
        val inRmsL = sqrt(inSumSqL / count)
        val inRmsR = if (channelCount == 2) sqrt(inSumSqR / count) else inRmsL
        val outRmsL = sqrt(outSumSqL / count)
        val outRmsR = if (channelCount == 2) sqrt(outSumSqR / count) else outRmsL

        return DspStatistics(
            inputPeakL = inPeakL,
            inputPeakR = inPeakR,
            inputRmsL = inRmsL,
            inputRmsR = inRmsR,
            outputPeakL = outPeakL,
            outputPeakR = outPeakR,
            outputRmsL = outRmsL,
            outputRmsR = outRmsR,
            maxGainReductionDb = gainFactorToDb(minGainFactor),
            currentGainReductionDb = gainFactorToDb(lastGainFactor),
            limiterActive = limiterEnabled && bufferLimitedFrames > 0L,
            limitedFrameCount = totalLimitedFrames,
            samplesProcessed = input.size.toLong(),
            requestedLinearGain = g,
            effectiveLinearGain = if (bypass) 1.0 else g * minGainFactor
        )
    }

    /**
     * Processes 32-bit floating-point PCM audio samples.
     * Input samples are nominally in the range [-1.0f, +1.0f].
     *
     * Non-finite input samples (NaN, +infinity, -infinity) are replaced with 0.0.
     * Finite samples outside the nominal range are processed normally: the shared
     * gain and frame-level guard keep non-bypassed output finite and within the
     * configured limiter ceiling, or within full scale when the limiter is disabled.
     * Bypass remains bit-transparent for finite inputs, including out-of-range values,
     * and sanitizes non-finite inputs to 0.0.
     *
     * @param channels Number of channels (1 for mono, 2 for stereo). Defaults to the
     * channel count from the configuration snapshot taken for this call.
     */
    fun process(input: FloatArray, output: FloatArray, channels: Int = USE_CONFIG_CHANNELS): DspStatistics {
        val cfg = config
        require(input.size == output.size) { "Input and output buffers must have the same size" }
        val channelCount = if (channels == USE_CONFIG_CHANNELS) cfg.channelCount else channels
        require(channelCount in 1..2) { "Only mono (1) and stereo (2) are supported" }

        val g = cfg.linearGain()
        val bypass = cfg.bypass || cfg.gainDb == 0.0
        val limiterEnabled = cfg.limiterEnabled
        val ceiling = cfg.limiterThresholdLinear()
        val sampleRate = cfg.sampleRate.toDouble()

        val dt = 1.0 / sampleRate
        val alphaAttack = 1.0 - exp(-dt / max(1e-5, cfg.attackTimeMs * 1e-3))
        val alphaRelease = 1.0 - exp(-dt / max(1e-5, cfg.releaseTimeMs * 1e-3))

        var inPeakL = 0.0
        var inPeakR = 0.0
        var inSumSqL = 0.0
        var inSumSqR = 0.0

        var outPeakL = 0.0
        var outPeakR = 0.0
        var outSumSqL = 0.0
        var outSumSqR = 0.0

        var minGainFactor = 1.0
        var lastGainFactor = 1.0
        var bufferLimitedFrames = 0L

        val totalFrames = input.size / channelCount

        for (frame in 0 until totalFrames) {
            val idx = frame * channelCount

            // Sanitize against NaN / Infinity
            var xL = input[idx].toDouble()
            if (xL.isNaN() || xL.isInfinite()) xL = 0.0

            var xR = if (channelCount == 2) input[idx + 1].toDouble() else xL
            if (xR.isNaN() || xR.isInfinite()) xR = 0.0

            // Input metering
            val absInL = abs(xL)
            val absInR = abs(xR)
            if (absInL > inPeakL) inPeakL = absInL
            if (absInR > inPeakR) inPeakR = absInR
            inSumSqL += xL * xL
            inSumSqR += xR * xR

            if (bypass) {
                output[idx] = xL.toFloat()
                if (channelCount == 2) output[idx + 1] = xR.toFloat()

                if (absInL > outPeakL) outPeakL = absInL
                if (absInR > outPeakR) outPeakR = absInR
                outSumSqL += xL * xL
                outSumSqR += xR * xR
                continue
            }

            // Requested uniform linear gain
            val ampL = xL * g
            val ampR = xR * g

            // One linked gain factor for the whole stereo frame
            val linkedPeak = max(abs(ampL), abs(ampR))
            val gainFactor = linkedLimiterGain(
                linkedPeak = linkedPeak,
                limiterEnabled = limiterEnabled,
                ceiling = ceiling,
                alphaAttack = alphaAttack,
                alphaRelease = alphaRelease
            )

            if (gainFactor < minGainFactor) minGainFactor = gainFactor
            lastGainFactor = gainFactor
            if (gainFactor < 1.0) bufferLimitedFrames++

            // Identical multiplier on both channels. The linked guard (limiter or
            // full-scale safety) prevents overflow, so no per-channel clamp is used.
            val yL = finiteOrZero(ampL * gainFactor)
            val yR = finiteOrZero(ampR * gainFactor)

            output[idx] = yL.toFloat()

            val absOutL = abs(yL)
            if (absOutL > outPeakL) outPeakL = absOutL
            outSumSqL += yL * yL

            if (channelCount == 2) {
                output[idx + 1] = yR.toFloat()

                val absOutR = abs(yR)
                if (absOutR > outPeakR) outPeakR = absOutR
                outSumSqR += yR * yR
            }
        }

        totalLimitedFrames += bufferLimitedFrames
        if (channelCount == 1) outPeakR = outPeakL

        val count = max(1, totalFrames).toDouble()
        val inRmsL = sqrt(inSumSqL / count)
        val inRmsR = if (channelCount == 2) sqrt(inSumSqR / count) else inRmsL
        val outRmsL = sqrt(outSumSqL / count)
        val outRmsR = if (channelCount == 2) sqrt(outSumSqR / count) else outRmsL

        return DspStatistics(
            inputPeakL = inPeakL,
            inputPeakR = inPeakR,
            inputRmsL = inRmsL,
            inputRmsR = inRmsR,
            outputPeakL = outPeakL,
            outputPeakR = outPeakR,
            outputRmsL = outRmsL,
            outputRmsR = outRmsR,
            maxGainReductionDb = gainFactorToDb(minGainFactor),
            currentGainReductionDb = gainFactorToDb(lastGainFactor),
            limiterActive = limiterEnabled && bufferLimitedFrames > 0L,
            limitedFrameCount = totalLimitedFrames,
            samplesProcessed = input.size.toLong(),
            requestedLinearGain = g,
            effectiveLinearGain = if (bypass) 1.0 else g * minGainFactor
        )
    }

    /**
     * Advances the shared stereo envelope and returns one gain multiplier for both
     * channels. When the limiter is enabled, the envelope attenuation and the
     * instantaneous limiter-ceiling guard are folded into that multiplier. When the
     * limiter is disabled, only a stateless full-scale guard remains: over-range
     * frames receive one common attenuation factor, preserving the L/R ratio.
     */
    private fun linkedLimiterGain(
        linkedPeak: Double,
        limiterEnabled: Boolean,
        ceiling: Double,
        alphaAttack: Double,
        alphaRelease: Double
    ): Double {
        if (!limiterEnabled) {
            return if (linkedPeak > FULL_SCALE_CEILING) {
                FULL_SCALE_CEILING / linkedPeak
            } else {
                1.0
            }
        }

        if (linkedPeak > envelope) {
            envelope += alphaAttack * (linkedPeak - envelope)
        } else {
            envelope += alphaRelease * (linkedPeak - envelope)
        }

        var gain = 1.0
        if (envelope > ceiling) {
            gain = ceiling / envelope
        }
        if (linkedPeak > ceiling) {
            gain = min(gain, ceiling / linkedPeak)
        }
        return gain
    }

    private fun finiteOrZero(value: Double): Double {
        return if (value.isNaN() || value.isInfinite()) 0.0 else value
    }

    private fun gainFactorToDb(factor: Double): Double {
        if (factor >= 1.0) return 0.0
        return 20.0 * log10(factor.coerceAtLeast(1e-12))
    }

    private fun normalizeShort(sample: Short): Double {
        return if (sample < 0) sample / 32768.0 else sample / 32767.0
    }

    /**
     * In-place processing for 16-bit short PCM.
     */
    fun processInPlace(samples: ShortArray, channels: Int = USE_CONFIG_CHANNELS): DspStatistics {
        return process(samples, samples, channels)
    }

    /**
     * In-place processing for 32-bit float PCM.
     */
    fun processInPlace(samples: FloatArray, channels: Int = USE_CONFIG_CHANNELS): DspStatistics {
        return process(samples, samples, channels)
    }

    /**
     * Safely quantizes a double [-1.0, 1.0] to a 16-bit signed Short [-32768, 32767]
     * with asymmetric scale factor and saturation clamping. Non-finite values are
     * converted to 0 rather than propagated.
     */
    private fun quantize16(v: Double): Short {
        val clamped = finiteOrZero(v).coerceIn(-1.0, 1.0)
        val scaled = if (clamped < 0.0) {
            clamped * 32768.0
        } else {
            clamped * 32767.0
        }
        val rounded = scaled.roundToInt()
        return rounded.coerceIn(-32768, 32767).toShort()
    }
}

private const val FULL_SCALE_CEILING = 1.0

// Sentinel default: resolve the channel count from the same config snapshot
// that supplies the gain, limiter, and sample-rate settings for this call.
private const val USE_CONFIG_CHANNELS = -1
