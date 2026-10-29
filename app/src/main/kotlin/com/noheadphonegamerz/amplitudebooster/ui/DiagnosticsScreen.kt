package com.noheadphonegamerz.amplitudebooster.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noheadphonegamerz.amplitudebooster.audio.BackendCapability
import com.noheadphonegamerz.amplitudebooster.dsp.DspStatistics
import com.noheadphonegamerz.amplitudebooster.ui.theme.NhgAccentCyan
import com.noheadphonegamerz.amplitudebooster.ui.theme.NhgAccentGreen

@Composable
fun DiagnosticsScreen(
    capability: BackendCapability,
    statistics: DspStatistics,
    sampleRate: Int,
    lastError: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    fun exportReport() {
        val report = capability.toDiagnosticText() + buildString {
            appendLine("\n--- Live DSP Engine Telemetry ---")
            appendLine("Sample Rate: $sampleRate Hz (PCM 16-bit / Float32)")
            appendLine("Requested Gain: %.2fx".format(statistics.requestedLinearGain))
            appendLine("Effective Gain (buffer minimum): %.2fx".format(statistics.effectiveLinearGain))
            appendLine("Limiter Attenuation (current frame): %.2f dB".format(statistics.currentGainReductionDb))
            appendLine("Limiter Attenuation (buffer minimum): %.2f dB".format(statistics.maxGainReductionDb))
            appendLine("Limited Frames (cumulative): ${statistics.limitedFrameCount}")
            appendLine("Last Backend Error: ${lastError ?: "None"}")
            appendLine("===============================================")
        }

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("NHG Diagnostic Report", report))
        Toast.makeText(context, "Scrubbed diagnostic report copied to clipboard!", Toast.LENGTH_LONG).show()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Export Action Button
        Button(
            onClick = { exportReport() },
            colors = ButtonDefaults.buttonColors(
                containerColor = NhgAccentCyan,
                contentColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(imageVector = Icons.Default.Share, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("EXPORT DIAGNOSTIC REPORT", fontWeight = FontWeight.Bold)
        }

        // Hardware & Platform Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "DEVICE ARCHITECTURE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                TelemetryRow("Android Version", capability.androidRelease)
                TelemetryRow("API Level", "${capability.apiLevel}")
                TelemetryRow("Manufacturer", capability.deviceManufacturer)
                TelemetryRow("Model", capability.deviceModel)
                TelemetryRow("Audio Backend", "${capability.primaryBackend}")
                TelemetryRow("Processing Scope", capability.activeScope)
                TelemetryRow("PCM Format", "16-bit Signed & 32-bit Float")
                TelemetryRow("Sample Rate", "$sampleRate Hz")
                TelemetryRow("Last Backend Error", lastError ?: "None")
            }
        }

        // DSP Telemetry Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "DSP EXECUTION TELEMETRY",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                TelemetryRow("Requested Gain", "%.2fx".format(statistics.requestedLinearGain))
                TelemetryRow("Effective Gain (min)", "%.2fx".format(statistics.effectiveLinearGain))
                TelemetryRow("Input Peak L / R", "%.3f / %.3f".format(statistics.inputPeakL, statistics.inputPeakR))
                TelemetryRow("Output Peak L / R", "%.3f / %.3f".format(statistics.outputPeakL, statistics.outputPeakR))
                TelemetryRow("Limiter Active", "${statistics.limiterActive}")
                TelemetryRow("Attenuation (current)", "%.2f dB".format(statistics.currentGainReductionDb))
                TelemetryRow("Attenuation (buffer min)", "%.2f dB".format(statistics.maxGainReductionDb))
                TelemetryRow("Limited Frames (cum.)", "${statistics.limitedFrameCount}")
            }
        }

        // System Audio Effects List Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "SYSTEM AUDIOEFFECT DESCRIPTORS (${capability.audioEffectsAvailable.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = NhgAccentGreen,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (capability.audioEffectsAvailable.isEmpty()) {
                    Text(
                        text = "No system audio effects returned by AudioEffect.queryEffects().",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    capability.audioEffectsAvailable.forEach { effect ->
                        Text(
                            text = "• $effect",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
