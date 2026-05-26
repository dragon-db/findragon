package dev.jdtech.jellyfin.jellyseerr.model

import dev.jdtech.jellyfin.jellyseerr.api.dto.MediaInfoDto
import dev.jdtech.jellyfin.jellyseerr.api.dto.ProductionCountryDto
import dev.jdtech.jellyfin.jellyseerr.api.dto.RequestDto
import dev.jdtech.jellyfin.jellyseerr.api.dto.RequestSeasonDto
import dev.jdtech.jellyfin.jellyseerr.api.dto.SeasonStatusDto
import dev.jdtech.jellyfin.jellyseerr.api.dto.SeasonWithEpisodesDto
import dev.jdtech.jellyfin.jellyseerr.api.dto.TvDetailsDto
import dev.jdtech.jellyfin.jellyseerr.api.dto.TvEpisodeDto
import dev.jdtech.jellyfin.jellyseerr.api.dto.TvNetworkDto
import dev.jdtech.jellyfin.jellyseerr.api.dto.TvSeasonDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JellyseerrTvDetailsTest {
    @Test
    fun `tv details mapping derives requestable seasons and excludes specials`() {
        val details =
            TvDetailsDto(
                id = 100,
                name = "Sample Series",
                overview = "Overview",
                firstAirDate = "2024-01-01",
                status = "Returning Series",
                voteAverage = 8.4,
                originalLanguage = "en",
                networks = listOf(TvNetworkDto(id = 1, name = "MGM+")),
                productionCountries =
                    listOf(ProductionCountryDto(iso31661 = "US", name = "United States")),
                seasons =
                    listOf(
                        TvSeasonDto(
                            id = 10,
                            seasonNumber = 0,
                            name = "Specials",
                            episodeCount = 2,
                        ),
                        TvSeasonDto(
                            id = 11,
                            seasonNumber = 1,
                            name = "Season 1",
                            episodeCount = 10,
                        ),
                        TvSeasonDto(
                            id = 12,
                            seasonNumber = 2,
                            name = "Season 2",
                            episodeCount = 10,
                        ),
                        TvSeasonDto(
                            id = 13,
                            seasonNumber = 3,
                            name = "Season 3",
                            episodeCount = 10,
                        ),
                    ),
                mediaInfo =
                    MediaInfoDto(
                        status = 4,
                        jellyfinMediaId = "series-id",
                        seasons =
                            listOf(
                                SeasonStatusDto(id = 1, seasonNumber = 1, status = 5),
                                SeasonStatusDto(id = 2, seasonNumber = 2, status = 1),
                                SeasonStatusDto(id = 3, seasonNumber = 3, status = 2),
                            ),
                    ),
            )

        val mapped = details.toJellyseerrTvDetails()

        assertEquals("Sample Series", mapped.title)
        assertEquals(JellyseerrMediaStatus.PARTIALLY_AVAILABLE, mapped.mediaStatus)
        assertTrue(mapped.canWatch)
        assertEquals(listOf("United States"), mapped.productionCountries)
        assertEquals(84, mapped.ratings?.tmdbScore)
        assertEquals(listOf(2), mapped.requestableSeasonNumbers)
        assertEquals(listOf(3, 2, 1), mapped.seasons.map { it.seasonNumber })
        assertFalse(mapped.seasons.any { it.seasonNumber == 0 })
        assertEquals(JellyseerrMediaStatus.AVAILABLE, mapped.seasons.last().status)
    }

    @Test
    fun `season details mapping keeps episode content and descending season order helper`() {
        val details =
            SeasonWithEpisodesDto(
                id = 22,
                seasonNumber = 4,
                name = "Season 4",
                overview = "Season overview",
                episodes =
                    listOf(
                        TvEpisodeDto(
                            id = 1,
                            episodeNumber = 1,
                            name = "Episode 1",
                            overview = "First",
                            airDate = "2026-01-01",
                            seasonNumber = 4,
                            showId = 100,
                        ),
                        TvEpisodeDto(
                            id = 2,
                            episodeNumber = 3,
                            name = "Episode 3",
                            overview = "Third",
                            airDate = "2026-01-03",
                            seasonNumber = 4,
                            showId = 100,
                        ),
                    ),
            )

        val mapped = details.toJellyseerrTvSeasonDetails()

        assertEquals("Season 4", mapped.name)
        assertEquals(listOf(3, 1), mapped.sortedEpisodes.map { it.episodeNumber })
        assertEquals("Third", mapped.sortedEpisodes.first().overview)
    }

    @Test
    fun `tv details mapping marks requested seasons as non requestable when request list includes them`() {
        val details =
            TvDetailsDto(
                id = 200,
                name = "Sample Series",
                overview = "Overview",
                firstAirDate = "2024-01-01",
                seasons =
                    listOf(
                        TvSeasonDto(
                            id = 21,
                            seasonNumber = 26,
                            name = "Season 26",
                            episodeCount = 10,
                        ),
                        TvSeasonDto(
                            id = 22,
                            seasonNumber = 27,
                            name = "Season 27",
                            episodeCount = 10,
                        ),
                    ),
                mediaInfo =
                    MediaInfoDto(
                        status = 2,
                        requests =
                            listOf(
                                RequestDto(
                                    id = 99,
                                    status = 2,
                                    type = "tv",
                                    seasons = listOf(RequestSeasonDto(seasonNumber = 27)),
                                )
                            ),
                    ),
            )

        val mapped = details.toJellyseerrTvDetails()
        val requestedSeason = mapped.seasons.first { it.seasonNumber == 27 }
        val otherSeason = mapped.seasons.first { it.seasonNumber == 26 }

        assertEquals(JellyseerrMediaStatus.PENDING, requestedSeason.status)
        assertFalse(requestedSeason.isRequestable)
        assertTrue(otherSeason.isRequestable)
        assertEquals(listOf(26), mapped.requestableSeasonNumbers)
    }

    @Test
    fun `tv details mapping marks all seasons as processing for approved full-series request`() {
        val details =
            TvDetailsDto(
                id = 201,
                name = "Sample Series",
                overview = "Overview",
                firstAirDate = "2024-01-01",
                seasons =
                    listOf(
                        TvSeasonDto(
                            id = 31,
                            seasonNumber = 1,
                            name = "Season 1",
                            episodeCount = 10,
                        ),
                        TvSeasonDto(
                            id = 32,
                            seasonNumber = 2,
                            name = "Season 2",
                            episodeCount = 10,
                        ),
                    ),
                mediaInfo =
                    MediaInfoDto(
                        status = 3,
                        requests =
                            listOf(
                                RequestDto(
                                    id = 100,
                                    status = 2,
                                    type = "tv",
                                    seasons = emptyList(),
                                )
                            ),
                    ),
            )

        val mapped = details.toJellyseerrTvDetails()

        assertEquals(
            listOf(JellyseerrMediaStatus.PROCESSING, JellyseerrMediaStatus.PROCESSING),
            mapped.seasons.map { it.status }
        )
        assertTrue(mapped.requestableSeasonNumbers.isEmpty())
    }
}
