package dev.jdtech.jellyfin.film.presentation.issue

import dev.jdtech.jellyfin.jellyseerr.JellyseerrRepository
import dev.jdtech.jellyfin.jellyseerr.model.CreateJellyseerrIssue
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrIssue
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrIssueStatus
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrIssueType
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrMediaStatus
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrMovieDetails
import dev.jdtech.jellyfin.models.FindroidImages
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.lang.reflect.Proxy
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IssueReporterViewModelTest {
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
    fun `a title without open issues skips reading every open issue`() = runTest(dispatcher) {
        val repository = FakeSeerr(openIssueCount = 0)
        val viewModel = IssueReporterViewModel(repository, unused())

        viewModel.loadForMovie(movie())
        advanceUntilIdle()

        assertEquals(0, repository.openIssueScans)
        assertEquals(77, viewModel.state.value.target?.seerrMediaId)
        assertEquals(emptyList<JellyseerrIssue>(), viewModel.state.value.openIssues)
    }

    @Test
    fun `a title with open issues, or an unknown count, still loads them`() = runTest(dispatcher) {
        for (count in listOf(2, null)) {
            val repository = FakeSeerr(openIssueCount = count, openIssues = listOf(issue(id = 1)))
            val viewModel = IssueReporterViewModel(repository, unused())

            viewModel.loadForMovie(movie())
            advanceUntilIdle()

            assertEquals(1, repository.openIssueScans)
            assertEquals(listOf(1), viewModel.state.value.openIssues.map { it.id })
        }
    }

    @Test
    fun `submitting shows the new issue without reading every open issue again`() =
        runTest(dispatcher) {
            val repository = FakeSeerr(openIssueCount = 1, openIssues = listOf(issue(id = 1)))
            val viewModel = IssueReporterViewModel(repository, unused())
            viewModel.loadForMovie(movie())
            advanceUntilIdle()

            viewModel.submit(
                IssueReportForm(
                    type = JellyseerrIssueType.VIDEO,
                    message = "No picture.",
                    seasonNumber = 0,
                    episodeNumber = 0,
                )
            )
            advanceUntilIdle()

            assertEquals(1, repository.openIssueScans)
            assertEquals(listOf(9, 1), viewModel.state.value.openIssues.map { it.id })
            assertEquals(false, viewModel.state.value.isSubmitting)
        }

    private fun movie() =
        FindroidMovie(
            id = UUID.randomUUID(),
            name = "Movie",
            originalTitle = null,
            overview = "",
            sources = emptyList(),
            played = false,
            favorite = false,
            canPlay = true,
            canDownload = false,
            runtimeTicks = 0L,
            playbackPositionTicks = 0L,
            premiereDate = null,
            people = emptyList(),
            genres = emptyList(),
            communityRating = null,
            officialRating = null,
            status = "Released",
            productionYear = 2025,
            endDate = null,
            trailer = null,
            images = FindroidImages(),
            chapters = emptyList(),
            trickplayInfo = null,
            tmdbId = 42,
        )

    private class FakeSeerr(
        private val openIssueCount: Int?,
        private val openIssues: List<JellyseerrIssue> = emptyList(),
    ) : JellyseerrRepository by unused() {
        var openIssueScans = 0
            private set

        override suspend fun getMovieDetails(tmdbId: Int): JellyseerrMovieDetails =
            JellyseerrMovieDetails(
                tmdbId = tmdbId,
                title = "Movie",
                originalTitle = null,
                overview = "",
                tagline = null,
                posterUrl = null,
                backdropUrl = null,
                releaseDate = null,
                runtimeMinutes = null,
                statusText = null,
                voteAverage = null,
                voteCount = null,
                originalLanguage = null,
                genres = emptyList(),
                productionCountries = emptyList(),
                spokenLanguages = emptyList(),
                mediaStatus = JellyseerrMediaStatus.AVAILABLE,
                jellyfinMediaId = null,
                seerrMediaId = 77,
                ratings = null,
                openIssueCount = openIssueCount,
            )

        override suspend fun getOpenIssues(mediaId: Int): List<JellyseerrIssue> {
            openIssueScans++
            return openIssues
        }

        override suspend fun createIssue(request: CreateJellyseerrIssue): JellyseerrIssue =
            issue(id = 9, message = request.message)
    }
}

private fun issue(id: Int, message: String = "Issue") =
    JellyseerrIssue(
        id = id,
        mediaId = 77,
        type = JellyseerrIssueType.VIDEO,
        status = JellyseerrIssueStatus.OPEN,
        problemSeason = 0,
        problemEpisode = 0,
        message = message,
        createdAt = null,
        updatedAt = null,
    )

/** Stands in for the parts of a big interface a test never calls; any call fails the test. */
private inline fun <reified T : Any> unused(): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
        throw UnsupportedOperationException("${T::class.java.simpleName}.${method.name}")
    } as T
