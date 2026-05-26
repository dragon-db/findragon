package dev.jdtech.jellyfin.jellyseerr.model

import dev.jdtech.jellyfin.jellyseerr.api.dto.MovieRatingsCombinedDto
import dev.jdtech.jellyfin.jellyseerr.api.dto.RottenTomatoesRatingsDto
import kotlin.math.roundToInt

data class JellyseerrRatings(
    val rottenTomatoesCriticsScore: Int? = null,
    val rottenTomatoesAudienceScore: Int? = null,
    val tmdbScore: Int? = null,
) {
    val hasAny: Boolean
        get() =
            rottenTomatoesCriticsScore != null ||
                rottenTomatoesAudienceScore != null ||
                tmdbScore != null
}

fun RottenTomatoesRatingsDto.toJellyseerrRatings(tmdbVoteAverage: Double?): JellyseerrRatings {
    return JellyseerrRatings(
        rottenTomatoesCriticsScore = criticsScore,
        rottenTomatoesAudienceScore = audienceScore,
        tmdbScore = tmdbVoteAverage.toTmdbPercentScore(),
    )
}

fun MovieRatingsCombinedDto.toJellyseerrRatings(tmdbVoteAverage: Double?): JellyseerrRatings {
    return JellyseerrRatings(
        rottenTomatoesCriticsScore = rt?.criticsScore,
        rottenTomatoesAudienceScore = rt?.audienceScore,
        tmdbScore = tmdbVoteAverage.toTmdbPercentScore(),
    )
}

internal fun Double?.toTmdbPercentScore(): Int? {
    return this?.times(10)?.roundToInt()?.coerceIn(0, 100)
}
