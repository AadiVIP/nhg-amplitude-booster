package com.noheadphonegamerz.amplitudebooster.dsp

import kotlin.math.pow

/**
 * Configuration parameters for the NHG DSP Engine.
 *
 * All frequency components receive the identical scalar gain:
 * y[n] = g * x[n]
 *
 * Where g = 10^(gainDb / 20).
 *
 * @property gainDb Requested uniform gain in decibels [0.0, 12.0] dB.
 * @property bypass When true, audio samples pass through unaltered.
 * @property limiterEnabled When true, the peak limiter and its configured threshold actively prevent digital clipping.
 * When false, the envelope limiter is bypassed; a stateless common full-scale guard still attenuates over-range
 * frames so non-bypassed output stays within [-1.0, 1.0] while the left/right ratio is preserved.
 * @property limiterThresholdDb Peak ceiling threshold in dBFS (default -0.1 dBFS to prevent DAC inter-sample clipping).
 * @property attackTimeMs Limiter envelope attack time in milliseconds (default 1.0 ms).
 * @property releaseTimeMs Limiter envelope release time in milliseconds (default 60.0 ms).
 * @property sampleRate Audio sampling rate in Hertz (default 48000 Hz).
 * @property channelCount Number of audio channels (1 = mono, 2 = stereo).
 */
data class DspConfig(
    val gainDb: Double = 0.0,
    val bypass: Boolean = false,
    val limiterEnabled: Boolean = true,
    val limiterThresholdDb: Double = -0.1,
    val attackTimeMs: Double = 1.0,
    val releaseTimeMs: Double = 60.0,
    val sampleRate: Int = 48000,
    val channelCount: Int = 2
) {
    init {
        require(gainDb >= 0.0) { "Gain dB must be non-negative: $gainDb" }
        require(attackTimeMs > 0.0) { "Attack time must be positive: $attackTimeMs" }
        require(releaseTimeMs > 0.0) { "Release time must be positive: $releaseTimeMs" }
        require(sampleRate > 0) { "Sample rate must be positive: $sampleRate" }
        require(channelCount in 1..2) { "Channel count must be 1 or 2: $channelCount" }
    }

    /**
     * Converts the requested decibel gain to an exact linear scalar multiplier.
     * g = 10^(gainDb / 20)
     */
    fun linearGain(): Double = 10.0.pow(gainDb / 20.0)

    /**
     * Converts the peak limiter threshold from dBFS to linear amplitude ceiling.
     * threshold = 10^(limiterThresholdDb / 20)
     */
    fun limiterThresholdLinear(): Double = 10.0.pow(limiterThresholdDb / 20.0)
}
