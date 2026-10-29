package com.noheadphonegamerz.amplitudebooster.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * True-Peak (Inter-Sample Peak) Analysis and Investigation Module.
 *
 * Implements 4x oversampling interpolation using a Lanczos-windowed Sinc reconstruction
 * kernel compliant with ITU-R BS.1770-4 principles to measure continuous analog peak levels
 * that occur between digital samples.
 */
object TruePeakInvestigation {

    private const val KERNEL_RADIUS = 4

    /**
     * Normalized sinc function: sinc(x) = sin(pi * x) / (pi * x)
     */
    private fun sinc(x: Double): Double {
        if (abs(x) < 1e-9) return 1.0
        val pix = PI * x
        return sin(pix) / pix
    }

    /**
     * Lanczos-windowed sinc interpolation kernel.
     */
    private fun lanczosKernel(x: Double, a: Int = KERNEL_RADIUS): Double {
        val ax = abs(x)
        if (ax >= a) return 0.0
        return sinc(x) * sinc(x / a)
    }

    /**
     * Estimates the maximum True-Peak amplitude (linear) of an audio buffer by 4x interpolation.
     *
     * @param samples Normalized audio samples [-1.0, 1.0].
     * @return Estimated maximum True Peak linear value.
     */
    fun estimateTruePeakLinear(samples: FloatArray): Double {
        if (samples.isEmpty()) return 0.0

        var maxPeak = 0.0
        val n = samples.size

        // Measure raw sample peaks
        for (i in 0 until n) {
            val s = abs(samples[i].toDouble())
            if (s > maxPeak) maxPeak = s
        }

        // 4x oversampling: evaluate intermediate points at offsets 0.25, 0.50, 0.75 between adjacent samples
        val subOffsets = doubleArrayOf(0.25, 0.50, 0.75)

        for (i in KERNEL_RADIUS until n - KERNEL_RADIUS) {
            for (offset in subOffsets) {
                var interpolated = 0.0
                for (k in -KERNEL_RADIUS until KERNEL_RADIUS) {
                    val sampleIdx = i + k
                    val weight = lanczosKernel(k - offset)
                    interpolated += samples[sampleIdx] * weight
                }
                val absInterp = abs(interpolated)
                if (absInterp > maxPeak) {
                    maxPeak = absInterp
                }
            }
        }

        return maxPeak
    }

    /**
     * Converts a linear amplitude to dBFS True Peak (dBFSTP).
     */
    fun linearToDbFsTp(linearPeak: Double): Double {
        if (linearPeak <= 1e-6) return -120.0
        return 20.0 * kotlin.math.log10(linearPeak)
    }
}
