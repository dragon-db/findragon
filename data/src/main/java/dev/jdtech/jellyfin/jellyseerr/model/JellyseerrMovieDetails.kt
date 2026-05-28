package dev.jdtech.jellyfin.jellyseerr.model

import dev.jdtech.jellyfin.jellyseerr.api.dto.MovieDetailsDto

data class JellyseerrMovieDetails(
    val tmdbId: Int,
    val title: String,
    val originalTitle: String?,
    val overview: String,
    val tagline: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val releaseDate: String?,
    val runtimeMinutes: Int?,
    val statusText: String?,
    val voteAverage: Double?,
    val voteCount: Int?,
    val originalLanguage: String?,
    val genres: List<String>,
    val productionCountries: List<String>,
    val spokenLanguages: List<String>,
    val mediaStatus: JellyseerrMediaStatus,
    val jellyfinMediaId: String?,
    val seerrMediaId: Int? = null,
    val ratings: JellyseerrRatings?,
) {
    val year: String?
        get() = releaseDate?.substringBefore('-')?.takeIf { it.isNotBlank() }

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

    fun toJellyseerrMedia(): JellyseerrMedia {
        return JellyseerrMedia(
            tmdbId = tmdbId,
            mediaType = JellyseerrMediaType.MOVIE,
            title = title,
            overview = overview,
            posterUrl = posterUrl,
            backdropUrl = backdropUrl,
            releaseDate = releaseDate,
            voteAverage = voteAverage,
            status = mediaStatus,
            jellyfinMediaId = jellyfinMediaId,
        )
    }
}

fun MovieDetailsDto.toJellyseerrMovieDetails(
    ratings: JellyseerrRatings? = null
): JellyseerrMovieDetails {
    return JellyseerrMovieDetails(
        tmdbId = id,
        title = title,
        originalTitle = originalTitle,
        overview = overview,
        tagline = tagline,
        posterUrl = posterPath?.toTmdbPosterUrl(),
        backdropUrl = backdropPath?.toTmdbBackdropUrl(),
        releaseDate = releaseDate,
        runtimeMinutes = runtime,
        statusText = status,
        voteAverage = voteAverage,
        voteCount = voteCount,
        originalLanguage = originalLanguage,
        genres = genres.map { it.name }.filter { it.isNotBlank() },
        productionCountries = productionCountries.map { it.name }.filter { it.isNotBlank() },
        spokenLanguages =
            spokenLanguages
                .mapNotNull { it.englishName ?: it.name }
                .filter { it.isNotBlank() },
        mediaStatus =
            mediaInfo?.status?.let(JellyseerrMediaStatus::fromCode)
                ?: JellyseerrMediaStatus.UNTRACKED,
        jellyfinMediaId = mediaInfo?.jellyfinMediaId,
        seerrMediaId = mediaInfo?.id,
        ratings = ratings ?: JellyseerrRatings(tmdbScore = voteAverage.toTmdbPercentScore()),
    )
}

private fun String.toTmdbPosterUrl(): String = "https://image.tmdb.org/t/p/w342$this"

private fun String.toTmdbBackdropUrl(): String = "https://image.tmdb.org/t/p/w780$this"
