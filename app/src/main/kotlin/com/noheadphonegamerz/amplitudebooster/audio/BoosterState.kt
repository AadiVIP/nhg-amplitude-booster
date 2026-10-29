package com.noheadphonegamerz.amplitudebooster.audio

import kotlin.math.pow

/**
 * Status of the global audio amplitude booster.
 */
enum class BoosterStatus {
    /** No effect is intended to be active; effect is released or disabled. */
    OFF,

    /** Initialization or attachment to session 0 is currently in progress. */
    STARTING,

    /** Effect exists on session 0 and readback confirms both enabled and hasControl. */
    ACTIVE,

    /** Effect creation, control, or enabling failed; audio is unboosted. */
    ERROR
}

/**
 * Audio effect implementation class currently attached.
 */
enum class EffectType {
    LOUDNESS_ENHANCER,
    DYNAMICS_PROCESSING,
    EQUALIZER,
    NONE
}

/**
 * Immutable state snapshot of the booster service and effect status.
 */
data class BoosterState(
    val status: BoosterStatus = BoosterStatus.OFF,
    val gainDb: Double = 0.0,
    val activeEffectType: EffectType = EffectType.NONE,
    val statusDetail: String = "Booster is off. System audio is playing at baseline volume.",
    val lastError: String? = null
) {
    /** True linear multiplier: 10^(dB / 20). */
    val linearMultiplier: Double
        get() = 10.0.pow(gainDb / 20.0)

    val isActive: Boolean
        get() = status == BoosterStatus.ACTIVE
}
