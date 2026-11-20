package dev.jdtech.jellyfin.film.presentation.explore

import dev.jdtech.jellyfin.jellyseerr.JellyseerrRepository
import dev.jdtech.jellyfin.jellyseerr.model.CreateJellyseerrIssue
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrIssue
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrMedia
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrMediaStatus
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrMediaType
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrMovieDetails
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrRatings
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrRecentRequest
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrRequestStatus
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrTvDetails
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrTvEpisode
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrTvSeason
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrTvSeasonDetails
import dev.jdtech.jellyfin.jellyseerr.model.toJellyseerrMedia
import dev.jdtech.jellyfin.models.FindroidItem
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExploreViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `toggleSeasonExpanded loads one season at a time and caches season details`() =
        runTest(dispatcher) {
            val repository = FakeJellyseerrRepository()
            val viewModel = ExploreViewModel(repository)

            viewModel.loadDetail(100, JellyseerrMediaType.TV)
            advanceUntilIdle()

            viewModel.toggleSeasonExpanded(1)
            advanceUntilIdle()
            assertEquals(1, viewModel.state.value.detail.expandedSeasonNumber)
            assertEquals(listOf(1), repository.loadedSeasonNumbers)

            viewModel.toggleSeasonExpanded(2)
            advanceUntilIdle()
            assertEquals(2, viewModel.state.value.detail.expandedSeasonNumber)
            assertEquals(listOf(1, 2), repository.loadedSeasonNumbers)

            viewModel.toggleSeasonExpanded(1)
            advanceUntilIdle()
            assertEquals(1, viewModel.state.value.detail.expandedSeasonNumber)
            assertEquals(listOf(1, 2), repository.loadedSeasonNumbers)
        }

    @Test
    fun `season selection supports toggle select all and clear`() = runTest(dispatcher) {
        val repository = FakeJellyseerrRepository()
        val viewModel = ExploreViewModel(repository)

        viewModel.loadDetail(100, JellyseerrMediaType.TV)
        advanceUntilIdle()

        viewModel.toggleSeasonSelected(1)
        advanceUntilIdle()
        assertEquals(setOf(1), viewModel.state.value.detail.selectedSeasonNumbers)

        viewModel.toggleSeasonSelected(2)
        advanceUntilIdle()
        assertEquals(setOf(1, 2), viewModel.state.value.detail.selectedSeasonNumbers)

        viewModel.selectAllRequestableSeasons()
        advanceUntilIdle()
        assertEquals(setOf(1, 2), viewModel.state.value.detail.selectedSeasonNumbers)

        viewModel.clearSeasonSelection()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.detail.selectedSeasonNumbers.isEmpty())
    }

    @Test
    fun `submitSelectedTvSeasons defaults to all requestable seasons and updates shelves`() =
        runTest(dispatcher) {
            val repository = FakeJellyseerrRepository()
            val viewModel = ExploreViewModel(repository)

            viewModel.loadData()
            advanceUntilIdle()
            viewModel.loadDetail(100, JellyseerrMediaType.TV)
            advanceUntilIdle()

            viewModel.submitSelectedTvSeasons()
            advanceUntilIdle()

            assertEquals(setOf(1, 2), repository.lastRequestedSeasons.toSet())
            val detail = viewModel.state.value.detail
            assertEquals(JellyseerrMediaStatus.PENDING, detail.tvDetails?.mediaStatus)
            val section = viewModel.state.value.popularSeries as SectionState.Success
            assertEquals(JellyseerrMediaStatus.PENDING, section.items.single().status)
        }

    @Test
    fun `loadData hides recent requests when repository returns none`() = runTest(dispatcher) {
        val repository = FakeJellyseerrRepository()
        val viewModel = ExploreViewModel(repository)

        viewModel.loadData()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.recentRequests is RecentRequestsState.Hidden)
    }

    @Test
    fun `cancelRecentRequest removes pending request from recent requests`() = runTest(dispatcher) {
        val repository = FakeJellyseerrRepository()
        repository.recentRequests =
            listOf(
                JellyseerrRecentRequest(
                    requestId = 7,
                    media = repository.currentMedia,
                    requestStatus = JellyseerrRequestStatus.PENDING_APPROVAL,
                    requestedAt = "2026-05-08T10:00:00.000Z",
                    updatedAt = "2026-05-08T10:00:00.000Z",
                    seasonNumbers = listOf(1),
                )
            )
        val viewModel = ExploreViewModel(repository)

        viewModel.loadData()
        advanceUntilIdle()
        val request = (viewModel.state.value.recentRequests as RecentRequestsState.Success).items.single()

        viewModel.cancelRecentRequest(request)
        advanceUntilIdle()

        assertEquals(7, repository.lastCancelledRequestId)
        assertTrue(viewModel.state.value.recentRequests is RecentRequestsState.Hidden)
    }

    @Test
    fun `requestMovie keeps detail submitting until repository returns`() = runTest(dispatcher) {
        val repository = FakeJellyseerrRepository()
        val requestedMovie = CompletableDeferred<JellyseerrMedia>()
        repository.requestMovieHandler = { requestedMovie.await() }
        val viewModel = ExploreViewModel(repository)

        viewModel.loadDetail(42, JellyseerrMediaType.MOVIE)
        advanceUntilIdle()
        val movie = requireNotNull(viewModel.state.value.detail.movieDetails).toJellyseerrMedia()

        viewModel.requestMovie(movie)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.detail.isSubmittingRequest)

        requestedMovie.complete(movie.copy(status = JellyseerrMediaStatus.PENDING))
        advanceUntilIdle()

        assertEquals(false, viewModel.state.value.detail.isSubmittingRequest)
        assertEquals(JellyseerrMediaStatus.PENDING, viewModel.state.value.detail.movieDetails?.mediaStatus)
    }

    @Test
    fun `loadData shows each row as soon as it arrives`() = runTest(dispatcher) {
        val repository = FakeJellyseerrRepository()
        val recentRequests = CompletableDeferred<List<JellyseerrRecentRequest>>()
        repository.recentRequestsHandler = { recentRequests.await() }
        repository.trendingHandler = { listOf(repository.currentMedia) }
        val viewModel = ExploreViewModel(repository)

        viewModel.loadData()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.trending is SectionState.Success)
        assertTrue(viewModel.state.value.popularSeries is SectionState.Success)
        assertTrue(viewModel.state.value.recentRequests is RecentRequestsState.Loading)

        recentRequests.complete(emptyList())
        advanceUntilIdle()

        assertTrue(viewModel.state.value.recentRequests is RecentRequestsState.Hidden)
    }

    @Test
    fun `loadData does not reload rows loaded in the last five minutes`() = runTest(dispatcher) {
        val repository = FakeJellyseerrRepository()
        val viewModel = ExploreViewModel(repository)
        var now = 0L
        viewModel.nanoTime = { now }

        viewModel.loadData()
        advanceUntilIdle()
        assertEquals(1, repository.trendingCalls)

        now += 1.minutes.inWholeNanoseconds
        viewModel.loadData()
        advanceUntilIdle()
        assertEquals(1, repository.trendingCalls)

        now += 5.minutes.inWholeNanoseconds
        viewModel.loadData()
        advanceUntilIdle()
        assertEquals(2, repository.trendingCalls)
    }

    @Test
    fun `refresh keeps loaded rows on screen while reloading and when reloading fails`() =
        runTest(dispatcher) {
            val repository = FakeJellyseerrRepository()
            repository.trendingHandler = { listOf(repository.currentMedia) }
            val viewModel = ExploreViewModel(repository)
            viewModel.loadData()
            advanceUntilIdle()

            val seerrAnswers = CompletableDeferred<Unit>()
            repository.trendingHandler = {
                seerrAnswers.await()
                error("Seerr is down")
            }
            viewModel.refresh()
            advanceUntilIdle()

            assertTrue(viewModel.state.value.isRefreshing)
            assertTrue(viewModel.state.value.trending is SectionState.Success)

            seerrAnswers.complete(Unit)
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isRefreshing)
            assertTrue(viewModel.state.value.trending is SectionState.Success)

            // A failed row means the next visit tries again instead of counting as fresh.
            viewModel.loadData()
            advanceUntilIdle()
            assertEquals(3, repository.trendingCalls)
        }

    @Test
    fun `loadData starts over when another account signs in`() = runTest(dispatcher) {
        val repository = FakeJellyseerrRepository()
        repository.trendingHandler = { listOf(repository.currentMedia) }
        val viewModel = ExploreViewModel(repository)
        viewModel.loadData()
        advanceUntilIdle()
        viewModel.loadDetail(42, JellyseerrMediaType.MOVIE)
        advanceUntilIdle()

        val seerrAnswers = CompletableDeferred<List<JellyseerrMedia>>()
        repository.trendingHandler = { seerrAnswers.await() }
        repository.accountKey = "server::someone-else"
        viewModel.loadData()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.trending is SectionState.Loading)
        assertNull(viewModel.state.value.detail.tmdbId)
        assertEquals(2, repository.trendingCalls)
    }

    @Test
    fun `loadDetail shows details before ratings arrive`() = runTest(dispatcher) {
        val repository = FakeJellyseerrRepository()
        val ratings = CompletableDeferred<JellyseerrRatings?>()
        repository.movieRatingsHandler = { ratings.await() }
        val viewModel = ExploreViewModel(repository)

        viewModel.loadDetail(42, JellyseerrMediaType.MOVIE)
        advanceUntilIdle()

        val detail = viewModel.state.value.detail
        assertFalse(detail.isLoading)
        assertEquals("Movie", detail.movieDetails?.title)
        assertEquals(74, detail.movieDetails?.ratings?.tmdbScore)
        assertNull(detail.movieDetails?.ratings?.rottenTomatoesCriticsScore)

        ratings.complete(
            JellyseerrRatings(
                rottenTomatoesCriticsScore = 91,
                rottenTomatoesAudienceScore = 88,
                tmdbScore = 74,
            )
        )
        advanceUntilIdle()

        assertEquals(91, viewModel.state.value.detail.movieDetails?.ratings?.rottenTomatoesCriticsScore)
    }

    @Test
    fun `loadDetail shows a title opened before at once and refreshes it quietly`() =
        runTest(dispatcher) {
            val repository = FakeJellyseerrRepository()
            var ratingsCalls = 0
            repository.movieRatingsHandler = {
                ratingsCalls++
                JellyseerrRatings(rottenTomatoesCriticsScore = 91, tmdbScore = 74)
            }
            val viewModel = ExploreViewModel(repository)
            viewModel.loadDetail(42, JellyseerrMediaType.MOVIE)
            advanceUntilIdle()
            viewModel.loadDetail(100, JellyseerrMediaType.TV)
            advanceUntilIdle()

            val freshDetails = CompletableDeferred<JellyseerrMovieDetails>()
            repository.movieDetailsHandler = { freshDetails.await() }
            viewModel.loadDetail(42, JellyseerrMediaType.MOVIE)
            advanceUntilIdle()

            // The earlier copy (with its Rotten Tomatoes score) is on screen while Seerr answers.
            var movie = viewModel.state.value.detail.movieDetails
            assertEquals(42, movie?.tmdbId)
            assertEquals(91, movie?.ratings?.rottenTomatoesCriticsScore)

            freshDetails.complete(
                repository
                    .movieDetails(42)
                    .copy(mediaStatus = JellyseerrMediaStatus.AVAILABLE, jellyfinMediaId = "movie-id")
            )
            advanceUntilIdle()

            movie = viewModel.state.value.detail.movieDetails
            assertEquals(JellyseerrMediaStatus.AVAILABLE, movie?.mediaStatus)
            assertEquals(91, movie?.ratings?.rottenTomatoesCriticsScore)
            assertEquals(1, ratingsCalls)
        }
}

private class FakeJellyseerrRepository : JellyseerrRepository {
    val loadedSeasonNumbers = mutableListOf<Int>()
    var lastRequestedSeasons: List<Int> = emptyList()
    var lastCancelledRequestId: Int? = null
    var recentRequests: List<JellyseerrRecentRequest> = emptyList()
    var requestMovieHandler: suspend (JellyseerrMedia) -> JellyseerrMedia = { it }
    var accountKey: String? = "server::dragon"
    var trendingCalls = 0
    var trendingHandler: suspend () -> List<JellyseerrMedia> = { emptyList() }
    var recentRequestsHandler: suspend () -> List<JellyseerrRecentRequest> = { recentRequests }
    var movieDetailsHandler: suspend (Int) -> JellyseerrMovieDetails = { movieDetails(it) }
    var movieRatingsHandler: suspend (Int) -> JellyseerrRatings? = { null }

    fun movieDetails(tmdbId: Int) =
        JellyseerrMovieDetails(
            tmdbId = tmdbId,
            title = "Movie",
            originalTitle = null,
            overview = "Overview",
            tagline = null,
            posterUrl = null,
            backdropUrl = null,
            releaseDate = "2025-01-01",
            runtimeMinutes = 120,
            statusText = "Released",
            voteAverage = 7.4,
            voteCount = 100,
            originalLanguage = "en",
            genres = emptyList(),
            productionCountries = emptyList(),
            spokenLanguages = emptyList(),
            mediaStatus = JellyseerrMediaStatus.UNKNOWN,
            jellyfinMediaId = null,
            ratings = JellyseerrRatings(tmdbScore = 74),
        )

    override suspend fun currentAccountKey(): String? = accountKey

    override suspend fun getMovieRatings(tmdbId: Int, tmdbVoteAverage: Double?): JellyseerrRatings? =
        movieRatingsHandler(tmdbId)

    override suspend fun getTvRatings(tmdbId: Int, tmdbVoteAverage: Double?): JellyseerrRatings? = null

    var currentDetails =
        JellyseerrTvDetails(
            tmdbId = 100,
            title = "From",
            overview = "Overview",
            posterUrl = null,
            backdropUrl = null,
            firstAirDate = "2022-02-20",
            lastAirDate = null,
            statusText = "Returning Series",
            voteAverage = 8.1,
            originalLanguage = "en",
            networks = listOf("MGM+"),
            productionCountries = emptyList(),
            nextAirDate = "2026-05-10",
            mediaStatus = JellyseerrMediaStatus.UNKNOWN,
            jellyfinMediaId = null,
            ratings = null,
            seasons =
                listOf(
                    JellyseerrTvSeason(
                        seasonNumber = 2,
                        name = "Season 2",
                        overview = "",
                        airDate = "2024-01-01",
                        episodeCount = 10,
                        posterUrl = null,
                        status = JellyseerrMediaStatus.UNKNOWN,
                    ),
                    JellyseerrTvSeason(
                        seasonNumber = 1,
                        name = "Season 1",
                        overview = "",
                        airDate = "2023-01-01",
                        episodeCount = 10,
                        posterUrl = null,
                        status = JellyseerrMediaStatus.UNTRACKED,
                    ),
                    JellyseerrTvSeason(
                        seasonNumber = 0,
                        name = "Specials",
                        overview = "",
                        airDate = null,
                        episodeCount = 2,
                        posterUrl = null,
                        status = JellyseerrMediaStatus.UNTRACKED,
                    ),
                ),
        )

    val currentMedia: JellyseerrMedia
        get() = currentDetails.toJellyseerrMedia()

    override suspend fun ensureAuthenticated() = Unit

    override suspend fun search(query: String): List<JellyseerrMedia> = emptyList()

    override suspend fun getTrending(): List<JellyseerrMedia> {
        trendingCalls++
        return trendingHandler()
    }

    override suspend fun getPopularMovies(): List<JellyseerrMedia> = emptyList()

    override suspend fun getPopularSeries(): List<JellyseerrMedia> = listOf(currentDetails.toJellyseerrMedia())

    override suspend fun getUpcomingSeries(): List<JellyseerrMedia> = emptyList()

    override suspend fun getRecentRequests(): List<JellyseerrRecentRequest> = recentRequestsHandler()

    override suspend fun getOpenIssues(mediaId: Int): List<JellyseerrIssue> = emptyList()

    override suspend fun getIssue(issueId: Int): JellyseerrIssue {
        error("Not used in test")
    }

    override suspend fun getMovieDetails(tmdbId: Int): JellyseerrMovieDetails =
        movieDetailsHandler(tmdbId)

    override suspend fun getTvDetails(tmdbId: Int): JellyseerrTvDetails = currentDetails

    override suspend fun getTvSeason(tmdbId: Int, seasonNumber: Int): JellyseerrTvSeasonDetails {
        loadedSeasonNumbers += seasonNumber
        return JellyseerrTvSeasonDetails(
            seasonNumber = seasonNumber,
            name = "Season $seasonNumber",
            overview = "",
            airDate = "2024-01-01",
            posterUrl = null,
            episodes =
                listOf(
                    JellyseerrTvEpisode(
                        episodeNumber = 1,
                        name = "Episode 1",
                        overview = "Overview",
                        airDate = "2024-01-01",
                    )
                ),
        )
    }

    override suspend fun requestMovie(media: JellyseerrMedia): JellyseerrMedia = requestMovieHandler(media)

    override suspend fun requestTvSeasons(
        tmdbId: Int,
        selectedSeasonNumbers: List<Int>,
    ): JellyseerrTvDetails {
        lastRequestedSeasons = selectedSeasonNumbers
        currentDetails =
            currentDetails.copy(
                mediaStatus = JellyseerrMediaStatus.PENDING,
                seasons =
                    currentDetails.seasons.map { season ->
                        if (season.seasonNumber in selectedSeasonNumbers) {
                            season.copy(status = JellyseerrMediaStatus.PENDING)
                        } else {
                            season
                        }
                    },
        )
        return currentDetails
    }

    override suspend fun cancelRequest(requestId: Int) {
        lastCancelledRequestId = requestId
        recentRequests = recentRequests.filterNot { it.requestId == requestId }
    }

    override suspend fun createIssue(request: CreateJellyseerrIssue): JellyseerrIssue {
        error("Not used in test")
    }

    override suspend fun findJellyfinItem(media: JellyseerrMedia): FindroidItem {
        error("Not used in test")
    }
}
