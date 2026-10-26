package dev.jdtech.jellyfin

import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.film.presentation.explore.ExploreViewModel
import dev.jdtech.jellyfin.findragon.FindragonConfig
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.presentation.film.ExploreDetailScreen
import dev.jdtech.jellyfin.presentation.film.ExploreScreen
import kotlinx.serialization.Serializable

// Findragon-only routes and tabs. Upstream's NavigationRoot.kt only calls withFindragonTabs() and
// findragonDestinations(), so upstream navigation changes stay easy to merge.

@Serializable data object ExploreRoute

@Serializable data class ExploreDetailRoute(val tmdbId: Int, val mediaType: String)

val exploreTab =
    TabBarItem(
        title = R.string.title_explore,
        icon = CoreR.drawable.ic_sparkles,
        route = ExploreRoute,
    )

/** Inserts the Explore tab after Home when Seerr is configured. */
fun withFindragonTabs(tabs: List<TabBarItem>): List<TabBarItem> {
    if (!FindragonConfig.isSeerrEnabled) return tabs
    val homeIndex = tabs.indexOf(homeTab)
    return tabs.toMutableList().apply { add(homeIndex + 1, exploreTab) }
}

fun NavGraphBuilder.findragonDestinations(
    navController: NavHostController,
    onItemClick: (FindroidItem) -> Unit,
    navigateToExploreDetail: (ExploreDetailRoute) -> Unit,
    navigateBack: () -> Unit,
) {
    composable<ExploreRoute> {
        val exploreBackStackEntry =
            remember(navController) {
                navController.getBackStackEntry(ExploreRoute::class.qualifiedName!!)
            }
        val exploreViewModel: ExploreViewModel = hiltViewModel(exploreBackStackEntry)
        ExploreScreen(
            onItemClick = onItemClick,
            onMediaClick = { media ->
                navigateToExploreDetail(
                    ExploreDetailRoute(tmdbId = media.tmdbId, mediaType = media.mediaType.apiValue)
                )
            },
            viewModel = exploreViewModel,
        )
    }
    composable<ExploreDetailRoute> { backStackEntry ->
        val route: ExploreDetailRoute = backStackEntry.toRoute()
        val exploreBackStackEntry =
            remember(navController) {
                navController.getBackStackEntry(ExploreRoute::class.qualifiedName!!)
            }
        val exploreViewModel: ExploreViewModel = hiltViewModel(exploreBackStackEntry)
        ExploreDetailScreen(
            tmdbId = route.tmdbId,
            mediaType = route.mediaType,
            navigateBack = { navigateBack() },
            onItemClick = onItemClick,
            viewModel = exploreViewModel,
        )
    }
}
