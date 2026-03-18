package org.telegram.wear.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.RemoteInput
import androidx.wear.compose.material.*
import androidx.wear.input.RemoteInputIntentHelper
import androidx.wear.input.wearableExtender
import com.google.android.horologist.compose.layout.ScalingLazyColumn
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState
import org.telegram.messenger.MessageObject
import org.telegram.wear.data.WearMessagesRepository

private const val REMOTE_INPUT_KEY = "reply_text"

/**
 * Shows messages in a single dialog and provides a reply button that triggers
 * the Wear OS system input chooser (emoji, voice dictation, keyboard, canned replies).
 * No custom input UI — system-first.
 */
@Composable
fun MessageScreen(dialogId: Long, onBack: () -> Unit) {
    val repo = remember { WearMessagesRepository() }
    val messages by repo.messagesFlow(dialogId).collectAsState(initial = emptyList())

    // System RemoteInput launcher — this opens the Wear OS input chooser
    val replyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val bundle = RemoteInput.getResultsFromIntent(result.data ?: return@rememberLauncherForActivityResult)
        val replyText = bundle?.getCharSequence(REMOTE_INPUT_KEY)?.toString()
        if (!replyText.isNullOrBlank()) {
            repo.sendMessage(dialogId, replyText)
        }
    }

    fun launchSystemReply() {
        val remoteInput = RemoteInput.Builder(REMOTE_INPUT_KEY)
            .setLabel("Reply")
            .wearableExtender {
                setEmojisAllowed(true)
            }
            .build()

        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent().apply {
            RemoteInputIntentHelper.putRemoteInputsExtra(this, listOf(remoteInput))
        }
        replyLauncher.launch(intent)
    }

    val columnState = rememberResponsiveColumnState()

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = columnState.state) }
    ) {
        ScalingLazyColumn(columnState = columnState) {
            // Reply button pinned at top
            item {
                Button(
                    onClick = { launchSystemReply() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Text("Reply")
                }
            }

            if (messages.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                items(messages.size) { index ->
                    MessageBubble(message = messages[index])
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: MessageObject) {
    val isOutgoing = message.isOut

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = if (isOutgoing) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Card(
            onClick = {},
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                if (!isOutgoing) {
                    Text(
                        text = message.senderName ?: "",
                        style = MaterialTheme.typography.caption2,
                        color = MaterialTheme.colors.primary
                    )
                }
                Text(
                    text = message.messageText?.toString() ?: "",
                    style = MaterialTheme.typography.body2
                )
            }
        }
    }
}
