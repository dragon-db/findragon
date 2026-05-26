package dev.jdtech.jellyfin.film.presentation.issue

import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrIssue
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrIssueStatus
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrIssueType
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrMediaStatus
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrTvSeason
import org.junit.Assert.assertEquals
import org.junit.Test

class IssueReporterStateTest {
    @Test
    fun `series target defaults to latest season and all episodes`() {
        val target =
            IssueReportTarget(
                title = "The Boys",
                seerrMediaId = 10,
                mediaType = IssueReportMediaType.SERIES,
                seasons =
                    listOf(
                        season(1),
                        season(5),
                        season(3),
                    ),
                defaultEpisodeNumber = 0,
            )

        assertEquals(5, target.initialSeasonNumber)
        assertEquals(0, target.initialEpisodeNumber)
    }

    @Test
    fun `open issues sort exact context matches first`() {
        val exact = issue(id = 2, season = 5, episode = 0)
        val other = issue(id = 1, season = 4, episode = 0)
        val state =
            IssueReporterState(
                target =
                    IssueReportTarget(
                        title = "The Boys",
                        seerrMediaId = 10,
                        mediaType = IssueReportMediaType.SERIES,
                        defaultSeasonNumber = 5,
                        defaultEpisodeNumber = 0,
                    ),
                openIssues = listOf(other, exact),
            )

        assertEquals(listOf(2, 1), state.sortedIssues.map { it.id })
    }

    private fun season(number: Int): JellyseerrTvSeason {
        return JellyseerrTvSeason(
            seasonNumber = number,
            name = "Season $number",
            overview = "",
            airDate = null,
            episodeCount = 8,
            posterUrl = null,
            status = JellyseerrMediaStatus.AVAILABLE,
        )
    }

    private fun issue(id: Int, season: Int, episode: Int): JellyseerrIssue {
        return JellyseerrIssue(
            id = id,
            mediaId = 10,
            type = JellyseerrIssueType.VIDEO,
            status = JellyseerrIssueStatus.OPEN,
            problemSeason = season,
            problemEpisode = episode,
            message = "Issue",
            createdAt = null,
            updatedAt = null,
        )
    }
}
