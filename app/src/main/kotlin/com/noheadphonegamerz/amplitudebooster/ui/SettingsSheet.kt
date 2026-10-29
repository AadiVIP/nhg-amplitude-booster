package com.noheadphonegamerz.amplitudebooster.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noheadphonegamerz.amplitudebooster.R
import com.noheadphonegamerz.amplitudebooster.audio.AudioBackendManager
import com.noheadphonegamerz.amplitudebooster.audio.AudioPlayerEngine
import com.noheadphonegamerz.amplitudebooster.audio.BackendCapability
import com.noheadphonegamerz.amplitudebooster.dsp.DspStatistics
import com.noheadphonegamerz.amplitudebooster.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    capability: BackendCapability,
    playerEngine: AudioPlayerEngine,
    isAudioPlaying: Boolean,
    liveStats: DspStatistics,
    onTogglePlayAudio: () -> Unit,
    onSelectTone: (AudioPlayerEngine.ToneType) -> Unit,
    activeTone: AudioPlayerEngine.ToneType,
    onDismiss: () -> Unit,
    onRefreshCapability: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    fun copyToClipboard(text: String, label: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "Copied $label to clipboard", Toast.LENGTH_SHORT).show()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = NhgDarkSurface,
        contentColor = NhgTextPrimary,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(NhgBorder)
            )
        }
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = NhgTextPrimary
                    )
                    Text(
                        text = "Technical verification and signal testing",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NhgTextSecondary
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.settings_close),
                        tint = NhgTextSecondary
                    )
                }
            }

            HorizontalDivider(color = NhgBorder, thickness = 1.dp)

            // Section 1: Architecture Explanation
            SettingsSectionHeader(title = stringResource(R.string.settings_architecture_title))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(NhgDarkSurfaceVariant)
                    .border(1.dp, NhgBorder, RoundedCornerShape(8.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_architecture_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = NhgTextPrimary,
                        lineHeight = 20.sp
                    )
                    Text(
                        text = "Public APIs used: android.media.audiofx.LoudnessEnhancer, DynamicsProcessing, Equalizer, AudioManager. No decompiled code, no private APIs, no accessibility privileges.",
                        style = MaterialTheme.typography.labelSmall,
                        color = NhgAccentCyan,
                        lineHeight = 16.sp
                    )
                }
            }

            // Section 2: Device Capability Audit
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingsSectionHeader(title = stringResource(R.string.settings_device_title))
                IconButton(
                    onClick = onRefreshCapability,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh device audit",
                        tint = NhgAccentCyan
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(NhgDarkSurfaceVariant)
                    .border(1.dp, NhgBorder, RoundedCornerShape(8.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SettingsInfoRow("Platform", "Android ${capability.androidRelease} (API ${capability.apiLevel})")
                    SettingsInfoRow("Hardware", "${capability.deviceManufacturer} ${capability.deviceModel}")
                    SettingsInfoRow("Session 0 Scope", capability.activeScope)
                    SettingsInfoRow("Session 0 Probe", if (capability.session0Permitted) "Permitted via MODIFY_AUDIO_SETTINGS" else "Restricted by HAL")
                    SettingsInfoRow("System AudioEffects", "${capability.audioEffectsAvailable.size} registered")

                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = {
                            val report = capability.toDiagnosticText()
                            copyToClipboard(report, "Diagnostic Report")
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NhgDarkSurface,
                            contentColor = NhgAccentCyan
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .border(1.dp, NhgBorder, RoundedCornerShape(8.dp))
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Copy Full System Report", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Section 3: In-App Test Signal Player (DSP Verification)
            SettingsSectionHeader(title = stringResource(R.string.settings_test_player_title))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(NhgDarkSurfaceVariant)
                    .border(1.dp, NhgBorder, RoundedCornerShape(8.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.settings_test_player_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = NhgTextSecondary
                    )

                    // Play / Stop Test Tone
                    Button(
                        onClick = onTogglePlayAudio,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAudioPlaying) NhgDangerRed else NhgAccentGreen,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(
                            imageVector = if (isAudioPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isAudioPlaying) "Stop Test Audio" else "Play Test Audio",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Signal Selector
                    val toneList = listOf(
                        AudioPlayerEngine.ToneType.SINE_1KHZ to "1 kHz Sine (Standard)",
                        AudioPlayerEngine.ToneType.SINE_440HZ to "440 Hz Sine (Concert A)",
                        AudioPlayerEngine.ToneType.SINE_100HZ to "100 Hz Sine (Bass)",
                        AudioPlayerEngine.ToneType.FREQUENCY_SWEEP to "Frequency Sweep",
                        AudioPlayerEngine.ToneType.PINK_NOISE to "Pink Noise"
                    )

                    toneList.forEach { (type, label) ->
                        val selected = activeTone == type
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (selected) NhgDarkSurface else Color.Transparent)
                                .clickable { onSelectTone(type) }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (selected) NhgAccentCyan else NhgTextPrimary,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                            RadioButton(
                                selected = selected,
                                onClick = { onSelectTone(type) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = NhgAccentCyan,
                                    unselectedColor = NhgTextSecondary
                                )
                            )
                        }
                    }

                    if (isAudioPlaying) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Peak L/R: %.2f / %.2f • Limiter: ${if (liveStats.limiterActive) "ACTIVE" else "IDLE"}"
                                .format(liveStats.outputPeakL, liveStats.outputPeakR),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (liveStats.limiterActive) NhgWarningAmber else NhgAccentGreen
                        )
                    }
                }
            }

            // Section 4: About & Attribution
            SettingsSectionHeader(title = stringResource(R.string.settings_about_title))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(NhgDarkSurfaceVariant)
                    .border(1.dp, NhgBorder, RoundedCornerShape(8.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "NHG Amplitude Booster — Version 1.0.0",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = NhgTextPrimary
                    )
                    Text(
                        text = "Developed by No Headphone Gamerz (NHG)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NhgAccentCyan
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Principles: Truthful reporting, zero EQ acoustic coloration, public Android audio APIs, fail-safe architecture.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NhgTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = NhgTextSecondary,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp
    )
}

@Composable
private fun SettingsInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = NhgTextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = NhgTextPrimary,
            fontFamily = FontFamily.Monospace
        )
    }
}
