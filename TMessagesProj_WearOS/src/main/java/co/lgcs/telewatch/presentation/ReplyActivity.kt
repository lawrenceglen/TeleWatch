package co.lgcs.telewatch.presentation

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.core.app.RemoteInput

/**
 * Receives inline reply results from Wear OS notification actions.
 * When the user replies directly from a notification (not in-app),
 * this activity picks up the RemoteInput result and sends via the core.
 */
class ReplyActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dialogId = intent.getLongExtra("dialog_id", 0L)
        if (dialogId == 0L) {
            finish()
            return
        }

        val bundle = RemoteInput.getResultsFromIntent(intent)
        val replyText = bundle?.getCharSequence("reply_text")?.toString()

        if (!replyText.isNullOrBlank()) {
            org.telegram.wear.data.WearMessagesRepository().sendMessage(dialogId, replyText)
        }

        finish()
    }
}
