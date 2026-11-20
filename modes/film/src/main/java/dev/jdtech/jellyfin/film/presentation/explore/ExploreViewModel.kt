package dev.jdtech.jellyfin.film.presentation.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.jellyseerr.JellyseerrLruCache
import dev.jdtech.jellyfin.jellyseerr.JellyseerrMissingCredentialsException
import dev.jdtech.jellyfin.jellyseerr.JellyseerrRepository
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrMedia
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrMediaType
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrMovieDetails
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrRatings
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrRecentRequest
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrTvDetails
import dev.jdtech.jellyfin.models.FindroidItem
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ExploreViewModel @Inject constructor(private val repository: JellyseerrRepository) : ViewModel() {
    private val _state = MutableStateFlow(ExploreUiState())
    val state = _state.asStateFlow()

    private val eventsChannel = Channel<ExploreEvent>()
    val events = eventsChannel.receiveAsFlow()
    private var currentSearchJob: Job? = null
    private var currentDetailJob: Job? = null
    private var currentLoadJob: Job? = null

    /** The account the loaded rows belong to (see [forgetOtherAccount]). */
    private var loadedAccountKey: String? = null
    private var accountKnown = false

    /** When ([nanoTime]) every Explore row last loaded without an error. */
    private var lastFullLoadNanos: Long? = null

    /** Titles opened recently: shown at once when opened again, then refreshed quietly. */
    private val movieDetailCache = JellyseerrLruCache<Int, JellyseerrMovieDetails>(DETAIL_CACHE_SIZE)
    private val tvDetailCache = JellyseerrLruCache<Int, JellyseerrTvDetails>(DETAIL_CACHE_SIZE)

    /** Clock for the freshness check. Tests replace it. */
    internal var nanoTime: () -> Long = System::nanoTime

    init {
        // Whatever a detail page last showed (details, ratings, a new request status) is where the
        // next visit to that title starts.
        viewModelScope.launch {
            state
                .map { it.detail }
                .distinctUntilChanged()
                .collect { detail ->
                    detail.movieDetails?.let { movieDetailCache[it.tmdbId] = it }
                    detail.tvDetails?.let { tvDetailCache[it.tmdbId] = it }
                }
        }
    }

    /**
     * Called whenever Explore comes on screen. Loads only when nothing is loaded yet, the account
     * changed, a row failed last time, or the rows are older than [EXPLORE_FRESH_FOR_NANOS]. Rows
     * already on screen stay there while it reloads.
     */
    fun loadData() {
        if (currentLoadJob?.isActive == true) {
            return
        }

        currentLoadJob =
            viewModelScope.launch {
                forgetOtherAccount()
                if (isFresh()) {
                    return@launch
                }
                load(userInitiated = false)
            }
    }

    /** Pull to refresh and retry: reloads every row now, keeping rows on screen until replaced. */
    fun refresh() {
        currentLoadJob?.cancel()
        currentLoadJob =
            viewModelScope.launch {
                forgetOtherAccount()
                load(userInitiated = true)
            }
    }

    private fun isFresh(): Boolean {
        val loadedAt = lastFullLoadNanos ?: return false
        return nanoTime() - loadedAt < EXPLORE_FRESH_FOR_NANOS
    }

    /**
     * Explore outlives sign-outs (it belongs to the activity), so rows, search and cached titles
     * of the previous account are dropped as soon as another account is signed in.
     */
    private suspend fun forgetOtherAccount() {
        val accountKey =
            try {
                repository.currentAccountKey()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        if (accountKnown && accountKey == loadedAccountKey) {
            return
        }

        if (accountKnown) {
            lastFullLoadNanos = null
            movieDetailCache.clear()
            tvDetailCache.clear()
            currentSearchJob?.cancel()
            currentDetailJob?.cancel()
            _state.value = ExploreUiState()
        }
        accountKnown = true
        loadedAccountKey = accountKey
    }

    private suspend fun load(userInitiated: Boolean) {
        _state.update { it.startLoading(isRefreshing = userInitiated) }

        try {
            repository.ensureAuthenticated()
        } catch (e: CancellationException) {
            throw e
        } catch (_: JellyseerrMissingCredentialsException) {
            lastFullLoadNanos = null
            _state.value =
                ExploreUiState(
                    recentRequests = RecentRequestsState.Hidden,
                    trending = SectionState.Empty,
                    popularMovies = SectionState.Empty,
                    popularSeries = SectionState.Empty,
                    upcomingSeries = SectionState.Empty,
                    authRequired = true,
                )
            return
        } catch (e: Exception) {
            lastFullLoadNanos = null
            if (_state.value.hasLoadedRows) {
                // Keep what is on screen; the next visit tries again.
                _state.update { it.failLoadingRows(e.message ?: "Failed to load section") }
                if (userInitiated) {
                    showMessage(e.message ?: "Explore could not refresh right now.")
                }
            } else {
                _state.value =
                    ExploreUiState(
                        recentRequests = RecentRequestsState.Hidden,
                        trending = SectionState.Empty,
                        popularMovies = SectionState.Empty,
                        popularSeries = SectionState.Empty,
                        upcomingSeries = SectionState.Empty,
                        authError = true,
                        error = e,
                    )
            }
            return
        }

        // Every row loads at the same time and shows up as soon as it arrives.
        val allLoaded =
            coroutineScope {
                listOf(
                        async { loadRecentRequests() },
                        async {
                            loadShelf(repository::getTrending, { it.trending }) {
                                copy(trending = it)
                            }
                        },
                        async {
                            loadShelf(repository::getPopularMovies, { it.popularMovies }) {
                                copy(popularMovies = it)
                            }
                        },
                        async {
                            loadShelf(repository::getPopularSeries, { it.popularSeries }) {
                                copy(popularSeries = it)
                            }
                        },
                        async {
                            loadShelf(repository::getUpcomingSeries, { it.upcomingSeries }) {
                                copy(upcomingSeries = it)
                            }
                        },
                    )
                    .awaitAll()
                    .all { it }
            }

        _state.update { it.copy(isRefreshing = false) }
        lastFullLoadNanos = if (allLoaded) nanoTime() else null
    }

    /** Loads one shelf and shows it as soon as it arrives. Returns false when it failed. */
    private suspend fun loadShelf(
        loader: suspend () -> List<JellyseerrMedia>,
        current: (ExploreUiState) -> SectionState,
        show: ExploreUiState.(SectionState) -> ExploreUiState,
    ): Boolean {
        val result = attempt { loader() }
        _state.update { state ->
            val section =
                result.fold(
                    onSuccess = { items ->
                        if (items.isEmpty()) SectionState.Empty else SectionState.Success(items)
                    },
                    onFailure = { e ->
                        // A failed refresh keeps the rows already on screen.
                        current(state).takeIf { it.isShown }
                            ?: SectionState.Error(e.message ?: "Failed to load section")
                    },
                )
            state.show(section)
        }
        return result.isSuccess
    }

    /** A rendezvous channel waits for a listener, so never make the caller wait for one. */
    private fun showMessage(message: String) {
        viewModelScope.launch { eventsChannel.send(ExploreEvent.ShowMessage(message)) }
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
                // A title opened recently shows at once and is refreshed in the background.
                val cachedMovie =
                    if (mediaType == JellyseerrMediaType.MOVIE) movieDetailCache[tmdbId] else null
                val cachedTv =
                    if (mediaType == JellyseerrMediaType.TV) tvDetailCache[tmdbId] else null
                val hasCachedDetails = cachedMovie != null || cachedTv != null

                _state.update {
                    it.copy(
                        detail =
                            ExploreDetailState(
                                tmdbId = tmdbId,
                                mediaType = mediaType,
                                selectedMedia =
                                    cachedMovie?.toJellyseerrMedia()
                                        ?: cachedTv?.toJellyseerrMedia()
                                        ?: it.findMedia(tmdbId, mediaType),
                                movieDetails = cachedMovie,
                                tvDetails = cachedTv,
                                isLoading = true,
                            )
                    )
                }

                try {
                    // Details go on screen first; ratings come from an outside service and are
                    // filled in afterwards.
                    when (mediaType) {
                        JellyseerrMediaType.MOVIE -> {
                            updateMovieDetails(repository.getMovieDetails(tmdbId), isLoading = false)
                            loadMovieRatings(tmdbId)
                        }
                        JellyseerrMediaType.TV -> {
                            updateTvDetails(repository.getTvDetails(tmdbId), isLoading = false)
                            loadTvRatings(tmdbId)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.update {
                        it.copy(
                            detail =
                                it.detail.copy(
                                    isLoading = false,
                                    // The cached copy stays on screen when the refresh fails.
                                    errorMessage =
                                        if (hasCachedDetails) {
                                            null
                                        } else {
                                            e.message ?: "Series details are unavailable right now."
                                        },
                                )
                        )
                    }
                }
            }
    }

    private suspend fun loadMovieRatings(tmdbId: Int) {
        val details = _state.value.detail.movieDetails?.takeIf { it.tmdbId == tmdbId } ?: return
        if (details.ratings.hasRottenTomatoes) {
            return
        }

        val ratings =
            attempt { repository.getMovieRatings(tmdbId, details.voteAverage) }.getOrNull()
                ?: return
        _state.update { state ->
            val current =
                state.detail.movieDetails?.takeIf { it.tmdbId == tmdbId } ?: return@update state
            state.copy(detail = state.detail.copy(movieDetails = current.copy(ratings = ratings)))
        }
    }

    private suspend fun loadTvRatings(tmdbId: Int) {
        val details = _state.value.detail.tvDetails?.takeIf { it.tmdbId == tmdbId } ?: return
        if (details.ratings.hasRottenTomatoes) {
            return
        }

        val ratings =
            attempt { repository.getTvRatings(tmdbId, details.voteAverage) }.getOrNull() ?: return
        _state.update { state ->
            val current =
                state.detail.tvDetails?.takeIf { it.tmdbId == tmdbId } ?: return@update state
            state.copy(detail = state.detail.copy(tvDetails = current.copy(ratings = ratings)))
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
                            ?: "Findragon could not open this title from Jellyfin."
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                eventsChannel.send(
                    ExploreEvent.ShowMessage(
                        e.message ?: "Request could not be cancelled right now."
                    )
                )
            }
        }
    }

    /** Loads recent requests and shows them as soon as they arrive. Returns false when it failed. */
    private suspend fun loadRecentRequests(): Boolean {
        val result = attempt { repository.getRecentRequests() }
        _state.update { state ->
            val recentRequests =
                result.fold(
                    onSuccess = { items ->
                        if (items.isEmpty()) {
                            RecentRequestsState.Hidden
                        } else {
                            RecentRequestsState.Success(items)
                        }
                    },
                    onFailure = { e ->
                        // A failed refresh keeps the requests already on screen.
                        state.recentRequests.takeIf { it is RecentRequestsState.Success }
                            ?: RecentRequestsState.Error(
                                e.message ?: "Failed to load recent requests."
                            )
                    },
                )
            state.copy(recentRequests = recentRequests)
        }
        return result.isSuccess
    }

    private suspend fun refreshRecentRequests() {
        loadRecentRequests()
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
                        tvDetails = tvDetails.keepingRatingsOf(currentDetail.tvDetails),
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
                        movieDetails = movieDetails.keepingRatingsOf(currentDetail.movieDetails),
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

/** Explore rows younger than this are shown as they are when the user comes back to Explore. */
private val EXPLORE_FRESH_FOR_NANOS = 5.minutes.inWholeNanoseconds

private const val DETAIL_CACHE_SIZE = 30

/** Like [runCatching], but lets coroutine cancellation through. */
private inline fun <T> attempt(block: () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/** Loaded content (or a loaded empty row) as opposed to a skeleton or an error. */
private val SectionState.isShown: Boolean
    get() = this is SectionState.Success || this is SectionState.Empty

private fun SectionState.loadingUnlessShown(): SectionState =
    if (isShown) this else SectionState.Loading

private fun RecentRequestsState.loadingUnlessShown(): RecentRequestsState =
    if (this is RecentRequestsState.Success || this is RecentRequestsState.Hidden) {
        this
    } else {
        RecentRequestsState.Loading
    }

private val ExploreUiState.hasLoadedRows: Boolean
    get() =
        recentRequests is RecentRequestsState.Success ||
            listOf(trending, popularMovies, popularSeries, upcomingSeries).any {
                it is SectionState.Success
            }

/** Rows already on screen stay; the others show their loading skeleton. */
private fun ExploreUiState.startLoading(isRefreshing: Boolean): ExploreUiState {
    // After a sign-in error the rows were never shown, so they all start from a skeleton.
    val keepRows = !authRequired && !authError
    return copy(
        recentRequests =
            if (keepRows) recentRequests.loadingUnlessShown() else RecentRequestsState.Loading,
        trending = if (keepRows) trending.loadingUnlessShown() else SectionState.Loading,
        popularMovies = if (keepRows) popularMovies.loadingUnlessShown() else SectionState.Loading,
        popularSeries = if (keepRows) popularSeries.loadingUnlessShown() else SectionState.Loading,
        upcomingSeries =
            if (keepRows) upcomingSeries.loadingUnlessShown() else SectionState.Loading,
        authRequired = false,
        authError = false,
        error = null,
        isRefreshing = isRefreshing,
    )
}

/** Rows still loading when the load gave up show [message]; loaded rows stay. */
private fun ExploreUiState.failLoadingRows(message: String): ExploreUiState {
    fun SectionState.failIfLoading() =
        if (this is SectionState.Loading) SectionState.Error(message) else this

    return copy(
        recentRequests =
            recentRequests.takeUnless { it is RecentRequestsState.Loading }
                ?: RecentRequestsState.Hidden,
        trending = trending.failIfLoading(),
        popularMovies = popularMovies.failIfLoading(),
        popularSeries = popularSeries.failIfLoading(),
        upcomingSeries = upcomingSeries.failIfLoading(),
        isRefreshing = false,
    )
}

private val JellyseerrRatings?.hasRottenTomatoes: Boolean
    get() = this?.rottenTomatoesCriticsScore != null || this?.rottenTomatoesAudienceScore != null

/** Fresh details only carry the TMDB score; keep Rotten Tomatoes scores already loaded. */
private fun JellyseerrMovieDetails.keepingRatingsOf(
    previous: JellyseerrMovieDetails?
): JellyseerrMovieDetails =
    if (previous?.tmdbId == tmdbId && previous.ratings.hasRottenTomatoes && !ratings.hasRottenTomatoes) {
        copy(ratings = previous.ratings)
    } else {
        this
    }

private fun JellyseerrTvDetails.keepingRatingsOf(previous: JellyseerrTvDetails?): JellyseerrTvDetails =
    if (previous?.tmdbId == tmdbId && previous.ratings.hasRottenTomatoes && !ratings.hasRottenTomatoes) {
        copy(ratings = previous.ratings)
    } else {
        this
    }
