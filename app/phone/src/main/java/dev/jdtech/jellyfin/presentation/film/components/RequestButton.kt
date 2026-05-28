package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun RequestButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (enabled) {
        Button(onClick = onClick, modifier = modifier) { Text(text = text) }
    } else {
        FilledTonalButton(onClick = {}, enabled = false, modifier = modifier) { Text(text = text) }
    }
}
