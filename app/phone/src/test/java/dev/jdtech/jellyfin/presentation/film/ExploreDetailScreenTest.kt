package dev.jdtech.jellyfin.presentation.film

import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrMediaStatus
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrTvDetails
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrTvSeason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExploreDetailScreenTest {
    @Test
    fun `season request label defaults to all when nothing is selected`() {
        assertEquals(
            "Request all seasons",
            buildSeasonRequestButtonLabel(
                selectedSeasonNumbers = emptySet(),
                requestableSeasonNumbers = listOf(1, 2, 3),
            ),
        )
    }

    @Test
    fun `season request label reflects a single selected season`() {
        assertEquals(
            "Request Season 2",
            buildSeasonRequestButtonLabel(
                selectedSeasonNumbers = setOf(2),
                requestableSeasonNumbers = listOf(1, 2, 3),
            ),
        )
    }

    @Test
    fun `season request label reflects a multi season selection`() {
        assertEquals(
            "Request 2 seasons",
            buildSeasonRequestButtonLabel(
                selectedSeasonNumbers = setOf(1, 3),
                requestableSeasonNumbers = listOf(1, 2, 3),
            ),
        )
    }

    @Test
    fun `requested season badge formats compact single and range labels`() {
        assertEquals("S27", formatSeasonNumberSummary(listOf(27)))
        assertEquals("S24-27", formatSeasonNumberSummary(listOf(24, 25, 26, 27)))
        assertEquals("S1-3, S7 +1", formatSeasonNumberSummary(listOf(1, 2, 3, 7, 10)))
    }

    @Test
    fun `requested season badge text returns all when every season is active`() {
        val tvDetails =
            JellyseerrTvDetails(
                tmdbId = 1,
                title = "Series",
                overview = "",
                posterUrl = null,
                backdropUrl = null,
                firstAirDate = "2020-01-01",
                lastAirDate = null,
                statusText = "Returning Series",
                voteAverage = 7.9,
                originalLanguage = "en",
                networks = emptyList(),
                productionCountries = emptyList(),
                nextAirDate = null,
                mediaStatus = JellyseerrMediaStatus.PENDING,
                jellyfinMediaId = null,
                ratings = null,
                seasons =
                    listOf(
                        requestedSeason(1),
                        requestedSeason(2),
                        requestedSeason(3),
                    ),
            )

        assertEquals("All", buildRequestedSeasonBadgeText(tvDetails))
    }

    @Test
    fun `requested season badge text returns null when nothing is active`() {
        val tvDetails =
            JellyseerrTvDetails(
                tmdbId = 1,
                title = "Series",
                overview = "",
                posterUrl = null,
                backdropUrl = null,
                firstAirDate = "2020-01-01",
                lastAirDate = null,
                statusText = "Returning Series",
                voteAverage = 7.9,
                originalLanguage = "en",
                networks = emptyList(),
                productionCountries = emptyList(),
                nextAirDate = null,
                mediaStatus = JellyseerrMediaStatus.UNKNOWN,
                jellyfinMediaId = null,
                ratings = null,
                seasons =
                    listOf(
                        requestedSeason(1, JellyseerrMediaStatus.AVAILABLE),
                        requestedSeason(2, JellyseerrMediaStatus.UNTRACKED),
                    ),
            )

        assertNull(buildRequestedSeasonBadgeText(tvDetails))
    }
}

private fun requestedSeason(
    seasonNumber: Int,
    status: JellyseerrMediaStatus = JellyseerrMediaStatus.PENDING,
): JellyseerrTvSeason {
    return JellyseerrTvSeason(
        seasonNumber = seasonNumber,
        name = "Season $seasonNumber",
        overview = "",
        airDate = null,
        episodeCount = 10,
        posterUrl = null,
        status = status,
    )
}
