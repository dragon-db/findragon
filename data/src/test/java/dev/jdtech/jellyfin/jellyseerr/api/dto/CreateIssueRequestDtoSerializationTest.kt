package dev.jdtech.jellyfin.jellyseerr.api.dto

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateIssueRequestDtoSerializationTest {
    private val json =
        Json {
            encodeDefaults = true
            explicitNulls = false
            ignoreUnknownKeys = true
        }

    @Test
    fun `movie issue serialization includes issue fields`() {
        val payload =
            json.encodeToString(
                CreateIssueRequestDto(
                    mediaId = 42,
                    issueType = 1,
                    message = "Video freezes.",
                    problemSeason = 0,
                    problemEpisode = 0,
                )
            )

        assertTrue(payload.contains("\"mediaId\":42"))
        assertTrue(payload.contains("\"issueType\":1"))
        assertTrue(payload.contains("\"message\":\"Video freezes.\""))
        assertTrue(payload.contains("\"problemSeason\":0"))
        assertTrue(payload.contains("\"problemEpisode\":0"))
    }

    @Test
    fun `series issue serialization includes season and episode context`() {
        val payload =
            json.encodeToString(
                CreateIssueRequestDto(
                    mediaId = 77,
                    issueType = 3,
                    message = "Subtitles are offset.",
                    problemSeason = 5,
                    problemEpisode = 2,
                )
            )

        assertTrue(payload.contains("\"mediaId\":77"))
        assertTrue(payload.contains("\"issueType\":3"))
        assertTrue(payload.contains("\"problemSeason\":5"))
        assertTrue(payload.contains("\"problemEpisode\":2"))
    }
}
