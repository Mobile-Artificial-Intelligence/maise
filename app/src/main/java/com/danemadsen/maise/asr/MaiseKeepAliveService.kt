package com.danemadsen.maise.asr

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.danemadsen.maise.R

private const val KEEPALIVE_CHANNEL = "maise_keepalive"
private const val KEEPALIVE_NOTIF_ID = 1002

/**
 * Minimal foreground service whose only purpose is to maintain
 * FOREGROUND_SERVICE_TYPE_SPECIAL_USE.
 *
 * SPECIAL_USE has no "eligible state" restriction and no runtime cap, so startForeground()
 * always succeeds — including on START_STICKY restarts with no visible activity. (The
 * original DATA_SYNC type gained a ~6-hour cumulative daily limit in Android 16 / API 36.)
 * This running service satisfies the "another non-shortService FGS is already active in this
 * app" eligible state condition, which allows [MaiseAsrService] to call
 * startForeground(MICROPHONE) when a recognition session begins.
 */
class MaiseKeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(KEEPALIVE_CHANNEL, "Voice Recognition", NotificationManager.IMPORTANCE_MIN)
                .apply { setSound(null, null) }
        )
        val notification = NotificationCompat.Builder(this, KEEPALIVE_CHANNEL)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Voice recognition ready")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(KEEPALIVE_NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(KEEPALIVE_NOTIF_ID, notification)
        }
        return START_STICKY
    }
}
