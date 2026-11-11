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
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import dev.jdtech.jellyfin.ExploreDetailRoute
import dev.jdtech.jellyfin.ExploreRoute
import dev.jdtech.jellyfin.HomeRoute
import dev.jdtech.jellyfin.LoginRoute
import dev.jdtech.jellyfin.UsersRoute
import dev.jdtech.jellyfin.dragonhub.DragonHubViewModel
import dev.jdtech.jellyfin.presentation.dragonhub.AccountNoticeDialog
import dev.jdtech.jellyfin.setup.R as SetupR
import dev.jdtech.jellyfin.setup.domain.session.SignInReason
import dev.jdtech.jellyfin.setup.presentation.session.SessionGateViewModel
import dev.jdtech.jellyfin.setup.presentation.session.messageRes
import kotlinx.coroutines.flow.first

/**
 * Checks the saved Jellyfin login before the app shows [content] and whenever the home screen opens
 * for another user. A dead login is replaced silently, or the user lands on the sign-in screen with
 * their username filled in. An expired or disabled DragonHub account gets a pop-up, shown again
 * when Explore opens. The only Findragon hook around NavigationRoot in MainActivity.
 */
@Composable
fun SessionGate(
    navController: NavHostController,
    viewModel: SessionGateViewModel = hiltViewModel(),
    // The same activity-scoped instance DragonHubOverlays uses.
    dragonHubViewModel: DragonHubViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dragonHubState by dragonHubViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.start() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onAppForegrounded() }

    if (!state.isReady) return

    content()

    LaunchedEffect(navController) {
        var previous: NavDestination? = null
        navController.currentBackStackEntryFlow.collect {
            // Read the destination now, not from the emitted entry: an entry delivered late may be a
            // home screen the sign-in redirect already removed.
            val destination = navController.currentDestination
            when {
                destination?.hasRoute(HomeRoute::class) == true -> viewModel.onHomeShown()
                // Once per Explore visit, not again when coming back from an Explore detail page.
                destination?.hasRoute(ExploreRoute::class) == true &&
                    previous?.isExplore() != true -> viewModel.onExploreShown()
            }
            previous = destination
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

    // The update prompt always wins. Every dialog is its own window and the newest one is on top, so
    // these wait until DragonHubOverlays shows no update: a forced update then stays above everything,
    // even for an expired or disabled account.
    if (dragonHubState.update != null) return

    state.dialog?.let { reason ->
        SignInAgainDialog(reason = reason, onDismiss = viewModel::dismissDialog)
    }

    state.accountNotice?.let { status ->
        AccountNoticeDialog(status = status, onDismiss = viewModel::dismissAccountNotice)
    }
}

private fun NavDestination.isExplore(): Boolean =
    hasRoute(ExploreRoute::class) || hasRoute(ExploreDetailRoute::class)

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
