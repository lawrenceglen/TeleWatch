package co.lgcs.telewatch.data

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.telegram.messenger.MessageObject
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.SendMessagesHelper
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC

class WearMessagesRepository {

    private val account: Int get() = UserConfig.selectedAccount
    private val messagesController: MessagesController get() = MessagesController.getInstance(account)
    private val notificationCenter: NotificationCenter get() = NotificationCenter.getInstance(account)

    fun dialogsFlow(): Flow<List<TLRPC.Dialog>> = callbackFlow {
        val observer = NotificationCenter.NotificationCenterDelegate { id, _, _ ->
            if (id == NotificationCenter.dialogsNeedReload ||
                id == NotificationCenter.updateInterfaces ||
                id == NotificationCenter.messagesDidLoad
            ) {
                trySend(messagesController.allDialogs.toList())
            }
        }

        notificationCenter.addObserver(observer, NotificationCenter.dialogsNeedReload)
        notificationCenter.addObserver(observer, NotificationCenter.updateInterfaces)
        notificationCenter.addObserver(observer, NotificationCenter.messagesDidLoad)

        trySend(messagesController.allDialogs.toList())
        messagesController.loadDialogs(0, 0, 20, true)

        awaitClose {
            notificationCenter.removeObserver(observer, NotificationCenter.dialogsNeedReload)
            notificationCenter.removeObserver(observer, NotificationCenter.updateInterfaces)
            notificationCenter.removeObserver(observer, NotificationCenter.messagesDidLoad)
        }
    }

    fun messagesFlow(dialogId: Long): Flow<List<MessageObject>> = callbackFlow {
        val classGuid = ConnectionsManager.generateClassGuid()

        val observer = NotificationCenter.NotificationCenterDelegate { id, _, args ->
            if (id == NotificationCenter.messagesDidLoad) {
                val loadedDialogId = args[0] as? Long ?: return@NotificationCenterDelegate
                if (loadedDialogId == dialogId) {
                    @Suppress("UNCHECKED_CAST")
                    val messages = args[2] as? ArrayList<MessageObject> ?: ArrayList()
                    trySend(messages)
                }
            }
        }

        notificationCenter.addObserver(observer, NotificationCenter.messagesDidLoad)

        messagesController.loadMessages(
            dialogId, 0, false, 30, 0, 0, true,
            0, classGuid, 0, 0, 0, 0, 0, 0, false
        )

        awaitClose {
            notificationCenter.removeObserver(observer, NotificationCenter.messagesDidLoad)
        }
    }

    fun sendMessage(dialogId: Long, text: String) {
        val params = SendMessagesHelper.SendMessageParams.of(text, dialogId)
        SendMessagesHelper.getInstance(account).sendMessage(params)
    }

    fun getDialogName(dialog: TLRPC.Dialog): String =
        messagesController.getPeerName(dialog.id) ?: "Unknown"

    fun getUnreadCount(dialog: TLRPC.Dialog): Int = dialog.unread_count
}
