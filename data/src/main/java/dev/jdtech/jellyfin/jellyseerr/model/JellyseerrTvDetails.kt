package dev.jdtech.jellyfin.jellyseerr.model

import dev.jdtech.jellyfin.jellyseerr.api.dto.SeasonStatusDto
import dev.jdtech.jellyfin.jellyseerr.api.dto.SeasonWithEpisodesDto
import dev.jdtech.jellyfin.jellyseerr.api.dto.TvDetailsDto
import dev.jdtech.jellyfin.jellyseerr.api.dto.TvEpisodeDto
import dev.jdtech.jellyfin.jellyseerr.model.toTmdbPercentScore

data class JellyseerrTvDetails(
    val tmdbId: Int,
    val title: String,
    val overview: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val firstAirDate: String?,
    val lastAirDate: String?,
    val statusText: String?,
    val voteAverage: Double?,
    val originalLanguage: String?,
    val networks: List<String>,
    val productionCountries: List<String>,
    val nextAirDate: String?,
    val mediaStatus: JellyseerrMediaStatus,
    val jellyfinMediaId: String?,
    val seerrMediaId: Int? = null,
    val ratings: JellyseerrRatings?,
    val seasons: List<JellyseerrTvSeason>,
) {
    val year: String?
        get() = firstAirDate?.substringBefore('-')?.takeIf { it.isNotBlank() }

    val canWatch: Boolean
        get() =
            !jellyfinMediaId.isNullOrBlank() &&
                (mediaStatus == JellyseerrMediaStatus.PARTIALLY_AVAILABLE ||
                    mediaStatus == JellyseerrMediaStatus.AVAILABLE)

    val isAvailableWithoutLink: Boolean
        get() =
            jellyfinMediaId.isNullOrBlank() &&
                (mediaStatus == JellyseerrMediaStatus.PARTIALLY_AVAILABLE ||
                    mediaStatus == JellyseerrMediaStatus.AVAILABLE)

    val requestableSeasonNumbers: List<Int>
        get() =
            seasons
                .asSequence()
                .filter { it.seasonNumber > 0 }
                .filter { it.isRequestable }
                .map { it.seasonNumber }
                .toList()

    fun toJellyseerrMedia(): JellyseerrMedia {
        return JellyseerrMedia(
            tmdbId = tmdbId,
            mediaType = JellyseerrMediaType.TV,
            title = title,
            overview = overview,
            posterUrl = posterUrl,
            backdropUrl = backdropUrl,
            releaseDate = firstAirDate,
            voteAverage = voteAverage,
            status = mediaStatus,
            jellyfinMediaId = jellyfinMediaId,
        )
    }
}

data class JellyseerrTvSeason(
    val seasonNumber: Int,
    val name: String,
    val overview: String,
    val airDate: String?,
    val episodeCount: Int,
    val posterUrl: String?,
    val status: JellyseerrMediaStatus,
) {
    val isRequestable: Boolean
        get() =
            seasonNumber > 0 &&
                (status == JellyseerrMediaStatus.UNTRACKED ||
                    status == JellyseerrMediaStatus.UNKNOWN)
}

data class JellyseerrTvSeasonDetails(
    val seasonNumber: Int,
    val name: String,
    val overview: String,
    val airDate: String?,
    val posterUrl: String?,
    val episodes: List<JellyseerrTvEpisode>,
) {
    val sortedEpisodes: List<JellyseerrTvEpisode>
        get() = episodes.sortedByDescending { it.episodeNumber }
}

data class JellyseerrTvEpisode(
    val episodeNumber: Int,
    val name: String,
    val overview: String,
    val airDate: String?,
)

fun TvDetailsDto.toJellyseerrTvDetails(
    ratings: JellyseerrRatings? = null
): JellyseerrTvDetails {
    val seasonStatuses = mediaInfo?.seasons.orEmpty().associateBy { it.seasonNumber }
    val mediaStatus =
        mediaInfo?.status?.let(JellyseerrMediaStatus::fromCode)
            ?: JellyseerrMediaStatus.UNTRACKED
    val requestedSeasonStatuses =
        mediaInfo?.requests
            .orEmpty()
            .filter { request -> request.isActiveSeasonRequest() }
            .flatMap { request ->
                request.toRequestedSeasonStatuses(
                    allSeasonNumbers = seasons.map { it.seasonNumber }.filter { it > 0 },
                    derivedStatus =
                        when (mediaStatus) {
                            JellyseerrMediaStatus.PROCESSING -> JellyseerrMediaStatus.PROCESSING
                            else -> JellyseerrMediaStatus.PENDING
                        },
                )
            }
            .toMap()

    return JellyseerrTvDetails(
        tmdbId = id,
        title = name,
        overview = overview,
        posterUrl = posterPath?.toTmdbPosterUrl(),
        backdropUrl = backdropPath?.toTmdbBackdropUrl(),
        firstAirDate = firstAirDate,
        lastAirDate = lastAirDate,
        statusText = status,
        voteAverage = voteAverage,
        originalLanguage = originalLanguage,
        networks = networks.map { it.name }.filter { it.isNotBlank() },
        productionCountries = productionCountries.map { it.name }.filter { it.isNotBlank() },
        nextAirDate = nextEpisodeToAir?.airDate,
        mediaStatus = mediaStatus,
        jellyfinMediaId = mediaInfo?.jellyfinMediaId,
        seerrMediaId = mediaInfo?.id,
        ratings = ratings ?: JellyseerrRatings(tmdbScore = voteAverage.toTmdbPercentScore()),
        seasons =
            seasons
                .asSequence()
                .filter { it.seasonNumber > 0 }
                .map { season ->
                    season.toJellyseerrTvSeason(
                        seasonStatus = seasonStatuses[season.seasonNumber],
                        requestedStatus = requestedSeasonStatuses[season.seasonNumber],
                    )
                }
                .sortedByDescending { it.seasonNumber }
                .toList(),
    )
}

fun SeasonWithEpisodesDto.toJellyseerrTvSeasonDetails(): JellyseerrTvSeasonDetails {
    return JellyseerrTvSeasonDetails(
        seasonNumber = seasonNumber,
        name = name,
        overview = overview,
        airDate = airDate,
        posterUrl = posterPath?.toTmdbPosterUrl(),
        episodes = episodes.map(TvEpisodeDto::toJellyseerrTvEpisode),
    )
}

private fun dev.jdtech.jellyfin.jellyseerr.api.dto.TvSeasonDto.toJellyseerrTvSeason(
    seasonStatus: SeasonStatusDto?,
    requestedStatus: JellyseerrMediaStatus?,
): JellyseerrTvSeason {
    val mappedSeasonStatus =
        seasonStatus?.status?.let(JellyseerrMediaStatus::fromCode)
            ?: JellyseerrMediaStatus.UNTRACKED
    val effectiveStatus =
        if (
            requestedStatus != null &&
                mappedSeasonStatus in
                setOf(JellyseerrMediaStatus.UNTRACKED, JellyseerrMediaStatus.UNKNOWN)
        ) {
            requestedStatus
        } else {
            mappedSeasonStatus
        }

    return JellyseerrTvSeason(
        seasonNumber = seasonNumber,
        name = name,
        overview = overview,
        airDate = airDate,
        episodeCount = episodeCount,
        posterUrl = posterPath?.toTmdbPosterUrl(),
        status = effectiveStatus,
    )
}

private fun TvEpisodeDto.toJellyseerrTvEpisode(): JellyseerrTvEpisode {
    return JellyseerrTvEpisode(
        episodeNumber = episodeNumber,
        name = name,
        overview = overview,
        airDate = airDate,
    )
}

private fun dev.jdtech.jellyfin.jellyseerr.api.dto.RequestDto.isActiveSeasonRequest(): Boolean {
    return status == REQUEST_STATUS_PENDING_APPROVAL || status == REQUEST_STATUS_APPROVED
}

private fun dev.jdtech.jellyfin.jellyseerr.api.dto.RequestDto.toRequestedSeasonStatuses(
    allSeasonNumbers: List<Int>,
    derivedStatus: JellyseerrMediaStatus,
): List<Pair<Int, JellyseerrMediaStatus>> {
    val seasonNumbers =
        if (seasons.isEmpty()) {
            allSeasonNumbers
        } else {
            seasons.map { it.seasonNumber }
        }

    return seasonNumbers.map { seasonNumber -> seasonNumber to derivedStatus }
}

private fun String.toTmdbPosterUrl(): String = "https://image.tmdb.org/t/p/w342$this"

private fun String.toTmdbBackdropUrl(): String = "https://image.tmdb.org/t/p/w780$this"

private const val REQUEST_STATUS_PENDING_APPROVAL = 1
private const val REQUEST_STATUS_APPROVED = 2
