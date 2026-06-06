package dev.jdtech.jellyfin

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import dev.jdtech.jellyfin.dragonhub.DragonHubViewModel
import dev.jdtech.jellyfin.presentation.dragonhub.DragonHubNotificationsDialog
import dev.jdtech.jellyfin.presentation.dragonhub.UpdateAvailableDialog
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.utils.LocalOfflineMode
import dev.jdtech.jellyfin.viewmodels.MainViewModel

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val dragonHubViewModel: DragonHubViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val dragonHubState by dragonHubViewModel.state.collectAsStateWithLifecycle()
            val context = LocalContext.current

            LaunchedEffect(state.isLoading) {
                if (!state.isLoading) {
                    dragonHubViewModel.check()
                }
            }

            FindroidTheme(dynamicColor = state.isDynamicColors) {
                val navController = rememberNavController()
                if (!state.isLoading) {
                    CompositionLocalProvider(LocalOfflineMode provides state.isOfflineMode) {
                        NavigationRoot(
                            navController = navController,
                            hasServers = state.hasServers,
                            hasCurrentServer = state.hasCurrentServer,
                            hasCurrentUser = state.hasCurrentUser,
                        )
                    }
                }

                dragonHubState.update?.let { update ->
                    UpdateAvailableDialog(
                        update = update,
                        onUpdateClick = {
                            val launched = context.openHttpUrl(update.apkUrl)
                            if (!update.forceUpdate || launched) {
                                dragonHubViewModel.dismissUpdate()
                            }
                        },
                        onDismiss = dragonHubViewModel::dismissUpdate,
                    )
                }

                if (
                    dragonHubState.update == null &&
                        dragonHubState.showNotifications &&
                        dragonHubState.notifications.isNotEmpty()
                ) {
                    DragonHubNotificationsDialog(
                        notifications = dragonHubState.notifications,
                        onDismiss = dragonHubViewModel::dismissNotifications,
                    )
                }
            }
        }
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
