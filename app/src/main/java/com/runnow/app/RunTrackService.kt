package com.runnow.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/** Service latar depan selama merekam lari, supaya GPS di WebView tidak dimatikan sistem saat layar mati. */
class RunTrackService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Rekam lari", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notif = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_runnow)
            .setContentTitle("Run Now sedang merekam lari")
            .setContentText("Ketuk untuk kembali ke layar lari")
            .setOngoing(true)
            .setContentIntent(open)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(ID, notif)
            }
        } catch (_: Exception) {
            // Izin lokasi belum diberikan: halaman memanggil lagi begitu GPS dapat sinyal pertama.
            stopSelf()
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "run_track"
        private const val ID = 4107

        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, RunTrackService::class.java))
            } catch (_: Exception) {
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RunTrackService::class.java))
        }
    }
}
