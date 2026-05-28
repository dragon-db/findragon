package dev.jdtech.jellyfin.jellyseerr

import android.content.SharedPreferences
import dev.jdtech.jellyfin.auth.SecureCredentialsStore
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.jellyseerr.api.JellyseerrApiException
import dev.jdtech.jellyfin.jellyseerr.api.JellyseerrApiService
import dev.jdtech.jellyfin.jellyseerr.api.dto.CreateIssueRequestDto
import dev.jdtech.jellyfin.jellyseerr.api.dto.DiscoverResponseDto
import dev.jdtech.jellyfin.jellyseerr.api.dto.IssueCommentDto
import dev.jdtech.jellyfin.jellyseerr.api.dto.IssueDto
import dev.jdtech.jellyfin.jellyseerr.api.dto.IssueResultsDto
import dev.jdtech.jellyfin.jellyseerr.api.dto.JellyseerrAuthRequestDto
import dev.jdtech.jellyfin.jellyseerr.api.dto.MediaDto
import dev.jdtech.jellyfin.jellyseerr.api.dto.MediaInfoDto
import dev.jdtech.jellyfin.jellyseerr.api.dto.RequestResponseDto
import dev.jdtech.jellyfin.jellyseerr.api.dto.SeasonWithEpisodesDto
import dev.jdtech.jellyfin.jellyseerr.api.dto.SeasonStatusDto
import dev.jdtech.jellyfin.jellyseerr.api.dto.TvDetailsDto
import dev.jdtech.jellyfin.jellyseerr.api.dto.TvEpisodeDto
import dev.jdtech.jellyfin.jellyseerr.api.dto.TvSeasonDto
import dev.jdtech.jellyfin.jellyseerr.model.CreateJellyseerrIssue
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrIssueType
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrMedia
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrMediaStatus
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrMediaType
import dev.jdtech.jellyfin.models.Server
import dev.jdtech.jellyfin.models.ServerWithAddressAndUser
import dev.jdtech.jellyfin.models.User
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class JellyseerrRepositoryImplTest {
    private val apiService = mockk<JellyseerrApiService>()
    private val serverDatabase = mockk<ServerDatabaseDao>()
    private val secureCredentialsStore = mockk<SecureCredentialsStore>()
    private val jellyfinRepository = mockk<JellyfinRepository>()
    private val sharedPreferences = FakeSharedPreferences()
    private val appPreferences = AppPreferences(sharedPreferences)

    private lateinit var repository: JellyseerrRepositoryImpl

    @Before
    fun setUp() {
        repository =
            JellyseerrRepositoryImpl(
                apiService = apiService,
                serverDatabase = serverDatabase,
                appPreferences = appPreferences,
                secureCredentialsStore = secureCredentialsStore,
                jellyfinRepository = jellyfinRepository,
            )

        sharedPreferences.edit().putString("pref_current_server", "server-id").apply()
        every { secureCredentialsStore.getUserPassword("server-id", "dragon") } returns "password"
        every { secureCredentialsStore.getJellyseerrSession("server-id", "dragon") } returns "session"
        every { secureCredentialsStore.clearJellyseerrSession(any(), any()) } just runs
        every { secureCredentialsStore.saveJellyseerrSession(any(), any(), any()) } just runs
        every { serverDatabase.getServerWithAddressAndUser("server-id") } returns serverWithUser()
    }

    @Test
    fun `getTrending retries transient network failures`() = runTest {
        coEvery { apiService.getTrending("session") } throws IOException("timeout") andThen
            DiscoverResponseDto(
                results =
                    listOf(
                        MediaDto(
                            id = 42,
                            mediaType = "movie",
                            title = "Retry Movie",
                            releaseDate = "2026-01-01",
                        )
                    )
            )

        val items = repository.getTrending()

        assertEquals(listOf("Retry Movie"), items.map { it.title })
        coVerify(exactly = 2) { apiService.getTrending("session") }
    }

    @Test
    fun `getTvSeason retries transient HTTP failures`() = runTest {
        coEvery { apiService.getTvSeason("session", 100, 2) } throws
            JellyseerrApiException(503, "Service unavailable") andThen
            seasonWithEpisodesDto(seasonNumber = 2)

        val season = repository.getTvSeason(100, 2)

        assertEquals(2, season.seasonNumber)
        coVerify(exactly = 2) { apiService.getTvSeason("session", 100, 2) }
    }

    @Test
    fun `requestMovie retries transient POST failures`() = runTest {
        coEvery { apiService.requestMovie("session", 42) } throws
            JellyseerrApiException(502, "Bad gateway") andThen
            RequestResponseDto(id = 9, status = 1, type = "movie")

        val updated = repository.requestMovie(movieMedia())

        assertEquals(JellyseerrMediaStatus.PENDING, updated.status)
        coVerify(exactly = 2) { apiService.requestMovie("session", 42) }
    }

    @Test
    fun `requestTvSeasons retries transient request and refresh failures`() = runTest {
        coEvery { apiService.requestTv("session", 100, listOf(1, 2)) } throws
            JellyseerrApiException(503, "Service unavailable") andThen
            RequestResponseDto(id = 11, status = 1, type = "tv")
        coEvery { apiService.getTvDetails("session", 100) } throws
            JellyseerrApiException(504, "Gateway timeout") andThen
            tvDetailsDto(status = 2)

        val details = repository.requestTvSeasons(100, listOf(2, 1))

        assertEquals(2, details.mediaStatus.code)
        coVerify(exactly = 2) { apiService.requestTv("session", 100, listOf(1, 2)) }
        coVerify(exactly = 2) { apiService.getTvDetails("session", 100) }
    }

    @Test
    fun `getTvSeason does not retry non retryable HTTP failures`() = runTest {
        coEvery { apiService.getTvSeason("session", 100, 2) } throws
            JellyseerrApiException(404, "Not found")

        runCatching { repository.getTvSeason(100, 2) }

        coVerify(exactly = 1) { apiService.getTvSeason("session", 100, 2) }
    }

    @Test
    fun `requestTvSeasons sends sorted distinct non special seasons and refreshes details after conflict`() =
        runTest {
            coEvery {
                apiService.requestTv("session", 100, listOf(1, 2, 3))
            } throws JellyseerrApiException(409, "Conflict")
            coEvery { apiService.getTvDetails("session", 100) } returns tvDetailsDto(status = 2)

            val details = repository.requestTvSeasons(100, listOf(2, 0, 3, 2, 1))

            assertEquals(100, details.tmdbId)
            assertEquals(2, details.mediaStatus.code)
            coVerify(exactly = 1) { apiService.requestTv("session", 100, listOf(1, 2, 3)) }
            coVerify(exactly = 1) { apiService.getTvDetails("session", 100) }
        }

    @Test
    fun `getTvDetails retries with reauthentication after unauthorized session`() = runTest {
        every { secureCredentialsStore.getJellyseerrSession("server-id", "dragon") } returns "stale"
        coEvery { apiService.getTvDetails("stale", 100) } throws JellyseerrApiException(401, "Unauthorized")
        coEvery {
            apiService.authenticate(
                JellyseerrAuthRequestDto(username = "dragon", password = "password")
            )
        } returns "fresh"
        coEvery { apiService.getTvDetails("fresh", 100) } returns tvDetailsDto(status = 5)

        val details = repository.getTvDetails(100)

        assertEquals(100, details.tmdbId)
        assertEquals(5, details.mediaStatus.code)
        verify(exactly = 1) { secureCredentialsStore.clearJellyseerrSession("server-id", "dragon") }
        verify(exactly = 1) {
            secureCredentialsStore.saveJellyseerrSession("server-id", "dragon", "fresh")
        }
        coVerify(exactly = 1) { apiService.getTvDetails("stale", 100) }
        coVerify(exactly = 1) { apiService.getTvDetails("fresh", 100) }
    }

    @Test
    fun `getOpenIssues filters open issues by seerr media id`() = runTest {
        coEvery { apiService.getOpenIssues("session", take = 100, skip = 0) } throws
            JellyseerrApiException(500, "Server error") andThen
            IssueResultsDto(
                pageInfo = dev.jdtech.jellyfin.jellyseerr.api.dto.PageInfoDto(results = 3),
                results =
                    listOf(
                        issueDto(id = 1, mediaId = 77, status = 1),
                        issueDto(id = 2, mediaId = 77, status = 2),
                        issueDto(id = 3, mediaId = 88, status = 1),
                    )
            )

        val issues = repository.getOpenIssues(77)

        assertEquals(listOf(1), issues.map { it.id })
        coVerify(exactly = 2) { apiService.getOpenIssues("session", take = 100, skip = 0) }
    }

    @Test
    fun `createIssue posts Seerr issue payload and maps response`() = runTest {
        val expectedPayload =
            CreateIssueRequestDto(
                mediaId = 77,
                issueType = 3,
                message = "Subtitles are offset.",
                problemSeason = 5,
                problemEpisode = 2,
            )
        coEvery { apiService.createIssue("session", expectedPayload) } returns
            issueDto(
                id = 9,
                mediaId = 77,
                status = 1,
                issueType = 3,
                problemSeason = 5,
                problemEpisode = 2,
                message = "Subtitles are offset.",
            )

        val issue =
            repository.createIssue(
                CreateJellyseerrIssue(
                    mediaId = 77,
                    type = JellyseerrIssueType.SUBTITLE,
                    message = "Subtitles are offset.",
                    problemSeason = 5,
                    problemEpisode = 2,
                )
            )

        assertEquals(9, issue.id)
        assertEquals(JellyseerrIssueType.SUBTITLE, issue.type)
        assertEquals("Subtitles are offset.", issue.message)
        coVerify(exactly = 1) { apiService.createIssue("session", expectedPayload) }
    }

    private fun movieMedia(): JellyseerrMedia {
        return JellyseerrMedia(
            tmdbId = 42,
            mediaType = JellyseerrMediaType.MOVIE,
            title = "Retry Movie",
            overview = "Overview",
            posterUrl = null,
            backdropUrl = null,
            releaseDate = "2026-01-01",
            voteAverage = null,
            status = JellyseerrMediaStatus.UNTRACKED,
            jellyfinMediaId = null,
        )
    }

    private fun seasonWithEpisodesDto(seasonNumber: Int): SeasonWithEpisodesDto {
        return SeasonWithEpisodesDto(
            id = seasonNumber,
            name = "Season $seasonNumber",
            seasonNumber = seasonNumber,
            episodes =
                listOf(
                    TvEpisodeDto(
                        id = 1,
                        name = "Episode 1",
                        episodeNumber = 1,
                        seasonNumber = seasonNumber,
                        showId = 100,
                    )
                ),
        )
    }

    private fun serverWithUser(): ServerWithAddressAndUser {
        val userId = UUID.randomUUID()
        return ServerWithAddressAndUser(
            server =
                Server(
                    id = "server-id",
                    name = "DragonDB",
                    currentServerAddressId = null,
                    currentUserId = userId,
                ),
            address = null,
            user = User(id = userId, name = "dragon", serverId = "server-id"),
        )
    }

    private fun tvDetailsDto(status: Int): TvDetailsDto {
        return TvDetailsDto(
            id = 100,
            name = "From",
            overview = "Overview",
            firstAirDate = "2022-02-20",
            seasons =
                listOf(
                    TvSeasonDto(id = 1, seasonNumber = 1, name = "Season 1", episodeCount = 10),
                    TvSeasonDto(id = 2, seasonNumber = 2, name = "Season 2", episodeCount = 10),
                    TvSeasonDto(id = 3, seasonNumber = 3, name = "Season 3", episodeCount = 10),
                ),
            mediaInfo =
                MediaInfoDto(
                    status = status,
                    jellyfinMediaId = "series-id",
                    seasons =
                        listOf(
                            SeasonStatusDto(id = 1, seasonNumber = 1, status = 5),
                            SeasonStatusDto(id = 2, seasonNumber = 2, status = status),
                        ),
                ),
        )
    }

    private fun issueDto(
        id: Int,
        mediaId: Int,
        status: Int,
        issueType: Int = 1,
        problemSeason: Int = 0,
        problemEpisode: Int = 0,
        message: String = "Issue",
    ): IssueDto {
        return IssueDto(
            id = id,
            issueType = issueType,
            status = status,
            problemSeason = problemSeason,
            problemEpisode = problemEpisode,
            media = MediaInfoDto(id = mediaId),
            comments = listOf(IssueCommentDto(message = message)),
        )
    }
}

private class FakeSharedPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values

    override fun getString(key: String?, defValue: String?): String? =
        values[key] as String? ?: defValue

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        @Suppress("UNCHECKED_CAST") (values[key] as MutableSet<String>?) ?: defValues

    override fun getInt(key: String?, defValue: Int): Int = values[key] as Int? ?: defValue

    override fun getLong(key: String?, defValue: Long): Long = values[key] as Long? ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float =
        values[key] as Float? ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        values[key] as Boolean? ?: defValue

    override fun contains(key: String?): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor(values)

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = Unit

    private class Editor(private val values: MutableMap<String, Any?>) : SharedPreferences.Editor {
        override fun putString(key: String?, value: String?): SharedPreferences.Editor =
            apply { values[key!!] = value }

        override fun putStringSet(
            key: String?,
            values: MutableSet<String>?
        ): SharedPreferences.Editor =
            apply { this.values[key!!] = values }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor =
            apply { values[key!!] = value }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor =
            apply { values[key!!] = value }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor =
            apply { values[key!!] = value }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor =
            apply { values[key!!] = value }

        override fun remove(key: String?): SharedPreferences.Editor =
            apply { values.remove(key) }

        override fun clear(): SharedPreferences.Editor =
            apply { values.clear() }

        override fun commit(): Boolean = true

        override fun apply() = Unit
    }
}
