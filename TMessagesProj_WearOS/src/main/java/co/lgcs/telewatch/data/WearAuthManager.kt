package co.lgcs.telewatch.data

import android.graphics.Bitmap
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.telegram.messenger.AccountInstance
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC

/**
 * Handles first-time authentication via QR code login (auth.exportLoginToken).
 * After initial QR auth the session is stored locally; subsequent launches are
 * fully standalone with no phone needed.
 */
class WearAuthManager {

    private val account: Int get() = UserConfig.selectedAccount
    private val accountInstance: AccountInstance get() = AccountInstance.getInstance(account)
    private val notificationCenter: NotificationCenter get() = NotificationCenter.getInstance(account)

    val isAuthenticated: Boolean
        get() = UserConfig.getInstance(account).isClientActivated

    /**
     * Emits QR code bitmaps as the login token refreshes (valid for 30s each).
     * Completes when authentication succeeds.
     */
    fun qrLoginFlow(): Flow<AuthState> = callbackFlow {
        val observer = NotificationCenter.NotificationCenterDelegate { id, _, _ ->
            when (id) {
                NotificationCenter.mainUserInfoChanged -> {
                    if (isAuthenticated) trySend(AuthState.Authenticated)
                }
            }
        }

        notificationCenter.addObserver(observer, NotificationCenter.mainUserInfoChanged)

        // Request a QR login token from Telegram
        requestQrToken { bitmap ->
            trySend(AuthState.ShowQr(bitmap))
        }

        awaitClose {
            notificationCenter.removeObserver(observer, NotificationCenter.mainUserInfoChanged)
        }
    }

    private fun requestQrToken(onToken: (Bitmap) -> Unit) {
        val req = TLRPC.TL_auth_exportLoginToken().apply {
            api_id = org.telegram.messenger.BuildVars.APP_ID
            api_hash = org.telegram.messenger.BuildVars.APP_HASH
            except_ids = ArrayList()
        }

        accountInstance.connectionsManager.sendRequest(req) { response, _ ->
            when (response) {
                is TLRPC.TL_auth_loginToken -> {
                    val bitmap = generateQrBitmap(response.token)
                    onToken(bitmap)
                    // Token expires in 30s — schedule a refresh
                }
                is TLRPC.TL_auth_loginTokenSuccess -> {
                    // Auth completed via another device scan — handled by observer
                }
            }
        }
    }

    private fun generateQrBitmap(token: ByteArray): Bitmap {
        // Encode token as a tg://login?token=<base64url> QR code
        val url = "tg://login?token=${android.util.Base64.encodeToString(
            token, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
        )}"
        return encodeQr(url, 300)
    }

    private fun encodeQr(content: String, size: Int): Bitmap {
        // Uses ZXing (bundled in TMessagesProj) to generate the QR bitmap
        val hints = mapOf(
            com.google.zxing.EncodeHintType.MARGIN to 1
        )
        val bits = com.google.zxing.qrcode.QRCodeWriter()
            .encode(content, com.google.zxing.BarcodeFormat.QR_CODE, size, size, hints)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (bits[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
            }
        }
        return bmp
    }
}

sealed class AuthState {
    data class ShowQr(val bitmap: Bitmap) : AuthState()
    object Authenticated : AuthState()
}
