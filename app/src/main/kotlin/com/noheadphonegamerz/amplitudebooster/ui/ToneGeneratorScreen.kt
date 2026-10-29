package com.noheadphonegamerz.amplitudebooster.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noheadphonegamerz.amplitudebooster.audio.AudioPlayerEngine
import com.noheadphonegamerz.amplitudebooster.dsp.DspStatistics
import com.noheadphonegamerz.amplitudebooster.ui.theme.NhgAccentCyan
import com.noheadphonegamerz.amplitudebooster.ui.theme.NhgAccentGreen
import com.noheadphonegamerz.amplitudebooster.ui.theme.NhgDangerRed
import com.noheadphonegamerz.amplitudebooster.ui.theme.NhgWarningAmber

@Composable
fun ToneGeneratorScreen(
    isPlaying: Boolean,
    activeTone: AudioPlayerEngine.ToneType,
    statistics: DspStatistics,
    onTogglePlay: () -> Unit,
    onSelectTone: (AudioPlayerEngine.ToneType) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Card: Playback Controls
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "REAL-TIME DSP TEST PLAYER",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.5.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onTogglePlay,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPlaying) NhgDangerRed else NhgAccentGreen,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isPlaying) "STOP AUDIO" else "PLAY TEST AUDIO",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isPlaying) "Audio actively streaming via AudioTrack through NHG DSP Engine" else "Press Play to test uniform gain and clipping protection on device hardware",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Card: Select Test Waveform
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "SELECT TEST SIGNAL",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                val tones = listOf(
                    AudioPlayerEngine.ToneType.SINE_100HZ to "100 Hz Sine (Bass Region)",
                    AudioPlayerEngine.ToneType.SINE_440HZ to "440 Hz Sine (Concert Pitch A4)",
                    AudioPlayerEngine.ToneType.SINE_1KHZ to "1 kHz Sine (Standard Telecom Ref)",
                    AudioPlayerEngine.ToneType.SINE_10KHZ to "10 kHz Sine (Treble Region)",
                    AudioPlayerEngine.ToneType.FREQUENCY_SWEEP to "Frequency Sweep (100 Hz – 12 kHz)",
                    AudioPlayerEngine.ToneType.MULTITONE to "Multitone (Simultaneous Multi-Frequency)",
                    AudioPlayerEngine.ToneType.PINK_NOISE to "Pink Noise (Broadband Noise)"
                )

                tones.forEach { (type, label) ->
                    val isSelected = activeTone == type
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) NhgAccentCyan.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { onSelectTone(type) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) NhgAccentCyan else MaterialTheme.colorScheme.onSurface
                        )
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelectTone(type) },
                            colors = RadioButtonDefaults.colors(selectedColor = NhgAccentCyan)
                        )
                    }
                }
            }
        }

        // Live DSP Telemetry Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "LIVE DSP ENGINE METRICS",
                    style = MaterialTheme.typography.labelSmall,
                    color = NhgAccentCyan,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                TelemetryRow("Input Peak (L / R)", "%.3f / %.3f".format(statistics.inputPeakL, statistics.inputPeakR))
                TelemetryRow("Input RMS (L / R)", "%.3f / %.3f".format(statistics.inputRmsL, statistics.inputRmsR))
                TelemetryRow("Output Peak (L / R)", "%.3f / %.3f".format(statistics.outputPeakL, statistics.outputPeakR))
                TelemetryRow("Output RMS (L / R)", "%.3f / %.3f".format(statistics.outputRmsL, statistics.outputRmsR))
                TelemetryRow("Requested Gain", "%.2fx (+%.1f dB)".format(statistics.requestedLinearGain, 20.0 * kotlin.math.log10(statistics.requestedLinearGain.coerceAtLeast(1e-4))))
                TelemetryRow("Effective Gain (min)", "%.2fx".format(statistics.effectiveLinearGain))
                TelemetryRow("Limiter Attn (current)", "%.2f dB".format(statistics.currentGainReductionDb))
                TelemetryRow("Limiter Attn (buffer min)", "%.2f dB".format(statistics.maxGainReductionDb))
                TelemetryRow("Limited Frames (cum.)", "${statistics.limitedFrameCount}")
            }
        }
    }
}

@Composable
fun TelemetryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}
