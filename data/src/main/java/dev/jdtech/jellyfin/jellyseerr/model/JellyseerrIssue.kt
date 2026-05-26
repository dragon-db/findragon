package dev.jdtech.jellyfin.jellyseerr.model

import dev.jdtech.jellyfin.jellyseerr.api.dto.IssueDto

data class JellyseerrIssue(
    val id: Int,
    val mediaId: Int?,
    val type: JellyseerrIssueType,
    val status: JellyseerrIssueStatus,
    val problemSeason: Int,
    val problemEpisode: Int,
    val message: String,
    val createdAt: String?,
    val updatedAt: String?,
) {
    fun matchesContext(seasonNumber: Int?, episodeNumber: Int?): Boolean {
        val expectedSeason = seasonNumber ?: 0
        val expectedEpisode = episodeNumber ?: 0
        return problemSeason == expectedSeason && problemEpisode == expectedEpisode
    }
}

data class CreateJellyseerrIssue(
    val mediaId: Int,
    val type: JellyseerrIssueType,
    val message: String,
    val problemSeason: Int,
    val problemEpisode: Int,
)

enum class JellyseerrIssueType(val code: Int) {
    VIDEO(1),
    AUDIO(2),
    SUBTITLE(3),
    OTHER(4);

    companion object {
        fun fromCode(code: Int): JellyseerrIssueType {
            return entries.firstOrNull { it.code == code } ?: OTHER
        }
    }
}

enum class JellyseerrIssueStatus(val code: Int) {
    OPEN(1),
    RESOLVED(2);

    companion object {
        fun fromCode(code: Int): JellyseerrIssueStatus {
            return entries.firstOrNull { it.code == code } ?: OPEN
        }
    }
}

fun IssueDto.toJellyseerrIssue(): JellyseerrIssue {
    return JellyseerrIssue(
        id = id,
        mediaId = media?.id,
        type = JellyseerrIssueType.fromCode(issueType),
        status = JellyseerrIssueStatus.fromCode(status),
        problemSeason = problemSeason,
        problemEpisode = problemEpisode,
        message = comments.firstOrNull()?.message.orEmpty(),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
