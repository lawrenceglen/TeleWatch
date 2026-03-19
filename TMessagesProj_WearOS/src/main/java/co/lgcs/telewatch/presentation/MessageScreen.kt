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
    val orderedMessages = remember(messages) { messages.reversed() }

    LaunchedEffect(orderedMessages.size) {
        if (orderedMessages.isNotEmpty()) {
            columnState.state.animateScrollToItem(orderedMessages.size)
        }
    }

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = columnState.state) }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
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
                // Padding so the last message clears the overlaid reply button
                item { Spacer(modifier = Modifier.height(48.dp)) }
            }

            // Small reply button pinned to bottom center
            Button(
                onClick = { launchSystemReply() },
                modifier = Modifier
                    .fillMaxWidth(0.45f)
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
            ) {
                Text("Reply")
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
                Text(
                    text = message.messageText?.toString() ?: "",
                    style = MaterialTheme.typography.body2
                )
            }
        }
    }
}
