package com.noheadphonegamerz.amplitudebooster.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noheadphonegamerz.amplitudebooster.audio.BackendCapability
import com.noheadphonegamerz.amplitudebooster.ui.theme.NhgAccentCyan
import com.noheadphonegamerz.amplitudebooster.ui.theme.NhgAccentGreen
import com.noheadphonegamerz.amplitudebooster.ui.theme.NhgWarningAmber

@Composable
fun SetupScreen(
    capability: BackendCapability,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    fun copyToClipboard(text: String, label: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "Copied $label to clipboard", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Backend Detection Summary
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DETECTED ENVIRONMENT",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp
                    )
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = NhgAccentCyan
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                TelemetryRow("Android Version", "${capability.androidRelease} (API ${capability.apiLevel})")
                TelemetryRow("Device Model", "${capability.deviceManufacturer} ${capability.deviceModel}")
                TelemetryRow("Processing Scope", capability.activeScope)
                TelemetryRow(
                    "Session 0 Probe",
                    if (capability.session0Permitted) "Permitted, not attached" else "Restricted (AOSP Policy)"
                )
                TelemetryRow("Global Effect Attached", "No")
                TelemetryRow("Shizuku Status", capability.shizukuStatus)
            }
        }

        // System Permissions Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "REQUIRED PERMISSIONS & PRIVACY",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                PermissionExplainer(
                    title = "FOREGROUND_SERVICE_MEDIA_PLAYBACK",
                    desc = "Permits keeping the audio generator playing smoothly in the background without Android battery termination. Required on Android 14+ (API 34+)."
                )

                Spacer(modifier = Modifier.height(10.dp))

                PermissionExplainer(
                    title = "POST_NOTIFICATIONS",
                    desc = "Displays an ongoing notification with current amplification level and quick Stop button while audio is active. Required on Android 13+ (API 33+)."
                )

                Spacer(modifier = Modifier.height(10.dp))

                PermissionExplainer(
                    title = "ZERO NETWORK / ZERO RECORDING",
                    desc = "NHG Amplitude Booster declares NO internet permission and NO microphone audio recording permission. All digital sample arithmetic occurs 100% locally on device."
                )
            }
        }

        // Shizuku / ADB Integration Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "ADB & SHIZUKU ARCHITECTURE ANALYSIS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Technical Honesty Note:",
                    style = MaterialTheme.typography.titleLarge,
                    color = NhgWarningAmber,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Shizuku executes shell commands as UID 2000 ('shell'). However, in the AOSP audio architecture, the permission 'android.permission.MODIFY_AUDIO_ROUTING' is strictly defined as 'signature|privileged', meaning UID 2000 does NOT hold authorization to attach to master AudioFlinger Session 0 without custom ROM firmware or Magisk/KernelSU root.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Copyable Setup Commands:",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(6.dp))

                val adbCmd = "adb shell dumpsys media.audio_flinger"
                CodeSnippetBox(code = adbCmd, onCopy = { copyToClipboard(adbCmd, "ADB Command") })

                Spacer(modifier = Modifier.height(8.dp))

                val grantNotifCmd = "adb shell pm grant com.noheadphonegamerz.amplitudebooster android.permission.POST_NOTIFICATIONS"
                CodeSnippetBox(code = grantNotifCmd, onCopy = { copyToClipboard(grantNotifCmd, "Notification Grant Command") })
            }
        }
    }
}

@Composable
fun PermissionExplainer(title: String, desc: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = NhgAccentCyan,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = desc,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun CodeSnippetBox(code: String, onCopy: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = code,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy",
                tint = NhgAccentGreen,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
