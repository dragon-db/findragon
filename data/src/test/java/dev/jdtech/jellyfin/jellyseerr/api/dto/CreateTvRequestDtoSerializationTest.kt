package dev.jdtech.jellyfin.jellyseerr.api.dto

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateTvRequestDtoSerializationTest {
    private val json =
        Json {
            encodeDefaults = true
            explicitNulls = false
            ignoreUnknownKeys = true
        }

    @Test
    fun `tv request serialization includes explicit media type`() {
        val payload =
            json.encodeToString(
                CreateTvRequestDto(
                    mediaId = 100,
                    mediaType = "tv",
                    seasons = listOf(1, 2),
                )
            )

        assertTrue(payload.contains("\"mediaType\":\"tv\""))
    }
}
