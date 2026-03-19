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

    private fun filteredDialogs(): List<TLRPC.Dialog> =
        messagesController.allDialogs
            .filter { it.id > 0 } // positive IDs = 1-on-1 user chats; negative = groups/channels
            .sortedByDescending { it.last_message_date }

    fun dialogsFlow(): Flow<List<TLRPC.Dialog>> = callbackFlow {
        val observer = NotificationCenter.NotificationCenterDelegate { id, _, _ ->
            if (id == NotificationCenter.dialogsNeedReload ||
                id == NotificationCenter.updateInterfaces ||
                id == NotificationCenter.messagesDidLoad
            ) {
                trySend(filteredDialogs())
            }
        }

        notificationCenter.addObserver(observer, NotificationCenter.dialogsNeedReload)
        notificationCenter.addObserver(observer, NotificationCenter.updateInterfaces)
        notificationCenter.addObserver(observer, NotificationCenter.messagesDidLoad)

        trySend(filteredDialogs())
        messagesController.loadDialogs(0, 0, 20, true)

        awaitClose {
            notificationCenter.removeObserver(observer, NotificationCenter.dialogsNeedReload)
            notificationCenter.removeObserver(observer, NotificationCenter.updateInterfaces)
            notificationCenter.removeObserver(observer, NotificationCenter.messagesDidLoad)
        }
    }

    fun messagesFlow(dialogId: Long): Flow<List<MessageObject>> = callbackFlow {
        val classGuid = ConnectionsManager.generateClassGuid()
        val currentMessages = mutableListOf<MessageObject>()

        val observer = NotificationCenter.NotificationCenterDelegate { id, _, args ->
            when (id) {
                NotificationCenter.messagesDidLoad -> {
                    val loadedDialogId = args[0] as? Long ?: return@NotificationCenterDelegate
                    if (loadedDialogId == dialogId) {
                        @Suppress("UNCHECKED_CAST")
                        val loaded = args[2] as? ArrayList<MessageObject> ?: ArrayList()
                        currentMessages.clear()
                        currentMessages.addAll(loaded)
                        trySend(currentMessages.toList())
                    }
                }
                NotificationCenter.didReceiveNewMessages -> {
                    val newMsgDialogId = args[0] as? Long ?: return@NotificationCenterDelegate
                    if (newMsgDialogId == dialogId) {
                        val scheduled = args[2] as? Boolean ?: false
                        if (!scheduled) {
                            @Suppress("UNCHECKED_CAST")
                            val newMessages = args[1] as? ArrayList<MessageObject> ?: ArrayList()
                            currentMessages.addAll(0, newMessages) // prepend (server order: newest first)
                            trySend(currentMessages.toList())
                        }
                    }
                }
            }
        }

        notificationCenter.addObserver(observer, NotificationCenter.messagesDidLoad)
        notificationCenter.addObserver(observer, NotificationCenter.didReceiveNewMessages)

        messagesController.loadMessages(
            dialogId, 0, false, 30, 0, 0, true,
            0, classGuid, 0, 0, 0, 0, 0, 0, false
        )

        awaitClose {
            notificationCenter.removeObserver(observer, NotificationCenter.messagesDidLoad)
            notificationCenter.removeObserver(observer, NotificationCenter.didReceiveNewMessages)
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
