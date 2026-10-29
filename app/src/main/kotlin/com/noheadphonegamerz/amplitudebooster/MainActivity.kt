package com.noheadphonegamerz.amplitudebooster

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.noheadphonegamerz.amplitudebooster.audio.AmplitudeBoosterService
import com.noheadphonegamerz.amplitudebooster.audio.AudioBackendManager
import com.noheadphonegamerz.amplitudebooster.audio.AudioPlayerEngine
import com.noheadphonegamerz.amplitudebooster.audio.BoosterStateHub
import com.noheadphonegamerz.amplitudebooster.audio.BoosterStatus
import com.noheadphonegamerz.amplitudebooster.dsp.DspStatistics
import com.noheadphonegamerz.amplitudebooster.ui.MainScreen
import com.noheadphonegamerz.amplitudebooster.ui.SettingsSheet
import com.noheadphonegamerz.amplitudebooster.ui.theme.NHGTheme
import com.noheadphonegamerz.amplitudebooster.ui.theme.NhgDarkBackground

class MainActivity : ComponentActivity() {

    private lateinit var testPlayerEngine: AudioPlayerEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(AmplitudeBoosterService.PREFS_NAME, Context.MODE_PRIVATE)
        val savedGain = prefs.getFloat(AmplitudeBoosterService.PREF_KEY_GAIN, 0.0f).toDouble()
        val savedEnabled = prefs.getBoolean(AmplitudeBoosterService.PREF_KEY_ENABLED, false)

        // Initialize state hub with persisted preferences if not already running
        if (!AmplitudeBoosterService.isServiceRunning) {
            BoosterStateHub.updateGain(savedGain)
            if (savedEnabled) {
                // If previously enabled before process death, user can re-enable with one tap
                BoosterStateHub.updateStatus(
                    BoosterStatus.OFF,
                    "Booster is ready. Tap Master Audio Boost to activate."
                )
            }
        }

        var liveStats by mutableStateOf(DspStatistics())
        testPlayerEngine = AudioPlayerEngine(sampleRate = 48000) { newStats ->
            liveStats = newStats
        }

        setContent {
            NHGTheme {
                val boosterState by BoosterStateHub.state.collectAsState()
                var showSettings by remember { mutableStateOf(false) }
                var capability by remember { mutableStateOf(AudioBackendManager.auditCapabilities(this)) }
                var isTestAudioPlaying by remember { mutableStateOf(false) }
                var activeTone by remember { mutableStateOf(AudioPlayerEngine.ToneType.SINE_1KHZ) }

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(NhgDarkBackground),
                    containerColor = NhgDarkBackground
                ) { innerPadding ->
                    MainScreen(
                        state = boosterState,
                        onGainChanged = { newGain ->
                            BoosterStateHub.updateGain(newGain)
                            prefs.edit().putFloat(AmplitudeBoosterService.PREF_KEY_GAIN, newGain.toFloat()).apply()
                            if (boosterState.status == BoosterStatus.ACTIVE || AmplitudeBoosterService.isServiceRunning) {
                                AmplitudeBoosterService.sendSetGain(this@MainActivity, newGain)
                            }
                            if (isTestAudioPlaying) {
                                testPlayerEngine.setGainDb(newGain)
                            }
                        },
                        onToggleBoost = { enable ->
                            if (enable) {
                                val gainToApply = boosterState.gainDb
                                AmplitudeBoosterService.sendStart(this@MainActivity, gainToApply)
                            } else {
                                AmplitudeBoosterService.sendStop(this@MainActivity)
                            }
                        },
                        onOpenSettings = { showSettings = true },
                        modifier = Modifier.padding(innerPadding)
                    )

                    if (showSettings) {
                        SettingsSheet(
                            capability = capability,
                            playerEngine = testPlayerEngine,
                            isAudioPlaying = isTestAudioPlaying,
                            liveStats = liveStats,
                            onTogglePlayAudio = {
                                if (isTestAudioPlaying) {
                                    testPlayerEngine.stop()
                                    isTestAudioPlaying = false
                                } else {
                                    testPlayerEngine.activeTone = activeTone
                                    testPlayerEngine.setGainDb(boosterState.gainDb)
                                    testPlayerEngine.setBypass(false)
                                    testPlayerEngine.start()
                                    isTestAudioPlaying = true
                                }
                            },
                            onSelectTone = { selectedTone ->
                                activeTone = selectedTone
                                testPlayerEngine.activeTone = selectedTone
                            },
                            activeTone = activeTone,
                            onDismiss = { showSettings = false },
                            onRefreshCapability = {
                                capability = AudioBackendManager.auditCapabilities(this@MainActivity)
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-sync UI state if service was stopped from notification
        if (!AmplitudeBoosterService.isServiceRunning && BoosterStateHub.currentState().status == BoosterStatus.ACTIVE) {
            BoosterStateHub.updateStatus(
                BoosterStatus.OFF,
                "Booster is off. System output mix is unmodified."
            )
        }
    }

    override fun onDestroy() {
        if (::testPlayerEngine.isInitialized) {
            testPlayerEngine.stop()
        }
        super.onDestroy()
    }
}
