package dev.jdtech.jellyfin.jellyseerr.model

import kotlinx.serialization.Serializable

@Serializable
enum class JellyseerrMediaType(val apiValue: String) {
    MOVIE("movie"),
    TV("tv");

    companion object {
        fun fromApiValue(value: String): JellyseerrMediaType {
            return when (value.lowercase()) {
                MOVIE.apiValue -> MOVIE
                else -> TV
            }
        }
    }
}
