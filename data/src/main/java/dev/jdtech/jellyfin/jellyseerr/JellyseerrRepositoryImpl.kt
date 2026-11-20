package dev.jdtech.jellyfin.jellyseerr

import dev.jdtech.jellyfin.auth.SecureCredentialsStore
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.jellyseerr.api.JellyseerrApiException
import dev.jdtech.jellyfin.jellyseerr.api.JellyseerrApiService
import dev.jdtech.jellyfin.jellyseerr.api.dto.DiscoverResponseDto
import dev.jdtech.jellyfin.jellyseerr.api.dto.CreateIssueRequestDto
import dev.jdtech.jellyfin.jellyseerr.api.dto.JellyseerrAuthRequestDto
import dev.jdtech.jellyfin.jellyseerr.api.dto.MediaRequestDto
import dev.jdtech.jellyfin.jellyseerr.model.CreateJellyseerrIssue
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrIssue
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrIssueStatus
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrMedia
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrMediaStatus
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrMediaType
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrMovieDetails
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrRatings
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrRecentRequest
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrRequestStatus
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrTvDetails
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrTvSeasonDetails
import dev.jdtech.jellyfin.jellyseerr.model.toJellyseerrMovieDetails
import dev.jdtech.jellyfin.jellyseerr.model.toJellyseerrRatings
import dev.jdtech.jellyfin.jellyseerr.model.toJellyseerrIssue
import dev.jdtech.jellyfin.jellyseerr.model.toJellyseerrMedia
import dev.jdtech.jellyfin.jellyseerr.model.toJellyseerrTvDetails
import dev.jdtech.jellyfin.jellyseerr.model.toJellyseerrTvSeasonDetails
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.io.IOException
import java.io.InterruptedIOException
import java.net.UnknownHostException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber

class JellyseerrRepositoryImpl(
    private val apiService: JellyseerrApiService,
    private val serverDatabase: ServerDatabaseDao,
    private val appPreferences: AppPreferences,
    private val secureCredentialsStore: SecureCredentialsStore,
    private val jellyfinRepository: JellyfinRepository,
) : JellyseerrRepository {
    /** Only one Seerr sign-in at a time, so parallel requests with an expired cookie share it. */
    private val authMutex = Mutex()

    /**
     * Title, poster and overview of titles seen in details or recent requests. Recent requests
     * only carry ids and status, so without this every Explore load fetched full details for each
     * request. Status always comes from the fresh request, never from here.
     */
    private val mediaMetadataCache =
        JellyseerrLruCache<MediaKey, JellyseerrMedia>(MEDIA_METADATA_CACHE_SIZE)

    /** Keeps recent-request lookups from taking every connection the shelves need. */
    private val recentRequestLookups = Semaphore(RECENT_REQUEST_LOOKUP_CONCURRENCY)

    override suspend fun ensureAuthenticated() {
        runRequest { currentSessionCookie(currentContext()) }
    }

    override suspend fun currentAccountKey(): String? {
        val serverId = appPreferences.getValue(appPreferences.currentServer) ?: return null
        val username = serverDatabase.getServerWithAddressAndUser(serverId)?.user?.name ?: return null
        return "$serverId::$username"
    }

    override suspend fun search(query: String): List<JellyseerrMedia> {
        if (query.isBlank()) {
            return emptyList()
        }

        return getSection { apiService.search(it, query) }
    }

    override suspend fun getTrending(): List<JellyseerrMedia> {
        return getSection { apiService.getTrending(it) }
    }

    override suspend fun getPopularMovies(): List<JellyseerrMedia> {
        return getSection { apiService.getPopularMovies(it) }
    }

    override suspend fun getPopularSeries(): List<JellyseerrMedia> {
        return getSection { apiService.getPopularSeries(it) }
    }

    override suspend fun getUpcomingSeries(): List<JellyseerrMedia> {
        return getSection { apiService.getUpcomingSeries(it) }
    }

    override suspend fun getRecentRequests(): List<JellyseerrRecentRequest> {
        return runRequest {
            withSession { sessionCookie ->
                val requests =
                    retryJellyseerrRequest("recent requests") {
                        apiService.getRecentRequests(sessionCookie).results
                    }
                val metadata =
                    loadRecentRequestMetadata(
                        sessionCookie = sessionCookie,
                        keys = requests.mapNotNull { it.mediaKey() }.distinct(),
                    )

                requests.mapNotNull { request -> request.toRecentRequest(metadata) }
            }
        }
    }

    override suspend fun getOpenIssues(mediaId: Int): List<JellyseerrIssue> {
        return runRequest {
            withSession { sessionCookie ->
                getAllOpenIssues(sessionCookie)
                    .map { it.toJellyseerrIssue() }
                    .filter { it.mediaId == mediaId && it.status == JellyseerrIssueStatus.OPEN }
            }
        }
    }

    override suspend fun getIssue(issueId: Int): JellyseerrIssue {
        return runRequest {
            withSession { sessionCookie ->
                retryJellyseerrRequest("issue $issueId") {
                    apiService.getIssue(sessionCookie, issueId)
                }.toJellyseerrIssue()
            }
        }
    }

    override suspend fun getMovieDetails(tmdbId: Int): JellyseerrMovieDetails {
        return runRequest {
            withSession { sessionCookie ->
                retryJellyseerrRequest("movie details $tmdbId") {
                    apiService.getMovieDetails(sessionCookie, tmdbId)
                }
                    .toJellyseerrMovieDetails()
            }
        }
            .also { rememberMetadata(it.toJellyseerrMedia()) }
    }

    override suspend fun getTvDetails(tmdbId: Int): JellyseerrTvDetails {
        return runRequest {
            withSession { sessionCookie ->
                retryJellyseerrRequest("tv details $tmdbId") {
                    apiService.getTvDetails(sessionCookie, tmdbId)
                }
                    .toJellyseerrTvDetails()
            }
        }
            .also { rememberMetadata(it.toJellyseerrMedia()) }
    }

    override suspend fun getMovieRatings(tmdbId: Int, tmdbVoteAverage: Double?): JellyseerrRatings? {
        return getRatings("movie ratings $tmdbId") { sessionCookie ->
            apiService.getMovieRatingsCombined(sessionCookie, tmdbId)
                .toJellyseerrRatings(tmdbVoteAverage)
        }
    }

    override suspend fun getTvRatings(tmdbId: Int, tmdbVoteAverage: Double?): JellyseerrRatings? {
        return getRatings("tv ratings $tmdbId") { sessionCookie ->
            apiService.getTvRatings(sessionCookie, tmdbId).toJellyseerrRatings(tmdbVoteAverage)
        }
    }

    override suspend fun getTvSeason(tmdbId: Int, seasonNumber: Int): JellyseerrTvSeasonDetails {
        return runRequest {
            withSession { sessionCookie ->
                retryJellyseerrRequest("tv season $tmdbId/$seasonNumber") {
                    apiService.getTvSeason(sessionCookie, tmdbId, seasonNumber)
                }
                    .toJellyseerrTvSeasonDetails()
            }
        }
    }

    override suspend fun requestMovie(media: JellyseerrMedia): JellyseerrMedia {
        return runRequest {
            try {
                withSession { sessionCookie ->
                    retryJellyseerrRequest("movie request ${media.tmdbId}") {
                        apiService.requestMovie(sessionCookie, media.tmdbId)
                    }
                }
            } catch (e: JellyseerrApiException) {
                if (e.code != 409) {
                    throw e
                }
            }

            media.copy(status = JellyseerrMediaStatus.PENDING)
        }
    }

    override suspend fun requestTvSeasons(
        tmdbId: Int,
        selectedSeasonNumbers: List<Int>,
    ): JellyseerrTvDetails {
        return runRequest {
            val sanitizedSeasonNumbers =
                selectedSeasonNumbers
                    .filter { it > 0 }
                    .distinct()
                    .sorted()

            require(sanitizedSeasonNumbers.isNotEmpty()) {
                "At least one season must be selected."
            }

            try {
                withSession { sessionCookie ->
                    retryJellyseerrRequest("tv request $tmdbId") {
                        apiService.requestTv(sessionCookie, tmdbId, sanitizedSeasonNumbers)
                    }
                }
            } catch (e: JellyseerrApiException) {
                if (e.code != 409) {
                    throw e
                }
            }

            withSession { sessionCookie ->
                retryJellyseerrRequest("tv details refresh $tmdbId") {
                    apiService.getTvDetails(sessionCookie, tmdbId)
                }.toJellyseerrTvDetails()
            }
        }
    }

    override suspend fun cancelRequest(requestId: Int) {
        runRequest {
            withSession { sessionCookie ->
                retryJellyseerrRequest("cancel request $requestId") {
                    apiService.cancelRequest(sessionCookie, requestId)
                }
            }
        }
    }

    override suspend fun createIssue(request: CreateJellyseerrIssue): JellyseerrIssue {
        return runRequest {
            withSession { sessionCookie ->
                apiService
                    .createIssue(
                        sessionCookie = sessionCookie,
                        request =
                            CreateIssueRequestDto(
                                mediaId = request.mediaId,
                                issueType = request.type.code,
                                message = request.message,
                                problemSeason = request.problemSeason,
                                problemEpisode = request.problemEpisode,
                            ),
                    )
                    .toJellyseerrIssue()
            }
        }
    }

    override suspend fun findJellyfinItem(media: JellyseerrMedia): FindroidItem {
        media.jellyfinMediaId?.let { jellyfinMediaId ->
            val parsedItemId = jellyfinMediaId.toUuidOrNull()

            try {
                val item =
                    parsedItemId?.let { jellyfinRepository.getItem(it) }
                        ?: throw JellyseerrWatchException(
                            "Jellyseerr marked this title as available, but Findroid could not find it in Jellyfin. The Jellyseerr library scan may be stale.",
                        )
                if (item.matches(media)) {
                    return item
                }
            } catch (e: JellyseerrWatchException) {
                Timber.w(
                    e,
                    "Failed to resolve Jellyfin media id %s for tmdb %s",
                    jellyfinMediaId,
                    media.tmdbId,
                )
            } catch (e: IllegalArgumentException) {
                Timber.w(
                    e,
                    "Invalid Jellyfin media id %s for tmdb %s",
                    jellyfinMediaId,
                    media.tmdbId,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(
                    e,
                    "Failed to open Jellyfin media id %s for tmdb %s",
                    jellyfinMediaId,
                    media.tmdbId,
                )
            }
        }

        return try {
            jellyfinRepository
                .getSearchItems(media.title)
                .firstOrNull { it.matches(media) }
                ?: throw JellyseerrWatchException(
                    "Findroid could not find this title in Jellyfin. Jellyseerr may be out of sync with the library for this user.",
                )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e is JellyseerrWatchException) {
                throw e
            }
            Timber.w(e, "Failed to search Jellyfin for %s", media.title)
            throw JellyseerrWatchException(
                "Findroid could not open this title from Jellyfin.",
                e,
            )
        }
    }

    private suspend fun getSection(
        loader: suspend (sessionCookie: String) -> DiscoverResponseDto
    ): List<JellyseerrMedia> {
        return runRequest {
            withSession { sessionCookie ->
                retryJellyseerrRequest("media section") { loader(sessionCookie) }
            }
                .results
                .asSequence()
                .filter { it.mediaType.equals("movie", ignoreCase = true) || it.mediaType.equals("tv", ignoreCase = true) }
                .map { it.toJellyseerrMedia() }
                .distinctBy { "${it.mediaType.apiValue}-${it.tmdbId}" }
                .toList()
        }
    }

    /**
     * Title info for each requested title: from the cache when seen before, otherwise one details
     * lookup per title (a title requested twice is looked up once), a few at a time.
     */
    private suspend fun loadRecentRequestMetadata(
        sessionCookie: String,
        keys: List<MediaKey>,
    ): Map<MediaKey, JellyseerrMedia> {
        return supervisorScope {
            keys
                .map { key ->
                    async {
                        val media =
                            mediaMetadataCache[key]
                                ?: recentRequestLookups.withPermit {
                                    fetchMetadata(sessionCookie, key)
                                }
                        media?.let { key to it }
                    }
                }
                .awaitAll()
                .filterNotNull()
                .toMap()
        }
    }

    private suspend fun fetchMetadata(sessionCookie: String, key: MediaKey): JellyseerrMedia? {
        val media =
            when (key.mediaType) {
                JellyseerrMediaType.MOVIE ->
                    optionalRequest("recent request movie details", key.tmdbId) {
                        apiService.getMovieDetails(sessionCookie, key.tmdbId)
                            .toJellyseerrMovieDetails()
                            .toJellyseerrMedia()
                    }
                JellyseerrMediaType.TV ->
                    optionalRequest("recent request tv details", key.tmdbId) {
                        apiService.getTvDetails(sessionCookie, key.tmdbId)
                            .toJellyseerrTvDetails()
                            .toJellyseerrMedia()
                    }
            }
        media?.let(::rememberMetadata)
        return media
    }

    private fun rememberMetadata(media: JellyseerrMedia) {
        mediaMetadataCache[MediaKey(media.mediaType, media.tmdbId)] = media
    }

    private fun MediaRequestDto.toRecentRequest(
        metadata: Map<MediaKey, JellyseerrMedia>
    ): JellyseerrRecentRequest? {
        val mediaInfo = media ?: return null
        val title = mediaKey()?.let(metadata::get) ?: return null

        return JellyseerrRecentRequest(
            requestId = id,
            // Title info may be cached; availability comes from this request's own media record.
            media =
                title.copy(
                    status =
                        mediaInfo.status?.let(JellyseerrMediaStatus::fromCode) ?: title.status,
                    jellyfinMediaId = mediaInfo.jellyfinMediaId ?: title.jellyfinMediaId,
                ),
            requestStatus = JellyseerrRequestStatus.fromCode(status),
            requestedAt = createdAt,
            updatedAt = updatedAt,
            seasonNumbers =
                seasons
                    .map { it.seasonNumber }
                    .filter { it > 0 }
                    .distinct()
                    .sorted(),
        )
    }

    private fun MediaRequestDto.mediaKey(): MediaKey? {
        val mediaInfo = media ?: return null
        val tmdbId = mediaInfo.tmdbId ?: return null
        val mediaType =
            mediaInfo.mediaType
                ?.takeIf { it.isNotBlank() }
                ?.let(JellyseerrMediaType::fromApiValue)
                ?: JellyseerrMediaType.fromApiValue(type)
        return MediaKey(mediaType, tmdbId)
    }

    /** Ratings are extras: a 404 (none known) gives null, other failures throw to the caller. */
    private suspend fun getRatings(
        requestName: String,
        loader: suspend (sessionCookie: String) -> JellyseerrRatings,
    ): JellyseerrRatings? {
        return runRequest {
            withSession { sessionCookie ->
                try {
                    retryJellyseerrRequest(requestName) { loader(sessionCookie) }
                } catch (e: JellyseerrApiException) {
                    if (e.code != 404) {
                        throw e
                    }
                    null
                }
            }
        }
    }

    private suspend fun getAllOpenIssues(
        sessionCookie: String,
        pageSize: Int = 100,
    ): List<dev.jdtech.jellyfin.jellyseerr.api.dto.IssueDto> {
        val issues = mutableListOf<dev.jdtech.jellyfin.jellyseerr.api.dto.IssueDto>()
        var skip = 0

        while (true) {
            val page =
                retryJellyseerrRequest("open issues page $skip") {
                    apiService.getOpenIssues(sessionCookie, take = pageSize, skip = skip)
                }
            issues += page.results

            val totalResults = page.pageInfo.results
            if (page.results.isEmpty() || issues.size >= totalResults) {
                return issues
            }

            skip += page.results.size
        }
    }

    private suspend fun <T> runRequest(block: suspend () -> T): T {
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: JellyseerrMissingCredentialsException) {
            throw e
        } catch (e: JellyseerrAuthenticationException) {
            throw e
        } catch (e: JellyseerrUnavailableException) {
            throw e
        } catch (e: JellyseerrApiException) {
            Timber.w(e, "Jellyseerr API request failed with HTTP %s", e.code)
            throw IllegalStateException(e.message ?: "Jellyseerr request failed with HTTP ${e.code}.", e)
        } catch (e: Exception) {
            throw JellyseerrUnavailableException(e)
        }
    }

    private suspend fun <T> retryJellyseerrRequest(
        requestName: String,
        block: suspend () -> T,
    ): T {
        var lastFailure: Throwable? = null

        repeat(JELLYSEERR_RETRY_DELAYS_MS.size + 1) { attempt ->
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: JellyseerrApiException) {
                if (!e.isRetryable || attempt == JELLYSEERR_RETRY_DELAYS_MS.size) {
                    throw e
                }
                lastFailure = e
                Timber.w(
                    e,
                    "Retrying Jellyseerr %s after HTTP %s failure, attempt %s of %s",
                    requestName,
                    e.code,
                    attempt + 1,
                    JELLYSEERR_MAX_ATTEMPTS,
                )
            } catch (e: IOException) {
                if (!e.isRetryable || attempt == JELLYSEERR_RETRY_DELAYS_MS.size) {
                    throw e
                }
                lastFailure = e
                Timber.w(
                    e,
                    "Retrying Jellyseerr %s after network failure, attempt %s of %s",
                    requestName,
                    attempt + 1,
                    JELLYSEERR_MAX_ATTEMPTS,
                )
            }

            delay(JELLYSEERR_RETRY_DELAYS_MS[attempt])
        }

        throw lastFailure ?: JellyseerrUnavailableException()
    }

    private suspend fun <T> withSession(block: suspend (sessionCookie: String) -> T): T {
        val context = currentContext()
        val sessionCookie = currentSessionCookie(context)

        return try {
            block(sessionCookie)
        } catch (e: JellyseerrApiException) {
            if (e.code != 401) {
                throw e
            }

            block(refreshSession(context, expiredCookie = sessionCookie))
        }
    }

    /**
     * Signs in again after [expiredCookie] was rejected. When several requests hit the expired
     * cookie at once, the first one signs in and the others reuse its new cookie.
     */
    private suspend fun refreshSession(context: JellyseerrContext, expiredCookie: String): String {
        return authMutex.withLock {
            val storedCookie =
                credentials { getJellyseerrSession(context.serverId, context.username) }
            if (storedCookie != null && storedCookie != expiredCookie) {
                return@withLock storedCookie
            }

            credentials { clearJellyseerrSession(context.serverId, context.username) }
            authenticate(context)
        }
    }

    private suspend fun <T> optionalRequest(
        requestName: String,
        tmdbId: Int,
        block: suspend () -> T
    ): T? {
        return try {
            retryJellyseerrRequest("$requestName $tmdbId", block)
        } catch (e: CancellationException) {
            throw e
        } catch (e: JellyseerrApiException) {
            if (e.code == 401) {
                throw e
            }

            Timber.w(e, "Failed to load %s for tmdb %s", requestName, tmdbId)
            null
        } catch (e: Exception) {
            Timber.w(e, "Failed to load %s for tmdb %s", requestName, tmdbId)
            null
        }
    }

    private suspend fun currentSessionCookie(context: JellyseerrContext): String {
        return credentials { getJellyseerrSession(context.serverId, context.username) }
            ?: authMutex.withLock {
                credentials { getJellyseerrSession(context.serverId, context.username) }
                    ?: authenticate(context)
            }
    }

    private suspend fun authenticate(context: JellyseerrContext): String {
        val sessionCookie =
            try {
                retryJellyseerrRequest("authentication") {
                    apiService.authenticate(
                        JellyseerrAuthRequestDto(
                            username = context.username,
                            password = context.password,
                        )
                    )
                }
            } catch (e: JellyseerrApiException) {
                if (e.code == 400 || e.code == 404) {
                    throw JellyseerrAuthenticationException(
                        "Jellyseerr could not authenticate with Jellyfin (HTTP ${e.code}). This often happens on Jellyfin 10.12+ when legacy auth is disabled.",
                        e,
                    )
                }
                if (e.code == 500 && e.message?.contains("hostname already configured", ignoreCase = true) == true) {
                    throw JellyseerrAuthenticationException(
                        "Jellyseerr rejected the Jellyfin hostname override. This Seerr server is already configured to a Jellyfin server and expects username/password only.",
                        e,
                    )
                }
                throw JellyseerrAuthenticationException(
                    "Jellyseerr login failed with HTTP ${e.code}.",
                    e,
                )
            }

        credentials { saveJellyseerrSession(context.serverId, context.username, sessionCookie) }
        return sessionCookie
    }

    /**
     * The encrypted store sets up its keys on first use and decrypts on every read. Callers are
     * often on the main thread (ViewModels), so this keeps that work off it.
     */
    private suspend fun <T> credentials(block: SecureCredentialsStore.() -> T): T =
        withContext(Dispatchers.IO) { secureCredentialsStore.block() }

    private suspend fun currentContext(): JellyseerrContext {
        val serverId =
            appPreferences.getValue(appPreferences.currentServer)
                ?: throw JellyseerrMissingCredentialsException()
        val serverData =
            serverDatabase.getServerWithAddressAndUser(serverId)
                ?: throw JellyseerrMissingCredentialsException()
        val username = serverData.user?.name ?: throw JellyseerrMissingCredentialsException()
        val password =
            credentials { getUserPassword(serverId, username) }
                ?: throw JellyseerrMissingCredentialsException()

        return JellyseerrContext(
            serverId = serverId,
            username = username,
            password = password,
        )
    }

    private data class JellyseerrContext(
        val serverId: String,
        val username: String,
        val password: String,
    )

    private data class MediaKey(val mediaType: JellyseerrMediaType, val tmdbId: Int)
}

private val JellyseerrApiException.isRetryable: Boolean
    get() = code in setOf(408, 429, 500, 502, 503, 504)

/**
 * A timeout already waited the full timeout, and a failed DNS lookup means no network: trying again
 * only makes the user wait several times longer for the same error.
 */
private val IOException.isRetryable: Boolean
    get() = this !is InterruptedIOException && this !is UnknownHostException

private const val MEDIA_METADATA_CACHE_SIZE = 200
private const val RECENT_REQUEST_LOOKUP_CONCURRENCY = 4
private const val JELLYSEERR_MAX_ATTEMPTS = 4
private val JELLYSEERR_RETRY_DELAYS_MS = longArrayOf(250L, 750L, 1500L)

private fun FindroidItem.matches(media: JellyseerrMedia): Boolean {
    val titleMatches =
        name.equals(media.title, ignoreCase = true) ||
            (originalTitle?.equals(media.title, ignoreCase = true) == true)

    if (!titleMatches) {
        return false
    }

    return when (media.mediaType) {
        JellyseerrMediaType.MOVIE -> this is FindroidMovie && productionYear.matches(media.year)
        JellyseerrMediaType.TV -> this is FindroidShow && productionYear.matches(media.year)
    }
}

private fun Int?.matches(year: String?): Boolean {
    val expectedYear = year?.toIntOrNull() ?: return true
    return this == null || this == expectedYear
}

private fun String.toUuidOrNull(): UUID? {
    return runCatching { UUID.fromString(this) }
        .getOrElse {
            val normalized = replace("-", "")
            if (normalized.length != 32 || normalized.any { char -> !char.isDigit() && char.lowercaseChar() !in 'a'..'f' }) {
                return null
            }

            val dashed =
                buildString(36) {
                    append(normalized, 0, 8)
                    append('-')
                    append(normalized, 8, 12)
                    append('-')
                    append(normalized, 12, 16)
                    append('-')
                    append(normalized, 16, 20)
                    append('-')
                    append(normalized, 20, 32)
                }

            runCatching { UUID.fromString(dashed) }.getOrNull()
        }
}
