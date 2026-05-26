package dev.jdtech.jellyfin.film.presentation.explore

import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrMedia
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrMediaType
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrMovieDetails
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrRecentRequest
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrTvDetails
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrTvSeasonDetails

data class ExploreUiState(
    val recentRequests: RecentRequestsState = RecentRequestsState.Loading,
    val trending: SectionState = SectionState.Loading,
    val popularMovies: SectionState = SectionState.Loading,
    val popularSeries: SectionState = SectionState.Loading,
    val upcomingSeries: SectionState = SectionState.Loading,
    val search: ExploreSearchState = ExploreSearchState.Idle,
    val detail: ExploreDetailState = ExploreDetailState(),
    val authRequired: Boolean = false,
    val authError: Boolean = false,
    val error: Throwable? = null,
)

data class ExploreDetailState(
    val tmdbId: Int? = null,
    val mediaType: JellyseerrMediaType? = null,
    val selectedMedia: JellyseerrMedia? = null,
    val movieDetails: JellyseerrMovieDetails? = null,
    val tvDetails: JellyseerrTvDetails? = null,
    val isLoading: Boolean = false,
    val isSubmittingRequest: Boolean = false,
    val errorMessage: String? = null,
    val expandedSeasonNumber: Int? = null,
    val seasonDetails: Map<Int, ExploreSeasonDetailState> = emptyMap(),
    val selectedSeasonNumbers: Set<Int> = emptySet(),
)

sealed interface ExploreSeasonDetailState {
    data object Loading : ExploreSeasonDetailState

    data class Success(val details: JellyseerrTvSeasonDetails) : ExploreSeasonDetailState

    data class Error(val message: String) : ExploreSeasonDetailState
}

sealed interface RecentRequestsState {
    data object Hidden : RecentRequestsState

    data object Loading : RecentRequestsState

    data class Success(val items: List<JellyseerrRecentRequest>) : RecentRequestsState

    data class Error(val message: String) : RecentRequestsState
}

sealed interface SectionState {
    data object Loading : SectionState

    data object Empty : SectionState

    data class Success(val items: List<JellyseerrMedia>) : SectionState

    data class Error(val message: String) : SectionState
}

sealed interface ExploreSearchState {
    data object Idle : ExploreSearchState

    data object Loading : ExploreSearchState

    data class Success(val items: List<JellyseerrMedia>) : ExploreSearchState

    data class Empty(val query: String) : ExploreSearchState

    data class Error(val message: String) : ExploreSearchState
}

fun ExploreUiState.findMedia(
    tmdbId: Int,
    mediaType: JellyseerrMediaType,
): JellyseerrMedia? {
    val searchItems =
        (search as? ExploreSearchState.Success)
            ?.items
            .orEmpty()
    val recentRequestItems =
        (recentRequests as? RecentRequestsState.Success)
            ?.items
            ?.map { it.media }
            .orEmpty()

    val shelfItems =
        listOf(trending, popularMovies, popularSeries, upcomingSeries)
            .filterIsInstance<SectionState.Success>()
            .flatMap { it.items }

    return (recentRequestItems + shelfItems + searchItems)
        .firstOrNull { it.tmdbId == tmdbId && it.mediaType == mediaType }
}
