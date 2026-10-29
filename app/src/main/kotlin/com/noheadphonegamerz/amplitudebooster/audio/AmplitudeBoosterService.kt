package com.noheadphonegamerz.amplitudebooster.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.noheadphonegamerz.amplitudebooster.MainActivity
import com.noheadphonegamerz.amplitudebooster.R
import kotlin.math.pow

/**
 * Foreground service hosting the global output-mix [AudioEffectAdapter] and optional
 * in-app test [AudioPlayerEngine] with clean foreground service lifecycle.
 */
class AmplitudeBoosterService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): AmplitudeBoosterService = this@AmplitudeBoosterService
    }

    private val binder = LocalBinder()

    var effectAdapter: AudioEffectAdapter? = null
        private set

    var playerEngine: AudioPlayerEngine? = null
        private set

    companion object {
        const val CHANNEL_ID = "nhg_amplitude_booster_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.noheadphonegamerz.amplitudebooster.ACTION_START"
        const val ACTION_STOP = "com.noheadphonegamerz.amplitudebooster.ACTION_STOP"
        const val ACTION_SET_GAIN = "com.noheadphonegamerz.amplitudebooster.ACTION_SET_GAIN"
        const val EXTRA_GAIN_DB = "extra_gain_db"

        const val PREFS_NAME = "nhg_amplitude_booster_prefs"
        const val PREF_KEY_GAIN = "saved_gain_db"
        const val PREF_KEY_ENABLED = "saved_boost_enabled"

        @Volatile
        var isServiceRunning = false
            private set

        fun sendStart(context: Context, gainDb: Double) {
            val intent = Intent(context, AmplitudeBoosterService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_GAIN_DB, gainDb)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun sendSetGain(context: Context, gainDb: Double) {
            val intent = Intent(context, AmplitudeBoosterService::class.java).apply {
                action = ACTION_SET_GAIN
                putExtra(EXTRA_GAIN_DB, gainDb)
            }
            context.startService(intent)
        }

        fun sendStop(context: Context) {
            val intent = Intent(context, AmplitudeBoosterService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val adapter = AudioEffectAdapter(AudioEffectAdapter.SESSION_GLOBAL_OUTPUT_MIX)
        adapter.onStateChanged = { newState ->
            BoosterStateHub.updateState(newState)
            updateNotification(newState.gainDb, newState.status == BoosterStatus.ACTIVE)
        }
        effectAdapter = adapter

        playerEngine = AudioPlayerEngine()
        BoosterStateHub.log("AmplitudeBoosterService created.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        when (intent?.action) {
            ACTION_STOP -> {
                stopBoosting()
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_SET_GAIN -> {
                val gain = intent.getDoubleExtra(
                    EXTRA_GAIN_DB,
                    prefs.getFloat(PREF_KEY_GAIN, 0.0f).toDouble()
                )
                setGain(gain)
            }

            ACTION_START -> {
                val gain = intent.getDoubleExtra(
                    EXTRA_GAIN_DB,
                    prefs.getFloat(PREF_KEY_GAIN, 0.0f).toDouble()
                )
                startBoosting(gain)
            }

            else -> {
                // If service is recreated by the system (START_STICKY)
                val wasEnabled = prefs.getBoolean(PREF_KEY_ENABLED, false)
                val savedGain = prefs.getFloat(PREF_KEY_GAIN, 0.0f).toDouble()
                if (wasEnabled) {
                    startBoosting(savedGain)
                } else {
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }
        return START_STICKY
    }

    fun startBoosting(gainDb: Double) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat(PREF_KEY_GAIN, gainDb.toFloat())
            .putBoolean(PREF_KEY_ENABLED, true)
            .apply()

        isServiceRunning = true

        val notification = buildNotification(gainDb, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val state = effectAdapter?.attach(gainDb)
        if (state != null) {
            BoosterStateHub.updateState(state)
        }
        updateNotification(gainDb, state?.status == BoosterStatus.ACTIVE)
    }

    fun setGain(gainDb: Double) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putFloat(PREF_KEY_GAIN, gainDb.toFloat()).apply()

        val state = effectAdapter?.setGainDb(gainDb)
        if (state != null) {
            BoosterStateHub.updateState(state)
        }
        playerEngine?.setGainDb(gainDb)
        updateNotification(gainDb, isServiceRunning)
    }

    fun stopBoosting() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(PREF_KEY_ENABLED, false).apply()

        effectAdapter?.release("Service stopped")
        playerEngine?.stop()

        isServiceRunning = false
        BoosterStateHub.updateStatus(
            BoosterStatus.OFF,
            "Booster is off. System output mix is unmodified."
        )
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        stopBoosting()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(gainDb: Double, isActive: Boolean): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingLaunch = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AmplitudeBoosterService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val linear = 10.0.pow(gainDb / 20.0)
        val contentText = if (isActive && gainDb > 0.0) {
            "Master Gain: +%.1f dB (%.2fx) • Active (Session 0)".format(gainDb, linear)
        } else if (isActive) {
            "Master Gain: 0.0 dB (Bypass) • Active (Session 0)"
        } else {
            "Master Gain: Off • Standby"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NHG Amplitude Booster")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingLaunch)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Boost", pendingStop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(gainDb: Double, isActive: Boolean) {
        if (!isServiceRunning) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(gainDb, isActive))
    }
}
