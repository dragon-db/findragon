package dev.jdtech.jellyfin.jellyseerr.model

import dev.jdtech.jellyfin.jellyseerr.api.dto.GenreDto
import dev.jdtech.jellyfin.jellyseerr.api.dto.MediaInfoDto
import dev.jdtech.jellyfin.jellyseerr.api.dto.MovieDetailsDto
import dev.jdtech.jellyfin.jellyseerr.api.dto.MovieRatingsCombinedDto
import dev.jdtech.jellyfin.jellyseerr.api.dto.ProductionCountryDto
import dev.jdtech.jellyfin.jellyseerr.api.dto.RottenTomatoesRatingsDto
import dev.jdtech.jellyfin.jellyseerr.api.dto.SpokenLanguageDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JellyseerrMovieDetailsTest {
    @Test
    fun `movie details mapping keeps metadata and derived rating values`() {
        val dto =
            MovieDetailsDto(
                id = 603,
                title = "The Matrix",
                originalTitle = "The Matrix",
                overview = "A computer hacker learns the truth.",
                tagline = "Welcome to the Real World.",
                releaseDate = "1999-03-31",
                runtime = 136,
                status = "Released",
                voteAverage = 8.7,
                voteCount = 25000,
                originalLanguage = "en",
                genres =
                    listOf(
                        GenreDto(id = 1, name = "Action"),
                        GenreDto(id = 2, name = "Science Fiction"),
                    ),
                productionCountries =
                    listOf(ProductionCountryDto(iso31661 = "US", name = "United States")),
                spokenLanguages =
                    listOf(
                        SpokenLanguageDto(
                            englishName = "English",
                            iso6391 = "en",
                            name = "English",
                        )
                    ),
                mediaInfo =
                    MediaInfoDto(
                        status = 5,
                        jellyfinMediaId = "movie-id",
                    ),
            )

        val ratings =
            MovieRatingsCombinedDto(
                rt =
                    RottenTomatoesRatingsDto(
                        criticsScore = 83,
                        audienceScore = 85,
                    )
            ).toJellyseerrRatings(dto.voteAverage)

        val mapped = dto.toJellyseerrMovieDetails(ratings = ratings)

        assertEquals("The Matrix", mapped.title)
        assertEquals(listOf("Action", "Science Fiction"), mapped.genres)
        assertEquals(listOf("United States"), mapped.productionCountries)
        assertEquals(listOf("English"), mapped.spokenLanguages)
        assertEquals(83, mapped.ratings?.rottenTomatoesCriticsScore)
        assertEquals(85, mapped.ratings?.rottenTomatoesAudienceScore)
        assertEquals(87, mapped.ratings?.tmdbScore)
        assertTrue(mapped.canWatch)
        assertFalse(mapped.isAvailableWithoutLink)
    }
}
