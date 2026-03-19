package co.lgcs.telewatch.data

import android.graphics.Bitmap
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.telegram.messenger.AccountInstance
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.BuildVars
import org.telegram.messenger.MessagesController
import org.telegram.messenger.MessagesStorage
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import kotlin.coroutines.resume
import kotlin.math.min

class WearAuthManager {

    private val account: Int get() = UserConfig.selectedAccount
    private val accountInstance: AccountInstance get() = AccountInstance.getInstance(account)

    val isAuthenticated: Boolean
        get() = UserConfig.getInstance(account).isClientActivated

    /**
     * Emits [AuthState.ShowQr] with a QR bitmap, then polls until the user scans it.
     * On success emits [AuthState.Authenticated].
     */
    fun qrLoginFlow(): Flow<AuthState> = flow {
        android.util.Log.e("WearAuth", "qrLoginFlow started, isAuthenticated=$isAuthenticated account=$account")
        var lastToken: ByteArray? = null

        while (true) {
            android.util.Log.e("WearAuth", "polling exportLoginToken...")
            val result = requestLoginToken()

            when (result) {
                is LoginTokenResult.ShowQr -> {
                    // Only regenerate bitmap if the token bytes changed
                    if (!result.token.contentEquals(lastToken)) {
                        lastToken = result.token
                        emit(AuthState.ShowQr(generateQrBitmap(result.token)))
                    }
                    delay(3_000)
                }
                is LoginTokenResult.MigrateTo -> {
                    val success = importToken(result.dcId, result.token) ?: run {
                        delay(3_000)
                        return@flow
                    }
                    finalizeAuth(success)
                    emit(AuthState.Authenticated)
                    return@flow
                }
                is LoginTokenResult.Success -> {
                    finalizeAuth(result.authorization)
                    emit(AuthState.Authenticated)
                    return@flow
                }
                is LoginTokenResult.Error -> {
                    delay(5_000)
                }
            }
        }
    }

    private suspend fun requestLoginToken(): LoginTokenResult =
        suspendCancellableCoroutine { cont ->
            val req = TLRPC.TL_auth_exportLoginToken().apply {
                api_id = BuildVars.APP_ID
                api_hash = BuildVars.APP_HASH
                except_ids = ArrayList()
            }
            accountInstance.connectionsManager.sendRequest(req, { response, error ->
                android.util.Log.e("WearAuth", "exportLoginToken response: ${response?.javaClass?.simpleName} error: ${error?.text}")
                val result = try {
                    when {
                        response is TLRPC.TL_auth_loginToken ->
                            LoginTokenResult.ShowQr(response.token)
                        response is TLRPC.TL_auth_loginTokenSuccess ->
                            (response.authorization as? TLRPC.TL_auth_authorization)
                                ?.let { LoginTokenResult.Success(it) }
                                ?: LoginTokenResult.Error("unexpected authorization type: ${response.authorization?.javaClass?.simpleName}")
                        response is TLRPC.TL_auth_loginTokenMigrateTo ->
                            LoginTokenResult.MigrateTo(response.dc_id, response.token)
                        else -> LoginTokenResult.Error(error?.text ?: "null response: ${response?.javaClass?.simpleName}")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("WearAuth", "exception in response handler", e)
                    LoginTokenResult.Error(e.message ?: "exception")
                }
                android.util.Log.e("WearAuth", "result: $result")
                if (cont.isActive) cont.resume(result)
            }, ConnectionsManager.RequestFlagWithoutLogin)
        }

    private suspend fun importToken(dcId: Int, token: ByteArray): TLRPC.TL_auth_authorization? =
        suspendCancellableCoroutine { cont ->
            val req = TLRPC.TL_auth_importLoginToken().apply { this.token = token }
            // Use the full sendRequest signature to specify dcId with a RequestDelegate (not RequestDelegateTimestamp)
            accountInstance.connectionsManager.sendRequest(
                req,
                { response, _ ->
                    val auth = (response as? TLRPC.TL_auth_loginTokenSuccess)
                        ?.authorization as? TLRPC.TL_auth_authorization
                    if (cont.isActive) cont.resume(auth)
                },
                null, null,
                ConnectionsManager.RequestFlagWithoutLogin,
                dcId,
                ConnectionsManager.ConnectionTypeGeneric,
                true
            )
        }

    private fun finalizeAuth(res: TLRPC.TL_auth_authorization) {
        AndroidUtilities.runOnUIThread {
            ConnectionsManager.getInstance(account).setUserId(res.user.id)
            UserConfig.getInstance(account).clearConfig()
            MessagesController.getInstance(account).cleanup()
            UserConfig.getInstance(account).setCurrentUser(res.user)
            UserConfig.getInstance(account).saveConfig(true)
            MessagesStorage.getInstance(account).cleanup(true)
            val users = ArrayList<TLRPC.User>().apply { add(res.user) }
            MessagesStorage.getInstance(account).putUsersAndChats(users, null, true, true)
            MessagesController.getInstance(account).putUser(res.user, false)
            ConnectionsManager.getInstance(account).updateDcSettings()
            MessagesController.getInstance(account).loadAppConfig()
        }
    }

    private fun generateQrBitmap(token: ByteArray): Bitmap {
        val url = "tg://login?token=${android.util.Base64.encodeToString(
            token, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
        )}"
        return encodeQr(url, 300)
    }

    private fun encodeQr(content: String, size: Int): Bitmap {
        val hints = HashMap<EncodeHintType, Any>().apply {
            put(EncodeHintType.MARGIN, 1)
        }
        val placeholder = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        return QRCodeWriter().encode(content, size, size, hints, placeholder)
    }
}

private sealed class LoginTokenResult {
    data class ShowQr(val token: ByteArray) : LoginTokenResult()
    data class Success(val authorization: TLRPC.TL_auth_authorization) : LoginTokenResult()
    data class MigrateTo(val dcId: Int, val token: ByteArray) : LoginTokenResult()
    data class Error(val message: String) : LoginTokenResult()
}

sealed class AuthState {
    data class ShowQr(val bitmap: Bitmap) : AuthState()
    object Authenticated : AuthState()
}
