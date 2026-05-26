package dev.jdtech.jellyfin.jellyseerr.model

import dev.jdtech.jellyfin.jellyseerr.api.dto.MediaDto
import kotlinx.serialization.Serializable

@Serializable
data class JellyseerrMedia(
    val tmdbId: Int,
    val mediaType: JellyseerrMediaType,
    val title: String,
    val overview: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val releaseDate: String?,
    val voteAverage: Double?,
    val status: JellyseerrMediaStatus,
    val jellyfinMediaId: String?,
) {
    val year: String?
        get() = releaseDate?.substringBefore('-')?.takeIf { it.isNotBlank() }

    val canRequestMovie: Boolean
        get() =
            mediaType == JellyseerrMediaType.MOVIE &&
                (status == JellyseerrMediaStatus.UNTRACKED ||
                    status == JellyseerrMediaStatus.UNKNOWN)

    val canWatch: Boolean
        get() =
            !jellyfinMediaId.isNullOrBlank() &&
                (status == JellyseerrMediaStatus.PARTIALLY_AVAILABLE ||
                    status == JellyseerrMediaStatus.AVAILABLE)

    val isAvailableWithoutLink: Boolean
        get() =
            jellyfinMediaId.isNullOrBlank() &&
                (status == JellyseerrMediaStatus.PARTIALLY_AVAILABLE ||
                    status == JellyseerrMediaStatus.AVAILABLE)
}

fun MediaDto.toJellyseerrMedia(): JellyseerrMedia {
    val mediaType = JellyseerrMediaType.fromApiValue(mediaType)
    val title = title ?: name.orEmpty()
    val date = releaseDate ?: firstAirDate

    return JellyseerrMedia(
        tmdbId = id,
        mediaType = mediaType,
        title = title,
        overview = overview,
        posterUrl = posterPath?.let { "https://image.tmdb.org/t/p/w342$it" },
        backdropUrl = backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" },
        releaseDate = date,
        voteAverage = voteAverage,
        status =
            mediaInfo?.status?.let(JellyseerrMediaStatus::fromCode)
                ?: JellyseerrMediaStatus.UNTRACKED,
        jellyfinMediaId = mediaInfo?.jellyfinMediaId,
    )
}
