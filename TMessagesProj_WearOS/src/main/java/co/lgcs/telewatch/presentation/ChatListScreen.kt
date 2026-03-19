package co.lgcs.telewatch.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.layout.ScalingLazyColumn
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState
import org.telegram.tgnet.TLRPC
import co.lgcs.telewatch.data.WearMessagesRepository

/**
 * Main chat list. Uses Horologist's ScalingLazyColumn which handles the round
 * Wear OS screen edge fade and rotary scroll input automatically.
 */
@OptIn(ExperimentalHorologistApi::class)
@Composable
fun ChatListScreen(onChatSelected: (Long) -> Unit) {
    val repo = remember { WearMessagesRepository() }
    val dialogs by repo.dialogsFlow().collectAsState(initial = emptyList())

    val columnState = rememberResponsiveColumnState()

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = columnState.state) }
    ) {
        ScalingLazyColumn(columnState = columnState) {
            item {
                ListHeader { Text("Chats") }
            }

            if (dialogs.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                items(dialogs.size) { index ->
                    val dialog = dialogs[index]
                    ChatRow(
                        dialog = dialog,
                        repo = repo,
                        onClick = { onChatSelected(dialog.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatRow(
    dialog: TLRPC.Dialog,
    repo: WearMessagesRepository,
    onClick: () -> Unit
) {
    val unread = repo.getUnreadCount(dialog)

    Chip(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        onClick = onClick,
        label = {
            Text(
                text = repo.getDialogName(dialog),
                maxLines = 1
            )
        },
        secondaryLabel = if (unread > 0) {
            { Text(text = "$unread unread") }
        } else null,
        colors = ChipDefaults.primaryChipColors()
    )
}
