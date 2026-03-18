package org.telegram.wear.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Text
import org.telegram.wear.data.AuthState
import org.telegram.wear.data.WearAuthManager

/**
 * Displays a QR code for first-time login via the Telegram QR auth flow.
 * The user scans this on their phone's Telegram app to authenticate the watch session.
 * After auth, sessions are stored locally and the watch operates standalone.
 */
@Composable
fun AuthScreen(onAuthenticated: () -> Unit) {
    val authManager = remember { WearAuthManager() }
    var authState by remember { mutableStateOf<AuthState?>(null) }

    LaunchedEffect(Unit) {
        authManager.qrLoginFlow().collect { state ->
            authState = state
            if (state is AuthState.Authenticated) onAuthenticated()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (val state = authState) {
            null -> CircularProgressIndicator()
            is AuthState.ShowQr -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Scan with Telegram", modifier = Modifier.padding(bottom = 8.dp))
                    Image(
                        bitmap = state.bitmap.asImageBitmap(),
                        contentDescription = "Login QR code",
                        modifier = Modifier.size(180.dp)
                    )
                }
            }
            is AuthState.Authenticated -> CircularProgressIndicator()
        }
    }
}
