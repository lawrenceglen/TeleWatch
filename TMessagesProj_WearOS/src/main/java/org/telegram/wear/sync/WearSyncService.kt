package org.telegram.wear.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import org.telegram.messenger.ConnectionsManager
import org.telegram.messenger.UserConfig

/**
 * Keeps the MTProto connection alive in the background so the watch receives
 * messages promptly over LTE without needing to be woken by FCM every time.
 * Runs as a foreground service to avoid being killed by the OS.
 *
 * Battery note: ConnectionsManager already handles its own keepalive intervals
 * and backs off when the radio is idle — we don't add any extra polling here.
 */
class WearSyncService : Service() {

    companion object {
        const val CHANNEL_ID = "telegram_sync"
        const val NOTIFICATION_ID = 9001
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())

        // Ensure the core connection is active for the selected account
        ConnectionsManager.getInstance(UserConfig.selectedAccount).checkConnection()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Sync", NotificationManager.IMPORTANCE_MIN)
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Telegram")
            .setContentText("Connected")
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setSilent(true)
            .build()
    }
}
