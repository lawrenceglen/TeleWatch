package co.lgcs.telewatch.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import co.lgcs.telewatch.MainActivity
import co.lgcs.telewatch.presentation.ReplyActivity
import org.telegram.messenger.PushListenerController

class WearPushService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_ID = "telegram_messages"
        const val REPLY_KEY = "reply_text"
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Delegate to the core exactly as GcmPushListenerService does
        PushListenerController.processRemoteMessage(
            PushListenerController.PUSH_TYPE_FIREBASE,
            message.data["p"],
            message.sentTime
        )

        val dialogId = message.data["dialog_id"]?.toLongOrNull() ?: return
        val senderName = message.data["sender"] ?: "Telegram"
        val text = message.data["text"] ?: return

        postNotification(dialogId, senderName, text)
    }

    override fun onNewToken(token: String) {
        PushListenerController.sendRegistrationToServer(
            PushListenerController.PUSH_TYPE_FIREBASE, token
        )
    }

    private fun postNotification(dialogId: Long, sender: String, text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Messages", NotificationManager.IMPORTANCE_HIGH)
        )

        val openIntent = PendingIntent.getActivity(
            this, dialogId.toInt(),
            Intent(this, MainActivity::class.java).also { intent ->
                intent.putExtra("dialog_id", dialogId)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val remoteInput = RemoteInput.Builder(REPLY_KEY).setLabel("Reply").build()

        val replyIntent = PendingIntent.getActivity(
            this, (dialogId + 1000).toInt(),
            Intent(this, ReplyActivity::class.java).also { intent ->
                intent.putExtra("dialog_id", dialogId)
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
