package com.noheadphonegamerz.amplitudebooster.audio

import android.content.Context
import android.media.audiofx.AudioEffect
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.os.IBinder
import java.lang.reflect.Method

/**
 * Probes, inspects, and audits Android audio backends, hardware effects, and platform capabilities.
 */
object AudioBackendManager {

    /**
     * Inspects the host device and evaluates the audio capability matrix.
     */
    fun auditCapabilities(context: Context? = null): BackendCapability {
        val release = Build.VERSION.RELEASE ?: "Unknown"
        val api = Build.VERSION.SDK_INT
        val manufacturer = Build.MANUFACTURER ?: "Unknown"
        val model = Build.MODEL ?: "Unknown"

        // Enumerate system audio effects
        val effectNames = mutableListOf<String>()
        try {
            val descriptors = AudioEffect.queryEffects()
            if (descriptors != null) {
                for (d in descriptors) {
                    effectNames.add("${d.name} (${d.implementor}) [type=${d.type}, uuid=${d.uuid}]")
                }
            }
        } catch (t: Throwable) {
            effectNames.add("Query failed: ${t.message}")
        }

        // Probe Session 0 Global Effect
        var session0Permitted = false
        var session0Error: String? = null
        var testEnhancer: LoudnessEnhancer? = null

        try {
            // LoudnessEnhancer(0) targets the global output mix (session 0).
            // AOSP 15 AudioFlinger gates this with MODIFY_AUDIO_SETTINGS.
            testEnhancer = LoudnessEnhancer(0)
            val hasControl = testEnhancer.hasControl()
            testEnhancer.enabled = false
            session0Permitted = true
            session0Error = "Verified: Session 0 constructor succeeded (hasControl=$hasControl)"
        } catch (se: SecurityException) {
            session0Permitted = false
            session0Error = "SecurityException: MODIFY_AUDIO_SETTINGS required (${se.message})"
        } catch (uoe: UnsupportedOperationException) {
            session0Permitted = false
            session0Error = "UnsupportedOperationException: Global session 0 is unsupported on this OEM HAL (${uoe.message})"
        } catch (t: Throwable) {
            session0Permitted = false
            session0Error = "${t.javaClass.simpleName}: ${t.message}"
        } finally {
            try {
                testEnhancer?.release()
            } catch (_: Throwable) {}
        }

        // Probe Shizuku IPC (informational only)
        val shizukuStatus = probeShizuku()

        val activeScope = "Global Output Mix (Session 0) + In-App DSP"
        val primaryBackend = if (session0Permitted) BackendType.GLOBAL_OUTPUT_MIX else BackendType.IN_APP_DSP

        return BackendCapability(
            androidRelease = release,
            apiLevel = api,
            deviceManufacturer = manufacturer,
            deviceModel = model,
            session0Permitted = session0Permitted,
            session0Error = session0Error,
            audioEffectsAvailable = effectNames,
            shizukuStatus = shizukuStatus,
            activeScope = activeScope,
            primaryBackend = primaryBackend
        )
    }

    /**
     * Checks if Shizuku service is running via Android ServiceManager reflection.
     */
    private fun probeShizuku(): String {
        return try {
            val serviceManagerClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod: Method = serviceManagerClass.getMethod("getService", String::class.java)
            val binder = getServiceMethod.invoke(null, "moe.shizuku.server.IShizukuService") as? IBinder
            if (binder != null && binder.isBinderAlive) {
                "Running (UID 2000 shell)"
            } else {
                "Service not detected (Not installed or stopped)"
            }
        } catch (_: Throwable) {
            "Service not detected (Not installed or stopped)"
        }
    }
}
