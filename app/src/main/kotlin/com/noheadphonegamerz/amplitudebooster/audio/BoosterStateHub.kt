package com.noheadphonegamerz.amplitudebooster.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Thread-safe single source of truth for booster state and real-time diagnostic telemetry.
 * Shared across Activity and Service components.
 */
object BoosterStateHub {

    private const val MAX_LOG_LINES = 150
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    private val _state = MutableStateFlow(
        BoosterState(
            status = BoosterStatus.OFF,
            gainDb = AudioEffectAdapter.DEFAULT_GAIN_DB,
            activeEffectType = EffectType.NONE,
            statusDetail = "Booster is off. System output mix is unmodified."
        )
    )
    val state: StateFlow<BoosterState> = _state.asStateFlow()

    private val logLines = ArrayDeque<String>()

    fun currentState(): BoosterState = _state.value

    fun updateState(newState: BoosterState) {
        _state.value = newState
        log("State: ${newState.status} | Gain: +%.1f dB | ${newState.activeEffectType}".format(newState.gainDb))
    }

    fun updateGain(gainDb: Double) {
        _state.update { it.copy(gainDb = gainDb) }
    }

    fun updateStatus(status: BoosterStatus, detail: String, error: String? = null) {
        _state.update {
            it.copy(
                status = status,
                statusDetail = detail,
                lastError = error
            )
        }
        log("Status -> $status: $detail" + (if (error != null) " (Error: $error)" else ""))
    }

    fun log(message: String) {
        val entry = "${timeFormat.format(Date())}  $message"
        synchronized(logLines) {
            logLines.addLast(entry)
            while (logLines.size > MAX_LOG_LINES) {
                logLines.removeFirst()
            }
        }
    }

    fun getLogs(): List<String> = synchronized(logLines) { logLines.toList() }

    fun clearLogs() = synchronized(logLines) { logLines.clear() }
}
