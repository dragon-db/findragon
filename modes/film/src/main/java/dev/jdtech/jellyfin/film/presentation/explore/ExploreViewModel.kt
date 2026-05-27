package dev.jdtech.jellyfin.film.presentation.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.jellyseerr.JellyseerrMissingCredentialsException
import dev.jdtech.jellyfin.jellyseerr.JellyseerrRepository
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrMedia
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrMediaType
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrMovieDetails
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrRecentRequest
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrTvDetails
import dev.jdtech.jellyfin.models.FindroidItem
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

@HiltViewModel
class ExploreViewModel @Inject constructor(private val repository: JellyseerrRepository) : ViewModel() {
    private val _state = MutableStateFlow(ExploreUiState())
    val state = _state.asStateFlow()

    private val eventsChannel = Channel<ExploreEvent>()
    val events = eventsChannel.receiveAsFlow()
    private var currentSearchJob: Job? = null
    private var currentDetailJob: Job? = null

    fun loadData(isRefreshing: Boolean = false) {
        viewModelScope.launch {
            _state.emit(
                _state.value.copy(
                    recentRequests = RecentRequestsState.Loading,
                    trending = SectionState.Loading,
                    popularMovies = SectionState.Loading,
                    popularSeries = SectionState.Loading,
                    upcomingSeries = SectionState.Loading,
                    authRequired = false,
                    authError = false,
                    error = null,
                    isRefreshing = isRefreshing,
                )
            )

            try {
                repository.ensureAuthenticated()
            } catch (_: JellyseerrMissingCredentialsException) {
                _state.emit(
                    ExploreUiState(
                        recentRequests = RecentRequestsState.Hidden,
                        trending = SectionState.Empty,
                        popularMovies = SectionState.Empty,
                        popularSeries = SectionState.Empty,
                        upcomingSeries = SectionState.Empty,
                        authRequired = true,
                    )
                )
                return@launch
            } catch (e: Exception) {
                _state.emit(
                    ExploreUiState(
                        recentRequests = RecentRequestsState.Hidden,
                        trending = SectionState.Empty,
                        popularMovies = SectionState.Empty,
                        popularSeries = SectionState.Empty,
                        upcomingSeries = SectionState.Empty,
                        authError = true,
                        error = e,
                    )
                )
                return@launch
            }

            supervisorScope {
                val recentRequests = async { loadRecentRequestsState() }
                val trending = async { loadSection { repository.getTrending() } }
                val popularMovies = async { loadSection { repository.getPopularMovies() } }
                val popularSeries = async { loadSection { repository.getPopularSeries() } }
                val upcomingSeries = async { loadSection { repository.getUpcomingSeries() } }

                _state.emit(
                    _state.value.copy(
                        recentRequests = recentRequests.await(),
                        trending = trending.await(),
                        popularMovies = popularMovies.await(),
                        popularSeries = popularSeries.await(),
                        upcomingSeries = upcomingSeries.await(),
                        isRefreshing = false,
                    )
                )
            }
        }
    }

    fun loadDetail(tmdbId: Int, mediaType: JellyseerrMediaType) {
        val existingDetail = _state.value.detail
        if (
            existingDetail.tmdbId == tmdbId &&
                existingDetail.mediaType == mediaType &&
                (
                    (mediaType == JellyseerrMediaType.MOVIE && existingDetail.movieDetails != null) ||
                        (mediaType == JellyseerrMediaType.TV && existingDetail.tvDetails != null)
                )
        ) {
            return
        }

        currentDetailJob?.cancel()
        currentDetailJob =
            viewModelScope.launch {
                val selectedMedia = _state.value.findMedia(tmdbId, mediaType)

                _state.emit(
                    _state.value.copy(
                        detail =
                            ExploreDetailState(
                                tmdbId = tmdbId,
                                mediaType = mediaType,
                                selectedMedia = selectedMedia,
                                isLoading = true,
                            )
                    )
                )

                try {
                    when (mediaType) {
                        JellyseerrMediaType.MOVIE -> {
                            val movieDetails = repository.getMovieDetails(tmdbId)
                            updateMovieDetails(movieDetails, isLoading = false)
                        }
                        JellyseerrMediaType.TV -> {
                            val tvDetails = repository.getTvDetails(tmdbId)
                            updateTvDetails(tvDetails, isLoading = false)
                        }
                    }
                } catch (_: CancellationException) {
                } catch (e: Exception) {
                    _state.emit(
                        _state.value.copy(
                            detail =
                                _state.value.detail.copy(
                                    isLoading = false,
                                    errorMessage =
                                        e.message ?: "Series details are unavailable right now.",
                                )
                        )
                    )
                }
            }
    }

    fun search(query: String) {
        currentSearchJob?.cancel()
        currentSearchJob =
            viewModelScope.launch {
                try {
                    if (query.isBlank()) {
                        _state.emit(_state.value.copy(search = ExploreSearchState.Idle))
                        return@launch
                    }

                    _state.emit(_state.value.copy(search = ExploreSearchState.Loading))
                    val items = repository.search(query)
                    _state.emit(
                        _state.value.copy(
                            search =
                                if (items.isEmpty()) {
                                    ExploreSearchState.Empty(query)
                                } else {
                                    ExploreSearchState.Success(items)
                                },
                        )
                    )
                } catch (_: CancellationException) {
                } catch (e: Exception) {
                    _state.emit(
                        _state.value.copy(
                            search =
                                ExploreSearchState.Error(
                                    e.message ?: "Search is unavailable right now."
                                )
                        )
                    )
                }
            }
    }

    fun requestMovie(media: JellyseerrMedia) {
        viewModelScope.launch {
            updateMovieRequestState(media, isSubmittingRequest = true, errorMessage = null)
            try {
                val updatedMedia = repository.requestMovie(media)
                updateMedia(updatedMedia)
                updateMovieRequestState(updatedMedia, isSubmittingRequest = false, errorMessage = null)
                refreshRecentRequests()
            } catch (e: Exception) {
                val message = e.message ?: "Request is unavailable right now."
                updateMovieRequestState(media, isSubmittingRequest = false, errorMessage = message)
                eventsChannel.send(ExploreEvent.ShowMessage(message))
            }
        }
    }

    fun toggleSeasonExpanded(seasonNumber: Int) {
        val detail = _state.value.detail
        if (detail.mediaType != JellyseerrMediaType.TV) {
            return
        }
        val tmdbId = detail.tmdbId ?: return

        val updatedExpandedSeason =
            if (detail.expandedSeasonNumber == seasonNumber) {
                null
            } else {
                seasonNumber
            }

        viewModelScope.launch {
            if (!isCurrentDetail(tmdbId, JellyseerrMediaType.TV)) {
                return@launch
            }
            _state.emit(
                _state.value.copy(
                    detail = _state.value.detail.copy(expandedSeasonNumber = updatedExpandedSeason)
                )
            )
        }

        if (updatedExpandedSeason == null || detail.seasonDetails.containsKey(seasonNumber)) {
            return
        }

        viewModelScope.launch {
            if (!isCurrentDetail(tmdbId, JellyseerrMediaType.TV)) {
                return@launch
            }
            _state.emit(
                _state.value.copy(
                    detail =
                        _state.value.detail.copy(
                            seasonDetails =
                                _state.value.detail.seasonDetails +
                                    (seasonNumber to ExploreSeasonDetailState.Loading)
                        )
                )
            )

            try {
                val seasonDetails = repository.getTvSeason(tmdbId, seasonNumber)
                if (!isCurrentDetail(tmdbId, JellyseerrMediaType.TV)) {
                    return@launch
                }
                _state.emit(
                    _state.value.copy(
                        detail =
                            _state.value.detail.copy(
                                seasonDetails =
                                    _state.value.detail.seasonDetails +
                                        (
                                            seasonNumber to
                                                ExploreSeasonDetailState.Success(seasonDetails)
                                            )
                            )
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!isCurrentDetail(tmdbId, JellyseerrMediaType.TV)) {
                    return@launch
                }
                _state.emit(
                    _state.value.copy(
                        detail =
                            _state.value.detail.copy(
                                seasonDetails =
                                    _state.value.detail.seasonDetails +
                                        (
                                            seasonNumber to
                                                ExploreSeasonDetailState.Error(
                                                    e.message
                                                        ?: "Episodes are unavailable right now."
                                                )
                                            )
                            )
                    )
                )
            }
        }
    }

    fun toggleSeasonSelected(seasonNumber: Int) {
        val detail = _state.value.detail
        val tvDetails = detail.tvDetails ?: return
        if (seasonNumber !in tvDetails.requestableSeasonNumbers) {
            return
        }

        val updatedSelection =
            if (seasonNumber in detail.selectedSeasonNumbers) {
                detail.selectedSeasonNumbers - seasonNumber
            } else {
                detail.selectedSeasonNumbers + seasonNumber
            }

        viewModelScope.launch {
            _state.emit(
                _state.value.copy(detail = _state.value.detail.copy(selectedSeasonNumbers = updatedSelection))
            )
        }
    }

    fun selectAllRequestableSeasons() {
        val tvDetails = _state.value.detail.tvDetails ?: return
        viewModelScope.launch {
            _state.emit(
                _state.value.copy(
                    detail =
                        _state.value.detail.copy(
                            selectedSeasonNumbers = tvDetails.requestableSeasonNumbers.toSet()
                        )
                )
            )
        }
    }

    fun clearSeasonSelection() {
        viewModelScope.launch {
            _state.emit(
                _state.value.copy(detail = _state.value.detail.copy(selectedSeasonNumbers = emptySet()))
            )
        }
    }

    fun submitSelectedTvSeasons() {
        val detail = _state.value.detail
        val tvDetails = detail.tvDetails ?: return
        val tmdbId = tvDetails.tmdbId
        val requestableSeasonNumbers = tvDetails.requestableSeasonNumbers
        val selectedSeasonNumbers =
            if (detail.selectedSeasonNumbers.isEmpty()) {
                requestableSeasonNumbers
            } else {
                detail.selectedSeasonNumbers
                    .filter { it in requestableSeasonNumbers }
                    .sorted()
            }

        if (selectedSeasonNumbers.isEmpty()) {
            viewModelScope.launch {
                eventsChannel.send(
                    ExploreEvent.ShowMessage("No requestable seasons are available.")
                )
            }
            return
        }

        viewModelScope.launch {
            if (!isCurrentDetail(tmdbId, JellyseerrMediaType.TV)) {
                return@launch
            }
            _state.emit(
                _state.value.copy(
                    detail = _state.value.detail.copy(isSubmittingRequest = true, errorMessage = null)
                )
            )

            try {
                val updatedDetails =
                    repository.requestTvSeasons(
                        tmdbId = tmdbId,
                        selectedSeasonNumbers = selectedSeasonNumbers,
                    )
                if (isCurrentDetail(tmdbId, JellyseerrMediaType.TV)) {
                    updateTvDetails(updatedDetails, isSubmittingRequest = false)
                }
                updateMedia(updatedDetails.toJellyseerrMedia())
                refreshRecentRequests()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!isCurrentDetail(tmdbId, JellyseerrMediaType.TV)) {
                    return@launch
                }
                _state.emit(
                    _state.value.copy(
                        detail =
                            _state.value.detail.copy(
                                isSubmittingRequest = false,
                                errorMessage = e.message ?: "Request is unavailable right now.",
                            )
                    )
                )
                eventsChannel.send(
                    ExploreEvent.ShowMessage(
                        e.message ?: "Request is unavailable right now."
                    )
                )
            }
        }
    }

    private fun isCurrentDetail(tmdbId: Int, mediaType: JellyseerrMediaType): Boolean {
        val currentDetail = _state.value.detail
        return currentDetail.tmdbId == tmdbId && currentDetail.mediaType == mediaType
    }

    fun openInJellyfin(media: JellyseerrMedia) {
        viewModelScope.launch {
            try {
                val item = repository.findJellyfinItem(media)
                eventsChannel.send(ExploreEvent.NavigateToItem(item))
            } catch (e: Exception) {
                eventsChannel.send(
                    ExploreEvent.ShowMessage(
                        e.message
                            ?: "Findroid could not open this title from Jellyfin."
                    )
                )
            }
        }
    }

    fun cancelRecentRequest(request: JellyseerrRecentRequest) {
        if (!request.canCancel) {
            return
        }

        viewModelScope.launch {
            try {
                repository.cancelRequest(request.requestId)
                refreshRecentRequests()
            } catch (e: Exception) {
                eventsChannel.send(
                    ExploreEvent.ShowMessage(
                        e.message ?: "Request could not be cancelled right now."
                    )
                )
            }
        }
    }

    private suspend fun loadSection(loader: suspend () -> List<JellyseerrMedia>): SectionState {
        return try {
            val items = loader()
            if (items.isEmpty()) {
                SectionState.Empty
            } else {
                SectionState.Success(items)
            }
        } catch (e: Exception) {
            SectionState.Error(e.message ?: "Failed to load section")
        }
    }

    private suspend fun loadRecentRequestsState(): RecentRequestsState {
        return try {
            val items = repository.getRecentRequests()
            if (items.isEmpty()) {
                RecentRequestsState.Hidden
            } else {
                RecentRequestsState.Success(items)
            }
        } catch (e: Exception) {
            RecentRequestsState.Error(e.message ?: "Failed to load recent requests.")
        }
    }

    private suspend fun refreshRecentRequests() {
        _state.emit(_state.value.copy(recentRequests = loadRecentRequestsState()))
    }

    private suspend fun updateMedia(updatedMedia: JellyseerrMedia) {
        val currentState = _state.value
        _state.emit(
            currentState.copy(
                recentRequests = currentState.recentRequests.replace(updatedMedia),
                trending = currentState.trending.replace(updatedMedia),
                popularMovies = currentState.popularMovies.replace(updatedMedia),
                popularSeries = currentState.popularSeries.replace(updatedMedia),
                upcomingSeries = currentState.upcomingSeries.replace(updatedMedia),
                search = currentState.search.replace(updatedMedia),
                detail = currentState.detail.replace(updatedMedia),
            )
        )
    }

    private suspend fun updateTvDetails(
        tvDetails: JellyseerrTvDetails,
        isLoading: Boolean? = null,
        isSubmittingRequest: Boolean? = null,
    ) {
        val currentDetail = _state.value.detail
        _state.emit(
            _state.value.copy(
                detail =
                    currentDetail.copy(
                        tmdbId = tvDetails.tmdbId,
                        mediaType = JellyseerrMediaType.TV,
                        selectedMedia = tvDetails.toJellyseerrMedia(),
                        movieDetails = null,
                        tvDetails = tvDetails,
                        isLoading = isLoading ?: currentDetail.isLoading,
                        isSubmittingRequest = isSubmittingRequest ?: currentDetail.isSubmittingRequest,
                        errorMessage = null,
                        selectedSeasonNumbers =
                            currentDetail.selectedSeasonNumbers
                                .intersect(tvDetails.requestableSeasonNumbers.toSet()),
                    )
            )
        )
    }

    private suspend fun updateMovieDetails(
        movieDetails: JellyseerrMovieDetails,
        isLoading: Boolean? = null,
    ) {
        val currentDetail = _state.value.detail
        _state.emit(
            _state.value.copy(
                detail =
                    currentDetail.copy(
                        tmdbId = movieDetails.tmdbId,
                        mediaType = JellyseerrMediaType.MOVIE,
                        selectedMedia = movieDetails.toJellyseerrMedia(),
                        movieDetails = movieDetails,
                        tvDetails = null,
                        isLoading = isLoading ?: currentDetail.isLoading,
                        isSubmittingRequest = false,
                        errorMessage = null,
                    )
            )
        )
    }

    private suspend fun updateMovieRequestState(
        media: JellyseerrMedia,
        isSubmittingRequest: Boolean,
        errorMessage: String?,
    ) {
        val currentDetail = _state.value.detail
        if (currentDetail.tmdbId != media.tmdbId || currentDetail.mediaType != JellyseerrMediaType.MOVIE) {
            return
        }

        _state.emit(
            _state.value.copy(
                detail =
                    currentDetail.copy(
                        isSubmittingRequest = isSubmittingRequest,
                        errorMessage = errorMessage,
                    )
            )
        )
    }

    private fun SectionState.replace(updatedMedia: JellyseerrMedia): SectionState {
        if (this !is SectionState.Success) {
            return this
        }

        return copy(
            items =
                items.map { item ->
                    if (
                        item.tmdbId == updatedMedia.tmdbId && item.mediaType == updatedMedia.mediaType
                    ) {
                        updatedMedia
                    } else {
                        item
                    }
                }
        )
    }

    private fun ExploreSearchState.replace(updatedMedia: JellyseerrMedia): ExploreSearchState {
        if (this !is ExploreSearchState.Success) {
            return this
        }

        return copy(
            items =
                items.map { item ->
                    if (
                        item.tmdbId == updatedMedia.tmdbId && item.mediaType == updatedMedia.mediaType
                    ) {
                        updatedMedia
                    } else {
                        item
                    }
                }
        )
    }

    private fun RecentRequestsState.replace(updatedMedia: JellyseerrMedia): RecentRequestsState {
        if (this !is RecentRequestsState.Success) {
            return this
        }

        return copy(
            items =
                items.map { item ->
                    if (
                        item.media.tmdbId == updatedMedia.tmdbId &&
                            item.media.mediaType == updatedMedia.mediaType
                    ) {
                        item.copy(media = updatedMedia)
                    } else {
                        item
                    }
                }
        )
    }

    private fun ExploreDetailState.replace(updatedMedia: JellyseerrMedia): ExploreDetailState {
        return if (
            selectedMedia?.tmdbId == updatedMedia.tmdbId &&
                selectedMedia.mediaType == updatedMedia.mediaType &&
                mediaType != JellyseerrMediaType.TV
        ) {
            copy(
                selectedMedia = updatedMedia,
                movieDetails =
                    movieDetails?.takeIf { it.tmdbId == updatedMedia.tmdbId }?.copy(
                        mediaStatus = updatedMedia.status,
                        jellyfinMediaId = updatedMedia.jellyfinMediaId,
                    ),
            )
        } else {
            this
        }
    }
}

sealed interface ExploreEvent {
    data class NavigateToItem(val item: FindroidItem) : ExploreEvent

    data class ShowMessage(val message: String) : ExploreEvent
}
