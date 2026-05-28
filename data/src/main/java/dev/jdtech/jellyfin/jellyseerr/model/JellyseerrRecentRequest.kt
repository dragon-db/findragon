package dev.jdtech.jellyfin.jellyseerr.model

data class JellyseerrRecentRequest(
    val requestId: Int,
    val media: JellyseerrMedia,
    val requestStatus: JellyseerrRequestStatus,
    val requestedAt: String?,
    val updatedAt: String?,
    val seasonNumbers: List<Int> = emptyList(),
) {
    val canCancel: Boolean
        get() = requestStatus == JellyseerrRequestStatus.PENDING_APPROVAL

    val displayStatus: JellyseerrRecentRequestDisplayStatus
        get() =
            when (requestStatus) {
                JellyseerrRequestStatus.PENDING_APPROVAL -> JellyseerrRecentRequestDisplayStatus.PENDING
                JellyseerrRequestStatus.DECLINED -> JellyseerrRecentRequestDisplayStatus.DECLINED
                JellyseerrRequestStatus.FAILED -> JellyseerrRecentRequestDisplayStatus.FAILED
                JellyseerrRequestStatus.UNKNOWN -> JellyseerrRecentRequestDisplayStatus.PENDING
                JellyseerrRequestStatus.COMPLETED ->
                    if (media.canWatch || media.isAvailableWithoutLink) {
                        JellyseerrRecentRequestDisplayStatus.AVAILABLE
                    } else {
                        JellyseerrRecentRequestDisplayStatus.COMPLETED
                    }
                JellyseerrRequestStatus.APPROVED ->
                    when {
                        media.canWatch || media.isAvailableWithoutLink ->
                            JellyseerrRecentRequestDisplayStatus.AVAILABLE
                        media.status == JellyseerrMediaStatus.PROCESSING ->
                            JellyseerrRecentRequestDisplayStatus.PROCESSING
                        else -> JellyseerrRecentRequestDisplayStatus.PENDING
                    }
            }
}

enum class JellyseerrRequestStatus(val code: Int) {
    PENDING_APPROVAL(1),
    APPROVED(2),
    DECLINED(3),
    FAILED(4),
    COMPLETED(5),
    UNKNOWN(-1),
    ;

    companion object {
        fun fromCode(code: Int): JellyseerrRequestStatus {
            return entries.firstOrNull { it.code == code } ?: UNKNOWN
        }
    }
}

enum class JellyseerrRecentRequestDisplayStatus {
    PENDING,
    PROCESSING,
    AVAILABLE,
    DECLINED,
    FAILED,
    COMPLETED,
}
