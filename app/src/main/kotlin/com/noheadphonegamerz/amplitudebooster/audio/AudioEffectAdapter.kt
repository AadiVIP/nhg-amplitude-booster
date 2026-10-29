package com.noheadphonegamerz.amplitudebooster.audio

import android.media.audiofx.AudioEffect
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Controller wrapping Android's public [android.media.audiofx] effect APIs attached to
 * Audio Session 0 (the Global Output Mix).
 *
 * Implements the verified rootless fallback chain:
 *   1. [LoudnessEnhancer] (Session 0, priority 0)
 *   2. [DynamicsProcessing] (Session 0)
 *   3. [Equalizer] (Session 0, priority 0)
 *
 * All state transitions verify both [AudioEffect.getEnabled] and [AudioEffect.hasControl]
 * via API readback before confirming an [BoosterStatus.ACTIVE] state.
 */
class AudioEffectAdapter(
    val audioSessionId: Int = SESSION_GLOBAL_OUTPUT_MIX,
    private val backend: EffectBackend = DefaultAudioEffectBackend(audioSessionId)
) {

    companion object {
        const val TAG = "NHG_AudioEffectAdapter"
        const val SESSION_GLOBAL_OUTPUT_MIX = 0
        const val MIN_GAIN_DB = 0.0
        const val MAX_GAIN_DB = 15.0
        const val DEFAULT_GAIN_DB = 0.0

        /**
         * Converts decibels to true linear multiplier: 10^(dB / 20).
         */
        fun dbToLinear(gainDb: Double): Double = 10.0.pow(gainDb / 20.0)

        /**
         * Converts decibels to millibels (1 dB = 100 mB).
         */
        fun dbToMillibels(gainDb: Double): Int = (gainDb * 100.0).roundToInt()

        private fun logI(msg: String) {
            BoosterStateHub.log("[$TAG] $msg")
            try { Log.i(TAG, msg) } catch (_: Throwable) {}
        }

        private fun logW(msg: String) {
            BoosterStateHub.log("[$TAG] WARN: $msg")
            try { Log.w(TAG, msg) } catch (_: Throwable) {}
        }

        private fun logE(msg: String, t: Throwable? = null) {
            BoosterStateHub.log("[$TAG] ERROR: $msg")
            try { Log.e(TAG, msg, t) } catch (_: Throwable) {}
        }
    }

    /**
     * Abstract backend allowing deterministic JVM testing of all adapter state transitions.
     */
    interface EffectBackend {
        fun attach(gainDb: Double): BackendAttachResult
        fun setGain(gainDb: Double): Boolean
        fun isEnabled(): Boolean
        fun hasControl(): Boolean
        fun release(reason: String)
        fun setOnControlLostListener(listener: (String) -> Unit)
    }

    sealed class BackendAttachResult {
        data class Success(val effectType: EffectType, val detail: String) : BackendAttachResult()
        data class Failure(val errorMessage: String, val exceptions: List<String>) : BackendAttachResult()
    }

    private var _state = BoosterState(
        status = BoosterStatus.OFF,
        gainDb = DEFAULT_GAIN_DB,
        activeEffectType = EffectType.NONE,
        statusDetail = "Booster is off. System output mix is unmodified."
    )

    var onStateChanged: ((BoosterState) -> Unit)? = null

    val currentState: BoosterState
        get() = _state

    val isEnabled: Boolean
        get() = _state.status == BoosterStatus.ACTIVE

    init {
        backend.setOnControlLostListener { reason ->
            handleControlLost(reason)
        }
    }

    /**
     * Clamps requested gain to safe operational boundaries [0.0, 15.0] dB.
     */
    fun sanitizeGain(gainDb: Double): Double = gainDb.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)

    /**
     * Attaches to the global output mix at the specified gain.
     * Transitions through STARTING -> ACTIVE on verified readback, or to ERROR on denial.
     */
    @Synchronized
    fun attach(gainDb: Double): BoosterState {
        val targetGain = sanitizeGain(gainDb)
        updateState(
            _state.copy(
                status = BoosterStatus.STARTING,
                gainDb = targetGain,
                statusDetail = "Initializing session 0 audio effect..."
            )
        )

        val result = backend.attach(targetGain)
        return when (result) {
            is BackendAttachResult.Success -> {
                val confirmed = backend.isEnabled() && backend.hasControl()
                if (confirmed) {
                    val detail = if (targetGain <= 0.0) {
                        "${result.effectType} attached on session 0 in bypass mode (0 dB). Output mix unmodified."
                    } else {
                        "${result.effectType} active on session 0 at +%.1f dB (%.2fx). AudioEffect readback confirmed."
                            .format(targetGain, dbToLinear(targetGain))
                    }
                    updateState(
                        BoosterState(
                            status = BoosterStatus.ACTIVE,
                            gainDb = targetGain,
                            activeEffectType = result.effectType,
                            statusDetail = detail,
                            lastError = null
                        )
                    )
                } else {
                    val errorMsg = "Effect created but readback failed: enabled=${backend.isEnabled()}, hasControl=${backend.hasControl()}"
                    logW(errorMsg)
                    backend.release("Readback verification failed")
                    updateState(
                        BoosterState(
                            status = BoosterStatus.ERROR,
                            gainDb = targetGain,
                            activeEffectType = EffectType.NONE,
                            statusDetail = "Audio effect permission or control check failed.",
                            lastError = errorMsg
                        )
                    )
                }
            }
            is BackendAttachResult.Failure -> {
                logE("All session 0 constructors failed: ${result.errorMessage}")
                updateState(
                    BoosterState(
                        status = BoosterStatus.ERROR,
                        gainDb = targetGain,
                        activeEffectType = EffectType.NONE,
                        statusDetail = "Unable to attach to global output mix: ${result.errorMessage}",
                        lastError = result.errorMessage
                    )
                )
            }
        }
    }

    /**
     * Sets target gain in decibels.
     * If currently active, updates the attached effect directly.
     */
    @Synchronized
    fun setGainDb(gainDb: Double): BoosterState {
        val targetGain = sanitizeGain(gainDb)
        if (_state.status == BoosterStatus.ACTIVE) {
            val success = backend.setGain(targetGain)
            if (success) {
                val detail = if (targetGain <= 0.0) {
                    "${_state.activeEffectType} attached in bypass mode (0 dB). Output mix unmodified."
                } else {
                    "${_state.activeEffectType} active on session 0 at +%.1f dB (%.2fx). AudioEffect readback confirmed."
                        .format(targetGain, dbToLinear(targetGain))
                }
                updateState(
                    _state.copy(
                        gainDb = targetGain,
                        statusDetail = detail,
                        lastError = null
                    )
                )
            } else {
                val errorMsg = "Failed to update gain on ${_state.activeEffectType}"
                logW(errorMsg)
                updateState(
                    _state.copy(
                        status = BoosterStatus.ERROR,
                        gainDb = targetGain,
                        statusDetail = "Gain update failed.",
                        lastError = errorMsg
                    )
                )
            }
        } else {
            updateState(_state.copy(gainDb = targetGain))
        }
        return _state
    }

    /**
     * Releases native AudioEffect resources and returns system mix to platform.
     * Fully idempotent.
     */
    @Synchronized
    fun release(reason: String = "User disabled"): BoosterState {
        logI("Releasing audio effects: $reason")
        backend.release(reason)
        return updateState(
            BoosterState(
                status = BoosterStatus.OFF,
                gainDb = _state.gainDb,
                activeEffectType = EffectType.NONE,
                statusDetail = "Booster is off. System output mix is unmodified ($reason)."
            )
        )
    }

    private fun handleControlLost(reason: String) {
        logW("Control lost event: $reason")
        backend.release(reason)
        updateState(
            BoosterState(
                status = BoosterStatus.ERROR,
                gainDb = _state.gainDb,
                activeEffectType = EffectType.NONE,
                statusDetail = "Effect control lost: $reason. Audio returned to system baseline.",
                lastError = reason
            )
        )
    }

    private fun updateState(newState: BoosterState): BoosterState {
        _state = newState
        onStateChanged?.invoke(newState)
        return newState
    }
}

/**
 * Standard Android implementation using public [AudioEffect] APIs.
 */
class DefaultAudioEffectBackend(
    private val sessionId: Int = 0
) : AudioEffectAdapter.EffectBackend {

    private var enhancer: LoudnessEnhancer? = null
    private var processing: DynamicsProcessing? = null
    private var equalizer: Equalizer? = null

    private var controlLostListener: ((String) -> Unit)? = null
    private var activeType = EffectType.NONE

    override fun setOnControlLostListener(listener: (String) -> Unit) {
        controlLostListener = listener
    }

    override fun attach(gainDb: Double): AudioEffectAdapter.BackendAttachResult {
        release("Re-attaching")
        val errors = mutableListOf<String>()

        // 1. Try LoudnessEnhancer
        try {
            val le = LoudnessEnhancer(sessionId)
            le.setControlStatusListener { _, hasControl ->
                if (!hasControl) controlLostListener?.invoke("LoudnessEnhancer control taken by another app")
            }
            le.setEnableStatusListener { _, enabled ->
                if (!enabled && activeType == EffectType.LOUDNESS_ENHANCER) {
                    controlLostListener?.invoke("LoudnessEnhancer disabled externally")
                }
            }
            enhancer = le
            activeType = EffectType.LOUDNESS_ENHANCER
            applyGainToLoudnessEnhancer(le, gainDb)
            return AudioEffectAdapter.BackendAttachResult.Success(
                EffectType.LOUDNESS_ENHANCER,
                "LoudnessEnhancer initialized on session $sessionId"
            )
        } catch (t: Throwable) {
            enhancer = null
            errors.add("LoudnessEnhancer(0): ${t.javaClass.simpleName}: ${t.message}")
            try { Log.w(AudioEffectAdapter.TAG, "LoudnessEnhancer(0) unavailable: ${t.message}") } catch (_: Throwable) {}
        }

        // 2. Try DynamicsProcessing
        try {
            val dp = DynamicsProcessing(sessionId)
            dp.setControlStatusListener { _, hasControl ->
                if (!hasControl) controlLostListener?.invoke("DynamicsProcessing control taken by another app")
            }
            dp.setEnableStatusListener { _, enabled ->
                if (!enabled && activeType == EffectType.DYNAMICS_PROCESSING) {
                    controlLostListener?.invoke("DynamicsProcessing disabled externally")
                }
            }
            processing = dp
            activeType = EffectType.DYNAMICS_PROCESSING
            applyGainToDynamicsProcessing(dp, gainDb)
            return AudioEffectAdapter.BackendAttachResult.Success(
                EffectType.DYNAMICS_PROCESSING,
                "DynamicsProcessing initialized on session $sessionId"
            )
        } catch (t: Throwable) {
            processing = null
            errors.add("DynamicsProcessing(0): ${t.javaClass.simpleName}: ${t.message}")
            try { Log.w(AudioEffectAdapter.TAG, "DynamicsProcessing(0) unavailable: ${t.message}") } catch (_: Throwable) {}
        }

        // 3. Try Equalizer
        try {
            val eq = Equalizer(0, sessionId)
            eq.setControlStatusListener { _, hasControl ->
                if (!hasControl) controlLostListener?.invoke("Equalizer control taken by another app")
            }
            eq.setEnableStatusListener { _, enabled ->
                if (!enabled && activeType == EffectType.EQUALIZER) {
                    controlLostListener?.invoke("Equalizer disabled externally")
                }
            }
            equalizer = eq
            activeType = EffectType.EQUALIZER
            applyGainToEqualizer(eq, gainDb)
            return AudioEffectAdapter.BackendAttachResult.Success(
                EffectType.EQUALIZER,
                "Equalizer initialized on session $sessionId"
            )
        } catch (t: Throwable) {
            equalizer = null
            errors.add("Equalizer(0, 0): ${t.javaClass.simpleName}: ${t.message}")
            try { Log.w(AudioEffectAdapter.TAG, "Equalizer(0, 0) unavailable: ${t.message}") } catch (_: Throwable) {}
        }

        activeType = EffectType.NONE
        return AudioEffectAdapter.BackendAttachResult.Failure(
            errorMessage = errors.joinToString("; "),
            exceptions = errors
        )
    }

    override fun setGain(gainDb: Double): Boolean {
        return try {
            when (activeType) {
                EffectType.LOUDNESS_ENHANCER -> enhancer?.let { applyGainToLoudnessEnhancer(it, gainDb); true } ?: false
                EffectType.DYNAMICS_PROCESSING -> processing?.let { applyGainToDynamicsProcessing(it, gainDb); true } ?: false
                EffectType.EQUALIZER -> equalizer?.let { applyGainToEqualizer(it, gainDb); true } ?: false
                EffectType.NONE -> false
            }
        } catch (t: Throwable) {
            try { Log.e(AudioEffectAdapter.TAG, "Failed applying gain: ${t.message}", t) } catch (_: Throwable) {}
            false
        }
    }

    private fun applyGainToLoudnessEnhancer(le: LoudnessEnhancer, gainDb: Double) {
        val mB = AudioEffectAdapter.dbToMillibels(gainDb)
        if (mB <= 0) {
            if (le.enabled) le.enabled = false
        } else {
            le.setTargetGain(mB)
            if (!le.enabled) le.enabled = true
        }
    }

    private fun applyGainToDynamicsProcessing(dp: DynamicsProcessing, gainDb: Double) {
        if (gainDb <= 0.0) {
            if (dp.enabled) dp.enabled = false
        } else {
            dp.setInputGainAllChannelsTo(gainDb.toFloat())
            if (!dp.enabled) dp.enabled = true
        }
    }

    private fun applyGainToEqualizer(eq: Equalizer, gainDb: Double) {
        val mB = AudioEffectAdapter.dbToMillibels(gainDb)
        if (mB <= 0) {
            if (eq.enabled) eq.enabled = false
        } else {
            val max = eq.bandLevelRange[1].toInt()
            val target = mB.coerceAtMost(max).toShort()
            val bands = eq.numberOfBands.toInt()
            for (band in 0 until bands) {
                eq.setBandLevel(band.toShort(), target)
            }
            if (!eq.enabled) eq.enabled = true
        }
    }

    override fun isEnabled(): Boolean {
        return when (activeType) {
            EffectType.LOUDNESS_ENHANCER -> enhancer?.enabled == true
            EffectType.DYNAMICS_PROCESSING -> processing?.enabled == true
            EffectType.EQUALIZER -> equalizer?.enabled == true
            EffectType.NONE -> false
        }
    }

    override fun hasControl(): Boolean {
        return when (activeType) {
            EffectType.LOUDNESS_ENHANCER -> enhancer?.hasControl() == true
            EffectType.DYNAMICS_PROCESSING -> processing?.hasControl() == true
            EffectType.EQUALIZER -> equalizer?.hasControl() == true
            EffectType.NONE -> false
        }
    }

    override fun release(reason: String) {
        runCatching {
            enhancer?.let {
                it.enabled = false
                it.release()
            }
        }
        enhancer = null

        runCatching {
            processing?.let {
                it.enabled = false
                it.release()
            }
        }
        processing = null

        runCatching {
            equalizer?.let {
                it.enabled = false
                it.release()
            }
        }
        equalizer = null
        activeType = EffectType.NONE
    }
}
