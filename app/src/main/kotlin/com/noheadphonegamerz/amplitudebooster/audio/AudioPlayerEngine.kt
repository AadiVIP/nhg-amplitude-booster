package com.noheadphonegamerz.amplitudebooster.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.noheadphonegamerz.amplitudebooster.dsp.DspConfig
import com.noheadphonegamerz.amplitudebooster.dsp.DspProcessor
import com.noheadphonegamerz.amplitudebooster.dsp.DspStatistics
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * High-fidelity in-app audio synthesis and playback engine.
 * Employs [DspProcessor] in the realtime audio render loop to demonstrate and verify
 * uniform scalar gain and clipping protection with live AudioTrack output.
 */
class AudioPlayerEngine(
    val sampleRate: Int = 48000,
    private val statsListener: ((DspStatistics) -> Unit)? = null
) {
    enum class ToneType {
        SINE_100HZ,
        SINE_440HZ,
        SINE_1KHZ,
        SINE_10KHZ,
        FREQUENCY_SWEEP,
        MULTITONE,
        PINK_NOISE
    }

    val processor = DspProcessor(
        DspConfig(
            gainDb = 0.0,
            bypass = false,
            limiterEnabled = true,
            limiterThresholdDb = -0.1,
            attackTimeMs = 1.0,
            releaseTimeMs = 60.0,
            sampleRate = sampleRate,
            channelCount = 2
        )
    )

    private var audioTrack: AudioTrack? = null
    private var playbackThread: Thread? = null
    private val isPlaying = AtomicBoolean(false)

    @Volatile
    var activeTone: ToneType = ToneType.SINE_1KHZ

    @Volatile
    var baseAmplitude: Double = 0.35 // -9 dBFS nominal unamplified signal

    fun isAudioPlaying(): Boolean = isPlaying.get()

    /**
     * Applies a read-modify-write update against the current configuration so that
     * changing one setting never resets another. Serialized to prevent lost updates
     * when setters run concurrently.
     */
    @Synchronized
    private fun updateConfig(transform: (DspConfig) -> DspConfig) {
        processor.config = transform(processor.config)
    }

    /**
     * Updates requested gain in dB.
     */
    fun setGainDb(gainDb: Double) {
        val clamped = gainDb.coerceIn(0.0, 12.0)
        updateConfig { it.copy(gainDb = clamped) }
    }

    /**
     * Toggles limiter protection.
     */
    fun setLimiterEnabled(enabled: Boolean) {
        updateConfig { it.copy(limiterEnabled = enabled) }
    }

    /**
     * Toggles bypass.
     */
    fun setBypass(bypass: Boolean) {
        updateConfig { it.copy(bypass = bypass) }
    }

    /**
     * Starts continuous synthesized audio playback through the DSP engine.
     */
    @Synchronized
    fun start() {
        if (isPlaying.get()) return

        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBufferSize * 2, 4096)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()
        isPlaying.set(true)

        playbackThread = Thread({
            renderAudioLoop(bufferSize / 2) // short count
        }, "NHG-AudioRenderThread").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    /**
     * Stops audio playback and releases the AudioTrack.
     */
    @Synchronized
    fun stop() {
        isPlaying.set(false)
        try {
            playbackThread?.join(500)
        } catch (_: InterruptedException) {}
        playbackThread = null

        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Throwable) {}
        audioTrack = null
        processor.reset()
    }

    /**
     * Real-time audio generation and processing loop.
     */
    private fun renderAudioLoop(bufferSizeShorts: Int) {
        val inputBuffer = ShortArray(bufferSizeShorts)
        val outputBuffer = ShortArray(bufferSizeShorts)
        var phase = 0.0
        var sweepFreq = 100.0
        var sweepDir = 1.0

        // Pink noise filter state
        var b0 = 0.0
        var b1 = 0.0
        var b2 = 0.0

        val framesPerBuffer = bufferSizeShorts / 2

        while (isPlaying.get()) {
            val tone = activeTone
            val amp = baseAmplitude

            for (frame in 0 until framesPerBuffer) {
                val sampleVal: Double = when (tone) {
                    ToneType.SINE_100HZ -> {
                        phase += 2.0 * PI * 100.0 / sampleRate
                        sin(phase) * amp
                    }
                    ToneType.SINE_440HZ -> {
                        phase += 2.0 * PI * 440.0 / sampleRate
                        sin(phase) * amp
                    }
                    ToneType.SINE_1KHZ -> {
                        phase += 2.0 * PI * 1000.0 / sampleRate
                        sin(phase) * amp
                    }
                    ToneType.SINE_10KHZ -> {
                        phase += 2.0 * PI * 10000.0 / sampleRate
                        sin(phase) * amp
                    }
                    ToneType.FREQUENCY_SWEEP -> {
                        sweepFreq += sweepDir * (10000.0 / (sampleRate * 2.0))
                        if (sweepFreq > 12000.0) {
                            sweepFreq = 12000.0
                            sweepDir = -1.0
                        } else if (sweepFreq < 100.0) {
                            sweepFreq = 100.0
                            sweepDir = 1.0
                        }
                        phase += 2.0 * PI * sweepFreq / sampleRate
                        sin(phase) * amp
                    }
                    ToneType.MULTITONE -> {
                        phase += 2.0 * PI * 1000.0 / sampleRate
                        val s1 = sin(phase) * 0.4
                        val s2 = sin(phase * 0.25) * 0.3
                        val s3 = sin(phase * 4.0) * 0.3
                        (s1 + s2 + s3) * amp
                    }
                    ToneType.PINK_NOISE -> {
                        val white = Random.nextDouble(-1.0, 1.0)
                        b0 = 0.99886 * b0 + white * 0.0555179
                        b1 = 0.99332 * b1 + white * 0.0750759
                        b2 = 0.96900 * b2 + white * 0.1538520
                        val pink = (b0 + b1 + b2 + white * 0.5362) * 0.15
                        pink * amp
                    }
                }

                // Subtracting an exact multiple of 2π is phase-equivalent for sin(),
                // so wrapping preserves waveform continuity for every tone.
                if (phase > 2.0 * PI * 10000.0) {
                    phase -= 2.0 * PI * 10000.0
                }

                val pcmSample = (sampleVal * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
                inputBuffer[frame * 2] = pcmSample
                inputBuffer[frame * 2 + 1] = pcmSample // Stereo L = R
            }

            // Real-time DSP processing pass
            val stats = processor.process(inputBuffer, outputBuffer, channels = 2)

            // Write to Android AudioTrack
            val track = audioTrack
            if (track != null && isPlaying.get()) {
                track.write(outputBuffer, 0, outputBuffer.size)
            }

            // Deliver telemetry to UI
            statsListener?.invoke(stats)
        }
    }
}
