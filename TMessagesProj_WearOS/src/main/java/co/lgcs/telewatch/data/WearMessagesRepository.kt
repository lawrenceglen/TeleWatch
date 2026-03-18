package co.lgcs.telewatch.data

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.telegram.messenger.AccountInstance
import org.telegram.messenger.MessageObject
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.SendMessagesHelper
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.TLRPC

/**
 * Thin wrapper around the Telegram core's MessagesController and SendMessagesHelper.
 * All actual protocol work (MTProto, encryption, storage) stays in the core.
 */
class WearMessagesRepository {

    private val account: Int get() = UserConfig.selectedAccount
    private val messagesController: MessagesController get() = MessagesController.getInstance(account)
    private val accountInstance: AccountInstance get() = AccountInstance.getInstance(account)
    private val notificationCenter: NotificationCenter get() = NotificationCenter.getInstance(account)

    /** Emit the latest dialog list whenever it changes. */
    fun dialogsFlow(): Flow<List<TLRPC.Dialog>> = callbackFlow {
        val observer = NotificationCenter.NotificationCenterDelegate { id, _, _ ->
            if (id == NotificationCenter.dialogsNeedReload ||
                id == NotificationCenter.updateInterfaces ||
                id == NotificationCenter.messagesDidLoad
            ) {
                trySend(messagesController.dialogs)
            }
        }

        notificationCenter.addObserver(observer, NotificationCenter.dialogsNeedReload)
        notificationCenter.addObserver(observer, NotificationCenter.updateInterfaces)
        notificationCenter.addObserver(observer, NotificationCenter.messagesDidLoad)

        // Emit current state immediately
        trySend(messagesController.dialogs)

        // Trigger a refresh from the server
        messagesController.loadDialogs(0, 0, 20, true)

        awaitClose {
            notificationCenter.removeObserver(observer, NotificationCenter.dialogsNeedReload)
            notificationCenter.removeObserver(observer, NotificationCenter.updateInterfaces)
            notificationCenter.removeObserver(observer, NotificationCenter.messagesDidLoad)
        }
    }

    /** Emit messages for a given dialog whenever they change. */
    fun messagesFlow(dialogId: Long): Flow<List<MessageObject>> = callbackFlow {
        val observer = NotificationCenter.NotificationCenterDelegate { id, args, _ ->
            if (id == NotificationCenter.messagesDidLoad) {
                val loadedDialogId = args[0] as? Long ?: return@NotificationCenterDelegate
                if (loadedDialogId == dialogId) {
                    val messages = messagesController.dialogMessage[dialogId]
                    trySend(messages?.let { listOf(it) } ?: emptyList())
                }
            }
        }

        notificationCenter.addObserver(observer, NotificationCenter.messagesDidLoad)

        // Load messages for this dialog
        messagesController.loadMessages(
            dialogId, 0, false, 30, 0, 0, true,
            0, 0, 0, 0, 0, false, 0
        )

        awaitClose {
            notificationCenter.removeObserver(observer, NotificationCenter.messagesDidLoad)
        }
    }

    /** Send a text message (emoji or otherwise) to a dialog. */
    fun sendMessage(dialogId: Long, text: String) {
        val peer = messagesController.getInputPeer(dialogId)
        SendMessagesHelper.getInstance(account).sendMessage(
            SendMessagesHelper.SendMessageParams.of(text, dialogId, null, peer, null, null, null, null, true, 0)
        )
    }

    /** Helper: get the display name for a dialog. */
    fun getDialogName(dialog: TLRPC.Dialog): String {
        return messagesController.getDialogName(dialog.id) ?: "Unknown"
    }

    /** Helper: get unread count for a dialog. */
    fun getUnreadCount(dialog: TLRPC.Dialog): Int = dialog.unread_count
}
