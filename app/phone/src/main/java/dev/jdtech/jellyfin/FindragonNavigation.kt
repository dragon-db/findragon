package dev.jdtech.jellyfin

import androidx.activity.compose.LocalActivity
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.film.presentation.explore.ExploreViewModel
import dev.jdtech.jellyfin.findragon.FindragonConfig
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.navigation.resumedAction
import dev.jdtech.jellyfin.presentation.film.ExploreDetailScreen
import dev.jdtech.jellyfin.presentation.film.ExploreScreen
import kotlinx.serialization.Serializable

// Findragon-only routes, tabs and navigation hooks. Upstream's NavigationRoot.kt only calls
// withFindragonTopLevelRoutes(), withFindragonTabs(), BindFindragonNavigation() and
// findragonEntries(), so upstream navigation changes stay easy to merge.

@Serializable data object ExploreRoute : NavKey

@Serializable data class ExploreDetailRoute(val tmdbId: Int, val mediaType: String) : NavKey

val exploreTab =
    TabBarItem(
        title = R.string.title_explore,
        icon = CoreR.drawable.ic_sparkles,
        route = ExploreRoute,
    )

/** Gives Explore its own back stack (a tab) when Seerr is configured. */
fun withFindragonTopLevelRoutes(routes: Set<NavKey>): Set<NavKey> =
    if (FindragonConfig.isSeerrEnabled) routes + ExploreRoute else routes

/** Inserts the Explore tab after Home when Seerr is configured. */
fun withFindragonTabs(tabs: List<TabBarItem>): List<TabBarItem> {
    if (!FindragonConfig.isSeerrEnabled) return tabs
    val homeIndex = tabs.indexOf(homeTab)
    return tabs.toMutableList().apply { add(homeIndex + 1, exploreTab) }
}

fun EntryProviderScope<NavKey>.findragonEntries(
    navigator: Navigator,
    onItemClick: (FindroidItem) -> Unit,
) {
    entry<ExploreRoute> {
        ExploreScreen(
            onItemClick = resumedAction { item -> onItemClick(item) },
            onMediaClick =
                resumedAction { media ->
                    navigator.navigate(
                        ExploreDetailRoute(
                            tmdbId = media.tmdbId,
                            mediaType = media.mediaType.apiValue,
                        )
                    )
                },
            viewModel = exploreViewModel(),
        )
    }
    entry<ExploreDetailRoute> { key ->
        ExploreDetailScreen(
            tmdbId = key.tmdbId,
            mediaType = key.mediaType,
            navigateBack = dropUnlessResumed { navigator.goBack() },
            onItemClick = resumedAction { item -> onItemClick(item) },
            viewModel = exploreViewModel(),
        )
    }
}

/**
 * Explore and its detail pages share one ViewModel, so a request made on a detail page shows up in
 * the Explore lists right away. Navigation 3 gives every entry its own ViewModel store, so the
 * shared one lives in the activity (the Explore tab root is never removed, so its lifetime is the
 * same).
 */
@Composable
private fun exploreViewModel(): ExploreViewModel =
    hiltViewModel(viewModelStoreOwner = LocalActivity.current as ComponentActivity)

/**
 * What [dev.jdtech.jellyfin.presentation.setup.session.SessionGate] needs from upstream's
 * NavigationRoot: the route on screen and a way to open the sign-in screen. NavigationRoot fills it
 * in through [BindFindragonNavigation].
 */
@Stable
class FindragonNavigationHandle {
    private var navigationState by mutableStateOf<NavigationState?>(null)
    private var setupBackStack by mutableStateOf<NavBackStack<NavKey>?>(null)
    private var setupComplete by mutableStateOf<Boolean?>(null)

    /** The main (tabs) navigator, or null while the setup flow is showing. */
    var navigator by mutableStateOf<Navigator?>(null)
        private set

    /** The route on screen, or null before NavigationRoot is shown. Snapshot state. */
    val currentRoute: NavKey?
        get() =
            when (setupComplete) {
                true -> navigationState?.currentBackStack()?.lastOrNull()
                false -> setupBackStack?.lastOrNull()
                null -> null
            }

    /** The setup flow's back stack while the setup flow is showing, else null. Snapshot state. */
    val activeSetupBackStack: NavBackStack<NavKey>?
        get() = setupBackStack.takeIf { setupComplete == false }

    internal fun bind(
        navigationState: NavigationState,
        navigator: Navigator,
        setupBackStack: NavBackStack<NavKey>,
        setupComplete: Boolean,
    ) {
        this.navigationState = navigationState
        this.navigator = navigator.takeIf { setupComplete }
        this.setupBackStack = setupBackStack
        this.setupComplete = setupComplete
    }
}

val LocalFindragonNavigationHandle = staticCompositionLocalOf<FindragonNavigationHandle?> { null }

/** The one hook line in NavigationRoot, after upstream has switched between setup and tabs. */
@Composable
fun BindFindragonNavigation(
    navigationState: NavigationState,
    navigator: Navigator,
    setupBackStack: NavBackStack<NavKey>,
    setupComplete: Boolean,
) {
    val handle = LocalFindragonNavigationHandle.current ?: return
    SideEffect { handle.bind(navigationState, navigator, setupBackStack, setupComplete) }
}
