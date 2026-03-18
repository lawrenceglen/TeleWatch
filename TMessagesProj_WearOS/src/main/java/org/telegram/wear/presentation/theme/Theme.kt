package org.telegram.wear.presentation.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material.MaterialTheme

@Composable
fun WearAppTheme(content: @Composable () -> Unit) {
    // Use the system Wear OS Material theme — system-first principle.
    // Do not override colours, typography, or shapes unless Telegram branding
    // specifically requires it for a UI element we own.
    MaterialTheme(content = content)
}
