package dev.jdtech.jellyfin.jellyseerr.model

import kotlinx.serialization.Serializable

@Serializable
enum class JellyseerrMediaStatus(val code: Int?) {
    UNTRACKED(null),
    UNKNOWN(1),
    PENDING(2),
    PROCESSING(3),
    PARTIALLY_AVAILABLE(4),
    AVAILABLE(5);

    companion object {
        fun fromCode(code: Int?): JellyseerrMediaStatus {
            return entries.firstOrNull { it.code == code } ?: UNKNOWN
        }
    }
}
