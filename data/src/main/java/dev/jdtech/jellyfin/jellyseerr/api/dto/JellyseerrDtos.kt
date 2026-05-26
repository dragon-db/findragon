package dev.jdtech.jellyfin.jellyseerr.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class JellyseerrAuthRequestDto(
    val username: String,
    val password: String,
    val serverType: Int = 2,
)

@Serializable
data class DiscoverResponseDto(
    val page: Int = 1,
    val totalPages: Int = 1,
    val totalResults: Int = 0,
    val results: List<MediaDto> = emptyList(),
)

@Serializable
data class RequestResultsDto(
    val pageInfo: PageInfoDto = PageInfoDto(),
    val results: List<MediaRequestDto> = emptyList(),
)

@Serializable
data class PageInfoDto(
    val page: Int = 1,
    val pages: Int = 1,
    val results: Int = 0,
)

@Serializable
data class MediaDto(
    val id: Int,
    val mediaType: String,
    val title: String? = null,
    val name: String? = null,
    val overview: String = "",
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val releaseDate: String? = null,
    val firstAirDate: String? = null,
    val voteAverage: Double? = null,
    val mediaInfo: MediaInfoDto? = null,
)

@Serializable
data class MediaInfoDto(
    val id: Int? = null,
    val tmdbId: Int? = null,
    val tvdbId: Int? = null,
    val status: Int? = null,
    val status4k: Int? = null,
    val mediaType: String? = null,
    val jellyfinMediaId: String? = null,
    val jellyfinMediaId4k: String? = null,
    val seasons: List<SeasonStatusDto> = emptyList(),
    val requests: List<RequestDto> = emptyList(),
)

@Serializable
data class IssueResultsDto(
    val pageInfo: PageInfoDto = PageInfoDto(),
    val results: List<IssueDto> = emptyList(),
)

@Serializable
data class IssueDto(
    val id: Int,
    val issueType: Int,
    val status: Int,
    val problemSeason: Int = 0,
    val problemEpisode: Int = 0,
    val media: MediaInfoDto? = null,
    val comments: List<IssueCommentDto> = emptyList(),
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class IssueCommentDto(
    val id: Int? = null,
    val message: String = "",
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class CreateIssueRequestDto(
    val mediaId: Int,
    val issueType: Int,
    val message: String,
    val problemSeason: Int,
    val problemEpisode: Int,
)

@Serializable
data class MediaRequestDto(
    val id: Int,
    val status: Int,
    val type: String,
    val media: MediaInfoDto? = null,
    val seasons: List<RequestSeasonDto> = emptyList(),
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class RequestDto(
    val id: Int,
    val status: Int,
    val type: String,
    val seasons: List<RequestSeasonDto> = emptyList(),
)

@Serializable
data class CreateRequestDto(
    val mediaId: Int,
    val mediaType: String,
)

@Serializable
data class CreateTvRequestDto(
    val mediaId: Int,
    val mediaType: String,
    val seasons: List<Int>,
)

@Serializable
data class RequestResponseDto(
    val id: Int,
    val status: Int,
    val type: String,
    val seasons: List<RequestSeasonDto> = emptyList(),
)

@Serializable
data class RequestSeasonDto(
    val id: Int? = null,
    val seasonNumber: Int,
    val status: Int? = null,
)

@Serializable
data class SeasonStatusDto(
    val id: Int,
    val seasonNumber: Int,
    val status: Int,
    val status4k: Int? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class TvDetailsDto(
    val id: Int,
    val name: String,
    val originalName: String? = null,
    val overview: String = "",
    val tagline: String? = null,
    val backdropPath: String? = null,
    val posterPath: String? = null,
    val firstAirDate: String? = null,
    val lastAirDate: String? = null,
    val status: String? = null,
    val type: String? = null,
    val inProduction: Boolean = false,
    val homepage: String? = null,
    val popularity: Double? = null,
    val voteAverage: Double? = null,
    val voteCount: Int? = null,
    val numberOfEpisodes: Int? = null,
    val numberOfSeasons: Int? = null,
    val episodeRunTime: List<Int> = emptyList(),
    val originalLanguage: String? = null,
    val networks: List<TvNetworkDto> = emptyList(),
    val productionCountries: List<ProductionCountryDto> = emptyList(),
    val seasons: List<TvSeasonDto> = emptyList(),
    val nextEpisodeToAir: TvEpisodeDto? = null,
    val mediaInfo: MediaInfoDto? = null,
)

@Serializable
data class TvNetworkDto(
    val id: Int,
    val name: String,
)

@Serializable
data class TvSeasonDto(
    val id: Int,
    val airDate: String? = null,
    val episodeCount: Int = 0,
    val name: String,
    val overview: String = "",
    val posterPath: String? = null,
    val seasonNumber: Int,
)

@Serializable
data class SeasonWithEpisodesDto(
    val id: Int,
    val airDate: String? = null,
    val name: String,
    val overview: String = "",
    val posterPath: String? = null,
    val seasonNumber: Int,
    val episodes: List<TvEpisodeDto> = emptyList(),
)

@Serializable
data class TvEpisodeDto(
    val id: Int,
    val name: String,
    val airDate: String? = null,
    val episodeNumber: Int,
    val overview: String = "",
    val seasonNumber: Int,
    val showId: Int,
    val stillPath: String? = null,
    val voteAverage: Double? = null,
    val voteCount: Int? = null,
)

@Serializable
data class GenreDto(
    val id: Int,
    val name: String,
)

@Serializable
data class ProductionCountryDto(
    @SerialName("iso_3166_1")
    val iso31661: String? = null,
    val name: String,
)

@Serializable
data class SpokenLanguageDto(
    @SerialName("english_name")
    val englishName: String? = null,
    @SerialName("iso_639_1")
    val iso6391: String? = null,
    val name: String,
)

@Serializable
data class MovieDetailsDto(
    val id: Int,
    val imdbId: String? = null,
    val adult: Boolean = false,
    val backdropPath: String? = null,
    val posterPath: String? = null,
    val budget: Long? = null,
    val genres: List<GenreDto> = emptyList(),
    val homepage: String? = null,
    val originalLanguage: String? = null,
    val originalTitle: String? = null,
    val overview: String = "",
    val popularity: Double? = null,
    val productionCountries: List<ProductionCountryDto> = emptyList(),
    val releaseDate: String? = null,
    val revenue: Long? = null,
    val runtime: Int? = null,
    val spokenLanguages: List<SpokenLanguageDto> = emptyList(),
    val status: String? = null,
    val tagline: String? = null,
    val title: String,
    val video: Boolean = false,
    val voteAverage: Double? = null,
    val voteCount: Int? = null,
    val mediaInfo: MediaInfoDto? = null,
)

@Serializable
data class RottenTomatoesRatingsDto(
    val title: String? = null,
    val year: Int? = null,
    val url: String? = null,
    val criticsScore: Int? = null,
    val criticsRating: String? = null,
    val audienceScore: Int? = null,
    val audienceRating: String? = null,
)

@Serializable
data class ImdbRatingsDto(
    val title: String? = null,
    val url: String? = null,
    val criticsScore: Double? = null,
)

@Serializable
data class MovieRatingsCombinedDto(
    val rt: RottenTomatoesRatingsDto? = null,
    val imdb: ImdbRatingsDto? = null,
)
