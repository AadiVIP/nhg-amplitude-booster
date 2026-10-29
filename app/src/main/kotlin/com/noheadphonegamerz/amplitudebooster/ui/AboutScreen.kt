package com.noheadphonegamerz.amplitudebooster.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noheadphonegamerz.amplitudebooster.ui.theme.NhgAccentCyan
import com.noheadphonegamerz.amplitudebooster.ui.theme.NhgAccentGreen
import com.noheadphonegamerz.amplitudebooster.ui.theme.NhgDangerRed
import com.noheadphonegamerz.amplitudebooster.ui.theme.NhgWarningAmber

@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Info Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "NO HEADPHONE GAMERZ (NHG)",
                    style = MaterialTheme.typography.labelSmall,
                    color = NhgAccentCyan,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "NHG Amplitude Booster",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Version 1.0.0 (API 28–36 Compatible)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Designed for gamers and power users who need clear, uniform audio amplitude boosting without acoustic coloration, pitch alteration, or artificial frequency distortion.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Scope: the gain controls apply only to this app's built-in test tone player. No global audio effect is attached, so other applications are not processed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NhgAccentCyan
                )
            }
        }

        // Processing Principle Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "MANDATORY AUDIO PRINCIPLE",
                    style = MaterialTheme.typography.labelSmall,
                    color = NhgAccentGreen,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "y[n] = g × x[n]",
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = NhgAccentGreen
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Where g = 10^(gainDb / 20). Every frequency component receives the exact identical scalar multiplier. There is NO graphic or parametric EQ, NO bass boost, NO treble shelf, NO stereo widening, and NO artificial harmonics.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Hearing Safety Warning Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = NhgDangerRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "HEARING & HARDWARE SAFETY",
                        style = MaterialTheme.typography.labelSmall,
                        color = NhgDangerRed,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "The integrated peak limiter prevents digital clipping and numerical overflow. However, it does NOT guarantee hearing protection or prevent non-linear distortion caused by exceeding physical speaker or amplifier excursion limits. Please listen responsibly at safe sound pressure levels.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Important Limitations Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "KNOWN PLATFORM LIMITATIONS",
                    style = MaterialTheme.typography.labelSmall,
                    color = NhgWarningAmber,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "1. Digital Gain ≠ Acoustic Loudness: Equal digital amplitude scaling does not produce identical acoustic sound pressure due to non-flat frequency response curves of device speakers and human ear perception (Fletcher-Munson curves).\n\n" +
                            "2. Android Sandbox Isolation: Starting in Android 9/10 (API 28+), AudioFlinger global session 0 is restricted from third-party applications without signature permissions. Unprivileged apps cannot modify audio from other applications system-wide without custom firmware.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
