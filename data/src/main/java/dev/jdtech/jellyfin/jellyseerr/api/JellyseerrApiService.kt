package dev.jdtech.jellyfin.jellyseerr.api

import dev.jdtech.jellyfin.data.BuildConfig
import dev.jdtech.jellyfin.jellyseerr.api.dto.CreateTvRequestDto
import dev.jdtech.jellyfin.jellyseerr.api.dto.CreateIssueRequestDto
import dev.jdtech.jellyfin.jellyseerr.api.dto.CreateRequestDto
import dev.jdtech.jellyfin.jellyseerr.api.dto.DiscoverResponseDto
import dev.jdtech.jellyfin.jellyseerr.api.dto.IssueDto
import dev.jdtech.jellyfin.jellyseerr.api.dto.IssueResultsDto
import dev.jdtech.jellyfin.jellyseerr.api.dto.JellyseerrAuthRequestDto
import dev.jdtech.jellyfin.jellyseerr.api.dto.MovieDetailsDto
import dev.jdtech.jellyfin.jellyseerr.api.dto.MovieRatingsCombinedDto
import dev.jdtech.jellyfin.jellyseerr.api.dto.RequestResultsDto
import dev.jdtech.jellyfin.jellyseerr.api.dto.RequestResponseDto
import dev.jdtech.jellyfin.jellyseerr.api.dto.RottenTomatoesRatingsDto
import dev.jdtech.jellyfin.jellyseerr.api.dto.SeasonWithEpisodesDto
import dev.jdtech.jellyfin.jellyseerr.api.dto.TvDetailsDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class JellyseerrApiService(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {
    suspend fun authenticate(request: JellyseerrAuthRequestDto): String =
        withContext(Dispatchers.IO) {
            val httpRequest =
                Request.Builder()
                    .url(url("auth/jellyfin"))
                    .post(json.encodeToString(request).toRequestBody(JSON_MEDIA_TYPE))
                    .build()

            okHttpClient.newCall(httpRequest).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) {
                    throw JellyseerrApiException(response.code, body)
                }

                return@use response.headers("Set-Cookie")
                    .firstOrNull { it.startsWith("connect.sid=") }
                    ?.substringBefore(';')
                    ?: throw JellyseerrApiException(
                        response.code,
                        "Missing connect.sid cookie in Jellyseerr auth response",
                    )
            }
        }

    suspend fun getTrending(sessionCookie: String): DiscoverResponseDto {
        return get(
            path = "discover/trending",
            sessionCookie = sessionCookie,
            queryParameters = mapOf("page" to "1"),
        )
    }

    suspend fun search(sessionCookie: String, query: String): DiscoverResponseDto {
        return get(
            path = "search",
            sessionCookie = sessionCookie,
            queryParameters = mapOf("page" to "1", "query" to query),
        )
    }

    suspend fun getPopularMovies(sessionCookie: String): DiscoverResponseDto {
        return get(
            path = "discover/movies",
            sessionCookie = sessionCookie,
            queryParameters = mapOf("page" to "1", "sortBy" to "popularity.desc"),
        )
    }

    suspend fun getPopularSeries(sessionCookie: String): DiscoverResponseDto {
        return get(
            path = "discover/tv",
            sessionCookie = sessionCookie,
            queryParameters = mapOf("page" to "1", "sortBy" to "popularity.desc"),
        )
    }

    suspend fun getUpcomingSeries(sessionCookie: String): DiscoverResponseDto {
        return get(
            path = "discover/tv/upcoming",
            sessionCookie = sessionCookie,
            queryParameters = mapOf("page" to "1"),
        )
    }

    suspend fun getRecentRequests(
        sessionCookie: String,
        take: Int = 10,
    ): RequestResultsDto {
        return get(
            path = "request",
            sessionCookie = sessionCookie,
            queryParameters =
                mapOf(
                    "take" to take.toString(),
                    "sort" to "added",
                    "sortDirection" to "desc",
                    "mediaType" to "all",
                ),
        )
    }

    suspend fun getOpenIssues(
        sessionCookie: String,
        take: Int = 100,
        skip: Int = 0,
    ): IssueResultsDto {
        return get(
            path = "issue",
            sessionCookie = sessionCookie,
            queryParameters =
                mapOf(
                    "take" to take.toString(),
                    "skip" to skip.toString(),
                    "sort" to "added",
                    "filter" to "open",
                ),
        )
    }

    suspend fun getIssue(sessionCookie: String, issueId: Int): IssueDto {
        return get(
            path = "issue/$issueId",
            sessionCookie = sessionCookie,
        )
    }

    suspend fun getMovieDetails(sessionCookie: String, tmdbId: Int): MovieDetailsDto {
        return get(
            path = "movie/$tmdbId",
            sessionCookie = sessionCookie,
        )
    }

    suspend fun getMovieRatingsCombined(
        sessionCookie: String,
        tmdbId: Int,
    ): MovieRatingsCombinedDto {
        return get(
            path = "movie/$tmdbId/ratingscombined",
            sessionCookie = sessionCookie,
        )
    }

    suspend fun getTvDetails(sessionCookie: String, tmdbId: Int): TvDetailsDto {
        return get(
            path = "tv/$tmdbId",
            sessionCookie = sessionCookie,
        )
    }

    suspend fun getTvRatings(sessionCookie: String, tmdbId: Int): RottenTomatoesRatingsDto {
        return get(
            path = "tv/$tmdbId/ratings",
            sessionCookie = sessionCookie,
        )
    }

    suspend fun getTvSeason(
        sessionCookie: String,
        tmdbId: Int,
        seasonNumber: Int,
    ): SeasonWithEpisodesDto {
        return get(
            path = "tv/$tmdbId/season/$seasonNumber",
            sessionCookie = sessionCookie,
        )
    }

    suspend fun requestMovie(sessionCookie: String, mediaId: Int): RequestResponseDto {
        return post(
            path = "request",
            sessionCookie = sessionCookie,
            payload = CreateRequestDto(mediaId = mediaId, mediaType = "movie"),
        )
    }

    suspend fun requestTv(
        sessionCookie: String,
        mediaId: Int,
        seasons: List<Int>,
    ): RequestResponseDto {
        return post(
            path = "request",
            sessionCookie = sessionCookie,
            payload = CreateTvRequestDto(mediaId = mediaId, mediaType = "tv", seasons = seasons),
        )
    }

    suspend fun createIssue(
        sessionCookie: String,
        request: CreateIssueRequestDto,
    ): IssueDto {
        return post(
            path = "issue",
            sessionCookie = sessionCookie,
            payload = request,
        )
    }

    suspend fun cancelRequest(sessionCookie: String, requestId: Int) {
        withContext(Dispatchers.IO) {
            val request =
                Request.Builder()
                    .url(url("request/$requestId"))
                    .addHeader("Cookie", sessionCookie)
                    .delete()
                    .build()

            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) {
                    throw JellyseerrApiException(response.code, body)
                }
            }
        }
    }

    private suspend inline fun <reified T> get(
        path: String,
        sessionCookie: String,
        queryParameters: Map<String, String> = emptyMap(),
    ): T =
        withContext(Dispatchers.IO) {
            val request =
                Request.Builder()
                    .url(url(path, queryParameters))
                    .addHeader("Cookie", sessionCookie)
                    .get()
                    .build()

            okHttpClient.newCall(request).execute().use(::decodeResponse)
        }

    private suspend inline fun <reified T, reified R> post(
        path: String,
        sessionCookie: String,
        payload: R,
    ): T =
        withContext(Dispatchers.IO) {
            val request =
                Request.Builder()
                    .url(url(path))
                    .addHeader("Cookie", sessionCookie)
                    .post(json.encodeToString(payload).toRequestBody(JSON_MEDIA_TYPE))
                    .build()

            okHttpClient.newCall(request).execute().use(::decodeResponse)
        }

    private inline fun <reified T> decodeResponse(response: okhttp3.Response): T {
        val body = response.body.string()
        if (!response.isSuccessful) {
            throw JellyseerrApiException(response.code, body)
        }
        return json.decodeFromString(body)
    }

    private fun url(path: String, queryParameters: Map<String, String> = emptyMap()): HttpUrl {
        return "${BuildConfig.JELLYSEERR_BASE_URL.trimEnd('/')}/api/v1/$path"
            .toHttpUrl()
            .newBuilder()
            .apply {
                queryParameters.forEach { (key, value) -> addQueryParameter(key, value) }
            }
            .build()
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
