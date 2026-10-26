package dev.jdtech.jellyfin.presentation.dragonhub

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.dragonhub.DragonHubViewModel
import timber.log.Timber

/**
 * Dragon Hub update and notification dialogs shown over the whole app. The only Findragon hook in
 * MainActivity; the check runs once, after the app finished loading.
 */
@Composable
fun DragonHubOverlays(isAppReady: Boolean, viewModel: DragonHubViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(isAppReady) {
        if (isAppReady) {
            viewModel.check()
        }
    }

    state.update?.let { update ->
        UpdateAvailableDialog(
            update = update,
            onUpdateClick = {
                val launched = context.openHttpUrl(update.apkUrl)
                if (!launched) {
                    Timber.w("Failed to launch Dragon Hub update URL: ${update.apkUrl}")
                    viewModel.dismissUpdate()
                } else if (!update.forceUpdate) {
                    viewModel.dismissUpdate()
                }
            },
            onDismiss = viewModel::dismissUpdate,
        )
    }

    if (state.update == null && state.showNotifications && state.notifications.isNotEmpty()) {
        DragonHubNotificationsDialog(
            notifications = state.notifications,
            onDismiss = viewModel::dismissNotifications,
        )
    }
}

private fun Context.openHttpUrl(url: String): Boolean {
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
    if (uri.scheme !in setOf("http", "https")) return false

    val intent = Intent(Intent.ACTION_VIEW, uri)
    return try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
