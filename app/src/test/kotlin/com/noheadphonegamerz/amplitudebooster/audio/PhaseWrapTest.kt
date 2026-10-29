package com.noheadphonegamerz.amplitudebooster.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Focused evidence for the audit claim that the render loop's phase wrap causes
 * periodic clicks. The wrap subtracts 2π × 10000, an exact integer multiple of
 * 2π, which is phase-equivalent for sine waves. These tests compare the wrapped
 * waveform with an unwrapped reference across the wrap boundary.
 */
class PhaseWrapTest {

    private val sampleRate = 48000
    private val twoPi = 2.0 * PI
    private val wrapSpan = twoPi * 10000.0
    private val increment = twoPi * 1000.0 / sampleRate

    @Test
    fun phaseWrapDoesNotIntroduceWaveformDiscontinuity() {
        val frames = sampleRate * 11 // 1 kHz phase crosses the wrap once in 10 s
        var wrapped = 0.0
        var unwrapped = 0.0
        var wrapCount = 0
        var maxDifference = 0.0
        var maxStep = 0.0
        var previous = 0.0

        repeat(frames) {
            wrapped += increment
            unwrapped += increment
            if (wrapped > wrapSpan) {
                wrapped -= wrapSpan
                wrapCount++
            }

            val wrappedSample = sin(wrapped)
            val unwrappedSample = sin(unwrapped)
            maxDifference = maxOf(maxDifference, abs(wrappedSample - unwrappedSample))
            maxStep = maxOf(maxStep, abs(wrappedSample - previous))
            previous = wrappedSample
        }

        val maxNormalStep = 2.0 * sin(increment / 2.0) + 1e-9
        assertEquals("1 kHz phase should wrap exactly once in 11 s", 1, wrapCount)
        assertTrue("max step $maxStep exceeds sine slope bound $maxNormalStep", maxStep <= maxNormalStep)
        assertTrue("wrapped sine differs from unwrapped by $maxDifference", maxDifference < 1e-6)
    }

    @Test
    fun phaseWrapIsEquivalentForScaledMultitonePhases() {
        val frames = sampleRate * 11
        var wrapped = 0.0
        var unwrapped = 0.0
        var wrapCount = 0
        var maxDifference = 0.0
        var maxQuantizedDifference = 0

        repeat(frames) {
            wrapped += increment
            unwrapped += increment
            if (wrapped > wrapSpan) {
                wrapped -= wrapSpan
                wrapCount++
            }

            val wrappedSum = multitone(wrapped)
            val unwrappedSum = multitone(unwrapped)
            maxDifference = maxOf(maxDifference, abs(wrappedSum - unwrappedSum))
            maxQuantizedDifference = maxOf(
                maxQuantizedDifference,
                abs(quantize(wrappedSum) - quantize(unwrappedSum))
            )
        }

        assertEquals(1, wrapCount)
        assertTrue("wrapped multitone differs by $maxDifference", maxDifference < 1e-6)
        assertTrue("quantized samples diverged by $maxQuantizedDifference", maxQuantizedDifference <= 1)
    }

    private fun multitone(phase: Double): Double {
        val s1 = sin(phase) * 0.4
        val s2 = sin(phase * 0.25) * 0.3
        val s3 = sin(phase * 4.0) * 0.3
        return s1 + s2 + s3
    }

    private fun quantize(value: Double): Int {
        return (value * 32767.0).toInt().coerceIn(-32768, 32767)
    }
}
