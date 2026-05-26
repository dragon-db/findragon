package dev.jdtech.jellyfin.jellyseerr.model

import dev.jdtech.jellyfin.jellyseerr.api.dto.IssueCommentDto
import dev.jdtech.jellyfin.jellyseerr.api.dto.IssueDto
import dev.jdtech.jellyfin.jellyseerr.api.dto.MediaInfoDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JellyseerrIssueTest {
    @Test
    fun `issue mapping keeps type status context and first comment`() {
        val mapped =
            IssueDto(
                id = 9,
                issueType = 3,
                status = 1,
                problemSeason = 5,
                problemEpisode = 2,
                media = MediaInfoDto(id = 77),
                comments = listOf(IssueCommentDto(message = "Subtitles are late.")),
                createdAt = "2026-05-23T10:00:00.000Z",
                updatedAt = "2026-05-23T10:05:00.000Z",
            ).toJellyseerrIssue()

        assertEquals(9, mapped.id)
        assertEquals(77, mapped.mediaId)
        assertEquals(JellyseerrIssueType.SUBTITLE, mapped.type)
        assertEquals(JellyseerrIssueStatus.OPEN, mapped.status)
        assertEquals(5, mapped.problemSeason)
        assertEquals(2, mapped.problemEpisode)
        assertEquals("Subtitles are late.", mapped.message)
        assertTrue(mapped.matchesContext(seasonNumber = 5, episodeNumber = 2))
        assertFalse(mapped.matchesContext(seasonNumber = 5, episodeNumber = 0))
    }
}
