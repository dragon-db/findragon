package dev.jdtech.jellyfin.presentation.setup.session

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import dev.jdtech.jellyfin.ExploreDetailRoute
import dev.jdtech.jellyfin.ExploreRoute
import dev.jdtech.jellyfin.FindragonNavigationHandle
import dev.jdtech.jellyfin.HomeRoute
import dev.jdtech.jellyfin.LocalFindragonNavigationHandle
import dev.jdtech.jellyfin.LoginRoute
import dev.jdtech.jellyfin.UsersRoute
import dev.jdtech.jellyfin.dragonhub.DragonHubViewModel
import dev.jdtech.jellyfin.presentation.dragonhub.AccountNoticeDialog
import dev.jdtech.jellyfin.setup.R as SetupR
import dev.jdtech.jellyfin.setup.domain.session.SignInReason
import dev.jdtech.jellyfin.setup.presentation.session.SessionGateViewModel
import dev.jdtech.jellyfin.setup.presentation.session.messageRes
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Checks the saved Jellyfin login before the app shows [content] and whenever the home screen opens
 * for another user. A dead login is replaced silently, or the user lands on the sign-in screen with
 * their username filled in. An expired or disabled DragonHub account gets a pop-up, shown again
 * when Explore opens. The only Findragon hook around NavigationRoot in MainActivity; NavigationRoot
 * reports its routes back through [FindragonNavigationHandle].
 */
@Composable
fun SessionGate(
    viewModel: SessionGateViewModel = hiltViewModel(),
    // The same activity-scoped instance DragonHubOverlays uses.
    dragonHubViewModel: DragonHubViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dragonHubState by dragonHubViewModel.state.collectAsStateWithLifecycle()
    val navigation = remember { FindragonNavigationHandle() }
    // Changing it rebuilds the navigation from scratch, which loads the home screen again.
    var navigationGeneration by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { viewModel.start() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onAppForegrounded() }

    if (!state.isReady) return

    key(navigationGeneration) {
        CompositionLocalProvider(LocalFindragonNavigationHandle provides navigation) { content() }
    }

    LaunchedEffect(navigation) {
        var previous: NavKey? = null
        snapshotFlow { navigation.currentRoute }
            .collect { route ->
                when {
                    route == HomeRoute -> viewModel.onHomeShown()
                    // Once per Explore visit, not again when coming back from an Explore detail page.
                    route == ExploreRoute && !previous.isExplore() -> viewModel.onExploreShown()
                }
                previous = route
            }
    }

    state.pendingSignIn?.let { username ->
        LaunchedEffect(username) {
            // The view model already marked the dead login as signed out, so upstream's
            // NavigationRoot switches to its setup flow on the user list. Sign-in goes on top of it.
            val setupBackStack =
                withTimeoutOrNull(SETUP_SWITCH_TIMEOUT_MS) {
                    snapshotFlow { navigation.activeSetupBackStack }.filterNotNull().first()
                }
            val loginRoute = LoginRoute(username = username)
            if (setupBackStack != null) {
                if (setupBackStack.lastOrNull() != loginRoute) setupBackStack.add(loginRoute)
            } else {
                // Still on the tabs (the login could not be marked as signed out): open it there.
                navigation.navigator?.apply {
                    navigateHome()
                    navigate(UsersRoute)
                    navigate(loginRoute)
                }
            }
            viewModel.onSignInOpened()
        }
    }

    if (state.reloadHome) {
        LaunchedEffect(Unit) {
            if (navigation.currentRoute == HomeRoute) navigationGeneration++
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

private fun NavKey?.isExplore(): Boolean = this is ExploreRoute || this is ExploreDetailRoute

private const val SETUP_SWITCH_TIMEOUT_MS = 3_000L

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
