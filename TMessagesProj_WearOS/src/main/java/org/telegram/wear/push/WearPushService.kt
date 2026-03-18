package org.telegram.wear.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.telegram.messenger.GcmPushListenerService
import org.telegram.wear.MainActivity
import org.telegram.wear.presentation.ReplyActivity

/**
 * Receives FCM pushes directly on the watch over LTE (standalone mode).
 * Delegates actual message handling to the Telegram core's GcmPushListenerService,
 * then posts a Wear OS notification with an inline reply action.
 */
class WearPushService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_ID = "telegram_messages"
        const val REPLY_KEY = "reply_text"
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Let the core handle MTProto-level sync triggered by the push
        GcmPushListenerService.sendRegistrationToServer(message.from)

        val dialogId = message.data["dialog_id"]?.toLongOrNull() ?: return
        val senderName = message.data["sender"] ?: "Telegram"
        val text = message.data["text"] ?: return

        postNotification(dialogId, senderName, text)
    }

    override fun onNewToken(token: String) {
        // Forward new FCM token to Telegram servers via the core
        GcmPushListenerService.sendRegistrationToServer(token)
    }

    private fun postNotification(dialogId: Long, sender: String, text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Messages", NotificationManager.IMPORTANCE_HIGH)
        )

        // Open app on tap
        val openIntent = PendingIntent.getActivity(
            this, dialogId.toInt(),
            Intent(this, MainActivity::class.java).apply {
                putExtra("dialog_id", dialogId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Inline reply action — triggers system Wear OS input chooser
        val remoteInput = RemoteInput.Builder(REPLY_KEY).setLabel("Reply").build()

        val replyIntent = PendingIntent.getActivity(
            this, (dialogId + 1000).toInt(),
            Intent(this, ReplyActivity::class.java).apply {
                putExtra("dialog_id", dialogId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send, "Reply", replyIntent
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(sender)
            .setContentText(text)
            .setContentIntent(openIntent)
            .addAction(replyAction)
            .setAutoCancel(true)
            .build()

        nm.notify(dialogId.toInt(), notification)
    }
}
