package com.noheadphonegamerz.amplitudebooster.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noheadphonegamerz.amplitudebooster.R
import com.noheadphonegamerz.amplitudebooster.audio.BoosterState
import com.noheadphonegamerz.amplitudebooster.audio.BoosterStatus
import com.noheadphonegamerz.amplitudebooster.ui.theme.*
import kotlin.math.abs

@Composable
fun MainScreen(
    state: BoosterState,
    onGainChanged: (Double) -> Unit,
    onToggleBoost: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val isBoostActive = state.status == BoosterStatus.ACTIVE
    val gainDb = state.gainDb

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header (Brand + App Title + Settings Icon)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.header_brand),
                    style = MaterialTheme.typography.labelSmall,
                    color = NhgAccentCyan,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.header_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = NhgTextPrimary
                )
            }
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(NhgDarkSurfaceVariant)
                    .border(1.dp, NhgBorder, RoundedCornerShape(8.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.header_settings_cd),
                    tint = NhgTextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // 2. Master Boost Switch & Live Status Card
        val statusBorderColor = when (state.status) {
            BoosterStatus.ACTIVE -> NhgAccentGreen
            BoosterStatus.STARTING -> NhgAccentCyan
            BoosterStatus.ERROR -> NhgDangerRed
            BoosterStatus.OFF -> NhgBorder
        }

        val statusBadgeColor = when (state.status) {
            BoosterStatus.ACTIVE -> NhgAccentGreen
            BoosterStatus.STARTING -> NhgAccentCyan
            BoosterStatus.ERROR -> NhgDangerRed
            BoosterStatus.OFF -> NhgTextSecondary
        }

        val statusBadgeText = when (state.status) {
            BoosterStatus.ACTIVE -> stringResource(R.string.status_active)
            BoosterStatus.STARTING -> stringResource(R.string.status_starting)
            BoosterStatus.ERROR -> stringResource(R.string.status_error)
            BoosterStatus.OFF -> stringResource(R.string.status_off)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(NhgDarkSurface)
                .border(1.dp, statusBorderColor, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Switch Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.boost_master_switch),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = NhgTextPrimary
                        )
                        Text(
                            text = stringResource(R.string.boost_master_switch_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = NhgTextSecondary
                        )
                    }
                    Switch(
                        checked = isBoostActive || state.status == BoosterStatus.STARTING,
                        onCheckedChange = onToggleBoost,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NhgAccentGreen,
                            checkedTrackColor = NhgAccentGreen.copy(alpha = 0.35f),
                            uncheckedThumbColor = NhgTextSecondary,
                            uncheckedTrackColor = NhgDarkSurfaceVariant
                        )
                    )
                }

                HorizontalDivider(color = NhgBorder, thickness = 1.dp)

                // Status Strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(statusBadgeColor.copy(alpha = 0.15f))
                            .border(1.dp, statusBadgeColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = statusBadgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusBadgeColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = state.statusDetail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = NhgTextPrimary,
                        modifier = Modifier.weight(1f),
                        lineHeight = 18.sp
                    )
                }

                if (state.lastError != null) {
                    Text(
                        text = "Failure details: ${state.lastError}",
                        style = MaterialTheme.typography.labelSmall,
                        color = NhgDangerRed,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // 3. Amplitude Gain Control Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(NhgDarkSurface)
                .border(1.dp, NhgBorder, RoundedCornerShape(12.dp))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = stringResource(R.string.gain_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = NhgTextSecondary,
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.Bold
                )

                // Large Numerical Readout
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "+%.1f".format(gainDb),
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = if (isBoostActive && gainDb > 0.0) NhgAccentGreen else NhgTextPrimary
                    )
                    Text(
                        text = " dB",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = NhgTextSecondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                Text(
                    text = "%.2f× Linear Amplitude".format(state.linearMultiplier),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = NhgAccentCyan,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Gain Slider [0.0 dB to 15.0 dB]
                Slider(
                    value = gainDb.toFloat(),
                    onValueChange = { onGainChanged(it.toDouble()) },
                    valueRange = 0.0f..15.0f,
                    steps = 29, // 0.5 dB increments from 0.0 to 15.0
                    colors = SliderDefaults.colors(
                        thumbColor = if (isBoostActive) NhgAccentGreen else NhgTextSecondary,
                        activeTrackColor = if (isBoostActive) NhgAccentGreen else NhgTextSecondary,
                        inactiveTrackColor = NhgDarkSurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Baseline scale labels
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("0 dB (Bypass)", style = MaterialTheme.typography.labelSmall, color = NhgTextSecondary)
                    Text("+6 dB (2×)", style = MaterialTheme.typography.labelSmall, color = NhgTextSecondary)
                    Text("+12 dB (4×)", style = MaterialTheme.typography.labelSmall, color = NhgTextSecondary)
                    Text("+15 dB (Max)", style = MaterialTheme.typography.labelSmall, color = NhgTextSecondary)
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Calibrated Preset Buttons (0 dB, +6 dB, +12 dB)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PresetButton(
                        label = stringResource(R.string.preset_0db),
                        sublabel = stringResource(R.string.preset_0db_sub),
                        isSelected = abs(gainDb - 0.0) < 0.1,
                        onClick = { onGainChanged(0.0) },
                        modifier = Modifier.weight(1f)
                    )
                    PresetButton(
                        label = stringResource(R.string.preset_6db),
                        sublabel = stringResource(R.string.preset_6db_sub),
                        isSelected = abs(gainDb - 6.0) < 0.1,
                        onClick = { onGainChanged(6.0) },
                        modifier = Modifier.weight(1f)
                    )
                    PresetButton(
                        label = stringResource(R.string.preset_12db),
                        sublabel = stringResource(R.string.preset_12db_sub),
                        isSelected = abs(gainDb - 12.0) < 0.1,
                        onClick = { onGainChanged(12.0) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 4. Safety & Clipping Warning (Visible when gain > 6 dB)
        AnimatedVisibility(
            visible = gainDb > 6.0,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(NhgWarningAmber.copy(alpha = 0.08f))
                    .border(1.dp, NhgWarningAmber.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = NhgWarningAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "HIGH GAIN CAUTION",
                            style = MaterialTheme.typography.labelSmall,
                            color = NhgWarningAmber,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = stringResource(R.string.warning_high_gain),
                        style = MaterialTheme.typography.bodyMedium,
                        color = NhgTextPrimary,
                        lineHeight = 18.sp
                    )
                    TextButton(
                        onClick = { onGainChanged(0.0) },
                        colors = ButtonDefaults.textButtonColors(contentColor = NhgWarningAmber),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text(stringResource(R.string.action_reset_zero), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 5. Output Mix Scope & Architecture Explanation
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(NhgDarkSurfaceVariant)
                .border(1.dp, NhgBorder, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = NhgAccentCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "GLOBAL OUTPUT MIX ARCHITECTURE",
                        style = MaterialTheme.typography.labelSmall,
                        color = NhgAccentCyan,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = stringResource(R.string.status_note_global),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NhgTextSecondary,
                    lineHeight = 19.sp
                )
                Text(
                    text = "• Target: Audio Session 0 (Global Output Mix)\n" +
                            "• Permission: MODIFY_AUDIO_SETTINGS (Normal, install-time)\n" +
                            "• Fail-safe: Effect released immediately when disabled or stopped\n" +
                            "• Telephony calls use separate circuit-switched paths and are not boosted",
                    style = MaterialTheme.typography.labelSmall,
                    color = NhgTextSecondary,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun PresetButton(
    label: String,
    sublabel: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isSelected) NhgAccentGreen.copy(alpha = 0.15f) else NhgDarkSurfaceVariant
    val borderColor = if (isSelected) NhgAccentGreen else NhgBorder
    val textColor = if (isSelected) NhgAccentGreen else NhgTextPrimary

    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = textColor,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = sublabel,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) NhgAccentGreen else NhgTextSecondary,
                fontSize = 10.sp
            )
        }
    }
}
