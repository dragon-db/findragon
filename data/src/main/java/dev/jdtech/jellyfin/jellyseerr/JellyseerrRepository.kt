package dev.jdtech.jellyfin.jellyseerr

import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrMedia
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrMovieDetails
import dev.jdtech.jellyfin.jellyseerr.model.CreateJellyseerrIssue
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrIssue
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrRecentRequest
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrTvDetails
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrTvSeasonDetails
import dev.jdtech.jellyfin.models.FindroidItem

interface JellyseerrRepository {
    suspend fun ensureAuthenticated()

    suspend fun search(query: String): List<JellyseerrMedia>

    suspend fun getTrending(): List<JellyseerrMedia>

    suspend fun getPopularMovies(): List<JellyseerrMedia>

    suspend fun getPopularSeries(): List<JellyseerrMedia>

    suspend fun getUpcomingSeries(): List<JellyseerrMedia>

    suspend fun getRecentRequests(): List<JellyseerrRecentRequest>

    suspend fun getOpenIssues(mediaId: Int): List<JellyseerrIssue>

    suspend fun getIssue(issueId: Int): JellyseerrIssue

    suspend fun getMovieDetails(tmdbId: Int): JellyseerrMovieDetails

    suspend fun getTvDetails(tmdbId: Int): JellyseerrTvDetails

    suspend fun getTvSeason(tmdbId: Int, seasonNumber: Int): JellyseerrTvSeasonDetails

    suspend fun requestMovie(media: JellyseerrMedia): JellyseerrMedia

    suspend fun requestTvSeasons(tmdbId: Int, selectedSeasonNumbers: List<Int>): JellyseerrTvDetails

    suspend fun cancelRequest(requestId: Int)

    suspend fun createIssue(request: CreateJellyseerrIssue): JellyseerrIssue

    suspend fun findJellyfinItem(media: JellyseerrMedia): FindroidItem
}

class JellyseerrMissingCredentialsException :
    IllegalStateException("No stored Jellyfin password is available for this user")

class JellyseerrAuthenticationException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

class JellyseerrUnavailableException(cause: Throwable? = null) :
    IllegalStateException("Jellyseerr is unavailable", cause)

class JellyseerrWatchException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)
