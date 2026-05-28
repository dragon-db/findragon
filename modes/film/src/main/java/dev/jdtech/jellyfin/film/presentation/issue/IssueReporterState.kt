package dev.jdtech.jellyfin.film.presentation.issue

import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrIssue
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrIssueType
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrTvSeason

data class IssueReporterState(
    val target: IssueReportTarget? = null,
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val openIssues: List<JellyseerrIssue> = emptyList(),
    val selectedIssue: JellyseerrIssue? = null,
    val showExistingIssues: Boolean = false,
    val showReportDialog: Boolean = false,
    val errorMessage: String? = null,
) {
    val canReport: Boolean
        get() = target?.seerrMediaId != null && !isLoading

    val issueCount: Int
        get() = openIssues.size

    val exactContextIssues: List<JellyseerrIssue>
        get() =
            target?.let { reportTarget ->
                openIssues.filter {
                    it.matchesContext(
                        seasonNumber = reportTarget.defaultSeasonNumber,
                        episodeNumber = reportTarget.defaultEpisodeNumber,
                    )
                }
            }.orEmpty()

    val sortedIssues: List<JellyseerrIssue>
        get() = exactContextIssues + (openIssues - exactContextIssues.toSet())
}

data class IssueReportTarget(
    val title: String,
    val seerrMediaId: Int?,
    val mediaType: IssueReportMediaType,
    val seasons: List<JellyseerrTvSeason> = emptyList(),
    val defaultSeasonNumber: Int? = null,
    val defaultEpisodeNumber: Int? = null,
) {
    val isSeries: Boolean
        get() = mediaType == IssueReportMediaType.SERIES

    val initialSeasonNumber: Int
        get() = defaultSeasonNumber ?: seasons.maxOfOrNull { it.seasonNumber } ?: 0

    val initialEpisodeNumber: Int
        get() = defaultEpisodeNumber ?: 0
}

enum class IssueReportMediaType {
    MOVIE,
    SERIES,
}

data class IssueReportForm(
    val type: JellyseerrIssueType,
    val message: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
)

sealed interface IssueReporterEvent {
    data class Message(val message: String) : IssueReporterEvent
}
