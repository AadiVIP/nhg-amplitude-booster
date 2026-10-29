package com.noheadphonegamerz.amplitudebooster.dsp

/**
 * Diagnostic and metering statistics collected during a single DSP processing buffer.
 *
 * Gain-reduction values are negative dB numbers (0.0 means no reduction).
 * [maxGainReductionDb] describes the smallest gain factor used anywhere in the buffer,
 * while [currentGainReductionDb] describes the factor applied to its final frame.
 */
data class DspStatistics(
    val inputPeakL: Double = 0.0,
    val inputPeakR: Double = 0.0,
    val inputRmsL: Double = 0.0,
    val inputRmsR: Double = 0.0,
    val outputPeakL: Double = 0.0,
    val outputPeakR: Double = 0.0,
    val outputRmsL: Double = 0.0,
    val outputRmsR: Double = 0.0,

    /**
     * Most negative linked gain reduction applied to any frame in this buffer, in dB.
     * Includes envelope attenuation, the instantaneous limiter-ceiling guard, and the
     * stateless full-scale safety guard used when the limiter is disabled.
     */
    val maxGainReductionDb: Double = 0.0,

    /**
     * Linked gain reduction applied to the final frame of this buffer, in dB.
     * This describes the final frame (including any safety-guard attenuation),
     * not the buffer minimum.
     */
    val currentGainReductionDb: Double = 0.0,

    /**
     * True only when the limiter is enabled and at least one frame in this buffer
     * received linked limiter attenuation. Stays false when the limiter is disabled,
     * even if the full-scale safety guard attenuated an over-range frame.
     */
    val limiterActive: Boolean = false,

    /**
     * Cumulative number of stereo frames (since construction or [DspProcessor.reset])
     * in which a linked gain factor below 1.0 was applied. This includes frames
     * attenuated by the stateless full-scale safety guard when the limiter is disabled.
     * It counts frames receiving gain reduction, not clipped samples or clipping events.
     */
    val limitedFrameCount: Long = 0L,

    /**
     * Number of interleaved samples in this buffer.
     */
    val samplesProcessed: Long = 0L,

    /**
     * Requested scalar gain g = 10^(gainDb / 20), before limiting and bypass.
     */
    val requestedLinearGain: Double = 1.0,

    /**
     * Smallest effective linear gain used in this buffer
     * (requested gain multiplied by the minimum linked attenuation factor,
     * including the full-scale safety guard when the limiter is disabled).
     * Equals 1.0 when the buffer is bypassed.
     */
    val effectiveLinearGain: Double = 1.0
) {
    /**
     * Converts a linear peak amplitude to dBFS.
     * Returns -120.0 dBFS for silence.
     */
    companion object {
        fun linearToDb(linear: Double): Double {
            if (linear <= 1e-6) return -120.0
            return 20.0 * kotlin.math.log10(linear)
        }
    }
}
