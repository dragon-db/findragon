package dev.jdtech.jellyfin.presentation.setup.session

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import dev.jdtech.jellyfin.HomeRoute
import dev.jdtech.jellyfin.LoginRoute
import dev.jdtech.jellyfin.UsersRoute
import dev.jdtech.jellyfin.setup.R as SetupR
import dev.jdtech.jellyfin.setup.domain.session.SignInReason
import dev.jdtech.jellyfin.setup.presentation.session.SessionGateViewModel
import dev.jdtech.jellyfin.setup.presentation.session.messageRes
import kotlinx.coroutines.flow.first

/**
 * Checks the saved Jellyfin login before the app shows [content] and whenever the home screen opens
 * for another user. A dead login is replaced silently, or the user lands on the sign-in screen with
 * their username filled in. The only Findragon hook around NavigationRoot in MainActivity.
 */
@Composable
fun SessionGate(
    navController: NavHostController,
    viewModel: SessionGateViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.start() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onAppForegrounded() }

    if (!state.isReady) return

    content()

    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collect {
            // Read the destination now, not from the emitted entry: an entry delivered late may be a
            // home screen the sign-in redirect already removed.
            if (navController.currentDestination?.hasRoute(HomeRoute::class) == true) {
                viewModel.onHomeShown()
            }
        }
    }

    state.pendingSignIn?.let { username ->
        LaunchedEffect(username) {
            // Wait until the NavHost inside content() has its graph.
            navController.currentBackStackEntryFlow.first()
            navController.navigate(UsersRoute) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
            navController.navigate(LoginRoute(username = username))
            viewModel.onSignInOpened()
        }
    }

    if (state.reloadHome) {
        LaunchedEffect(Unit) {
            if (navController.currentDestination?.hasRoute(HomeRoute::class) == true) {
                navController.navigate(HomeRoute) {
                    popUpTo(navController.graph.id) { inclusive = true }
                }
            }
            viewModel.onHomeReloaded()
        }
    }

    state.dialog?.let { reason ->
        SignInAgainDialog(reason = reason, onDismiss = viewModel::dismissDialog)
    }
}

@Composable
private fun SignInAgainDialog(reason: SignInReason, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(SetupR.string.session_sign_in_title)) },
        text = { Text(text = stringResource(reason.messageRes())) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(android.R.string.ok)) }
        },
    )
}
