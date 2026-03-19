package co.lgcs.telewatch.presentation

import android.app.RemoteInput
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.wear.input.RemoteInputIntentHelper
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.layout.ScalingLazyColumn
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState
import org.telegram.messenger.MessageObject
import co.lgcs.telewatch.data.WearMessagesRepository

private const val REMOTE_INPUT_KEY = "reply_text"

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun MessageScreen(dialogId: Long, onBack: () -> Unit) {
    val repo = remember { WearMessagesRepository() }
    val messages by repo.messagesFlow(dialogId).collectAsState(initial = emptyList())

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
            .build()
        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(remoteInput))
        replyLauncher.launch(intent)
    }

    val columnState = rememberResponsiveColumnState()

    // Messages come from server newest-first; reverse so oldest is at top, newest at bottom
    val orderedMessages = remember(messages) { messages.reversed() }

    // Scroll to bottom (reply button) whenever messages load or update
    val itemCount = if (orderedMessages.isEmpty()) 2 else orderedMessages.size + 1
    LaunchedEffect(orderedMessages.size) {
        if (orderedMessages.isNotEmpty()) {
            columnState.state.animateScrollToItem(itemCount - 1)
        }
    }

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = columnState.state) }
    ) {
        ScalingLazyColumn(columnState = columnState) {
            if (orderedMessages.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                items(orderedMessages.size) { index ->
                    MessageBubble(message = orderedMessages[index])
                }
            }

            // Reply button always at the bottom
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
                        text = message.messageOwner?.from_id?.toString() ?: "",
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
