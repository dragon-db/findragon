package dev.jdtech.jellyfin.presentation.film

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.R
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.film.presentation.explore.ExploreEvent
import dev.jdtech.jellyfin.film.presentation.explore.ExploreSeasonDetailState
import dev.jdtech.jellyfin.film.presentation.explore.ExploreViewModel
import dev.jdtech.jellyfin.film.presentation.explore.findMedia
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrMedia
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrMediaStatus
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrMediaType
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrMovieDetails
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrRatings
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrTvDetails
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrTvEpisode
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrTvSeason
import dev.jdtech.jellyfin.models.FindroidChapter
import dev.jdtech.jellyfin.models.FindroidImages
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidSource
import dev.jdtech.jellyfin.presentation.film.components.ItemHeader
import dev.jdtech.jellyfin.presentation.film.components.ItemTopBar
import dev.jdtech.jellyfin.presentation.film.components.OverviewText
import dev.jdtech.jellyfin.presentation.film.components.RequestButton
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.presentation.utils.rememberSafePadding
import dev.jdtech.jellyfin.utils.ObserveAsEvents
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.UUID

@Composable
fun ExploreDetailScreen(
    tmdbId: Int,
    mediaType: String,
    navigateBack: () -> Unit,
    onItemClick: (FindroidItem) -> Unit,
    viewModel: ExploreViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val parsedMediaType = remember(mediaType) { JellyseerrMediaType.fromApiValue(mediaType) }
    val detailState =
        state.detail.takeIf { it.tmdbId == tmdbId && it.mediaType == parsedMediaType }
    val currentMedia =
        detailState?.selectedMedia ?: state.findMedia(tmdbId, parsedMediaType)

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is ExploreEvent.NavigateToItem -> onItemClick(event.item)
            is ExploreEvent.ShowMessage ->
                Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(tmdbId, parsedMediaType) {
        viewModel.loadDetail(tmdbId, parsedMediaType)
    }

    when {
        parsedMediaType == JellyseerrMediaType.MOVIE && detailState?.movieDetails != null -> {
            val movieDetails = requireNotNull(detailState.movieDetails)
            ExploreMovieDetailScreenLayout(
                movieDetails = movieDetails,
                isSubmittingRequest = detailState.isSubmittingRequest,
                errorMessage = detailState.errorMessage,
                onBackClick = navigateBack,
                onRequestClick = { viewModel.requestMovie(movieDetails.toJellyseerrMedia()) },
                onWatchClick = { viewModel.openInJellyfin(movieDetails.toJellyseerrMedia()) },
            )
        }
        parsedMediaType == JellyseerrMediaType.TV && detailState?.tvDetails != null -> {
            val tvDetails = requireNotNull(detailState.tvDetails)
            ExploreTvDetailScreenLayout(
                tvDetails = tvDetails,
                expandedSeasonNumber = detailState.expandedSeasonNumber,
                seasonDetails = detailState.seasonDetails,
                selectedSeasonNumbers = detailState.selectedSeasonNumbers,
                isSubmittingRequest = detailState.isSubmittingRequest,
                errorMessage = detailState.errorMessage,
                onBackClick = navigateBack,
                onWatchClick = { viewModel.openInJellyfin(tvDetails.toJellyseerrMedia()) },
                onSeasonExpandToggle = viewModel::toggleSeasonExpanded,
                onSeasonSelectionToggle = viewModel::toggleSeasonSelected,
                onSelectAllClick = viewModel::selectAllRequestableSeasons,
                onClearSelectionClick = viewModel::clearSeasonSelection,
                onSubmitRequestClick = viewModel::submitSelectedTvSeasons,
            )
        }
        detailState?.isLoading == true && currentMedia == null -> {
            ExploreDetailLoading(onBackClick = navigateBack)
        }
        currentMedia != null -> {
            ExploreMovieDetailFallbackLayout(
                media = currentMedia,
                isLoading = detailState?.isLoading == true,
                isSubmittingRequest = detailState?.isSubmittingRequest == true,
                errorMessage = detailState?.errorMessage,
                onBackClick = navigateBack,
                onRequestClick = { viewModel.requestMovie(currentMedia) },
                onWatchClick = { viewModel.openInJellyfin(currentMedia) },
            )
        }
        else -> ExploreDetailUnavailable(
            onBackClick = navigateBack,
            message = detailState?.errorMessage,
        )
    }
}

@Composable
private fun ExploreMovieDetailScreenLayout(
    movieDetails: JellyseerrMovieDetails,
    isSubmittingRequest: Boolean,
    errorMessage: String?,
    onBackClick: () -> Unit,
    onRequestClick: () -> Unit,
    onWatchClick: () -> Unit,
) {
    val media = remember(movieDetails) { movieDetails.toJellyseerrMedia() }
    val metadataEntries =
        remember(movieDetails) {
            buildList {
                movieDetails.statusText?.takeIf { it.isNotBlank() }?.let {
                    add(
                        ExploreMetadataEntry(
                            labelRes = R.string.explore_movie_status_label,
                            value = it,
                        )
                    )
                }
                formatDisplayDate(movieDetails.releaseDate)?.let {
                    add(
                        ExploreMetadataEntry(
                            labelRes = R.string.explore_release_date_label,
                            value = it,
                        )
                    )
                }
                formatRuntime(movieDetails.runtimeMinutes)?.let {
                    add(
                        ExploreMetadataEntry(
                            labelRes = R.string.explore_runtime_label,
                            value = it,
                        )
                    )
                }
                formatLanguage(movieDetails.originalLanguage)?.let {
                    add(
                        ExploreMetadataEntry(
                            labelRes = R.string.explore_original_language_label,
                            value = it,
                        )
                    )
                }
                formatListValue(movieDetails.productionCountries)?.let {
                    add(
                        ExploreMetadataEntry(
                            labelRes = R.string.explore_production_countries_label,
                            value = it,
                        )
                    )
                }
                val originalTitle = movieDetails.originalTitle
                if (
                    !originalTitle.isNullOrBlank() &&
                        !originalTitle.equals(movieDetails.title, ignoreCase = true)
                ) {
                    add(
                        ExploreMetadataEntry(
                            labelRes = R.string.explore_original_title_label,
                            value = originalTitle,
                        )
                    )
                }
                formatListValue(movieDetails.spokenLanguages)?.let {
                    add(
                        ExploreMetadataEntry(
                            labelRes = R.string.explore_spoken_languages_label,
                            value = it,
                        )
                    )
                }
            }
        }

    ExploreDetailContent(
        headerItem = media.toExploreHeaderItem(),
        title = movieDetails.title,
        subtitle = mediaDetailSubtitle(media),
        onBackClick = onBackClick,
    ) {
        ExploreHeaderMetaRow(media = media)
        Spacer(Modifier.height(MaterialTheme.spacings.small))

        ExplorePrimaryActionButton(
            media = media,
            isSubmittingRequest = isSubmittingRequest,
            onRequestClick = onRequestClick,
            onWatchClick = onWatchClick,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(MaterialTheme.spacings.medium))

        errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(MaterialTheme.spacings.small))
        }

        OverviewText(
            text = movieDetails.overview.ifBlank { stringResource(R.string.explore_no_overview) },
            maxCollapsedLines = 4,
        )
        Spacer(Modifier.height(MaterialTheme.spacings.medium))

        if (movieDetails.genres.isNotEmpty()) {
            ExploreChipSection(
                title = stringResource(R.string.explore_genres_section_title),
                values = movieDetails.genres,
            )
            Spacer(Modifier.height(MaterialTheme.spacings.medium))
        }

        movieDetails.ratings?.takeIf { it.hasAny }?.let { ratings ->
            ExploreRatingsSection(ratings = ratings)
            Spacer(Modifier.height(MaterialTheme.spacings.medium))
        }

        if (metadataEntries.isNotEmpty()) {
            ExploreMetadataSection(entries = metadataEntries)
        }
    }
}

@Composable
private fun ExploreMovieDetailFallbackLayout(
    media: JellyseerrMedia,
    isLoading: Boolean,
    isSubmittingRequest: Boolean,
    errorMessage: String?,
    onBackClick: () -> Unit,
    onRequestClick: () -> Unit,
    onWatchClick: () -> Unit,
) {
    ExploreDetailContent(
        headerItem = media.toExploreHeaderItem(),
        title = media.title,
        subtitle = mediaDetailSubtitle(media),
        onBackClick = onBackClick,
    ) {
        ExploreHeaderMetaRow(media = media)
        Spacer(Modifier.height(MaterialTheme.spacings.small))

        ExplorePrimaryActionButton(
            media = media,
            isSubmittingRequest = isSubmittingRequest,
            onRequestClick = onRequestClick,
            onWatchClick = onWatchClick,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(MaterialTheme.spacings.small))

        if (isLoading) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(R.string.explore_loading_details),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.74f),
                )
            }
            Spacer(Modifier.height(MaterialTheme.spacings.small))
        }

        errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(MaterialTheme.spacings.small))
        }

        OverviewText(
            text = media.overview.ifBlank { stringResource(R.string.explore_no_overview) },
            maxCollapsedLines = 4,
        )
    }
}

@Composable
private fun ExploreTvDetailScreenLayout(
    tvDetails: JellyseerrTvDetails,
    expandedSeasonNumber: Int?,
    seasonDetails: Map<Int, ExploreSeasonDetailState>,
    selectedSeasonNumbers: Set<Int>,
    isSubmittingRequest: Boolean,
    errorMessage: String?,
    onBackClick: () -> Unit,
    onWatchClick: () -> Unit,
    onSeasonExpandToggle: (Int) -> Unit,
    onSeasonSelectionToggle: (Int) -> Unit,
    onSelectAllClick: () -> Unit,
    onClearSelectionClick: () -> Unit,
    onSubmitRequestClick: () -> Unit,
) {
    val media = remember(tvDetails) { tvDetails.toJellyseerrMedia() }
    val requestableSeasonNumbers = tvDetails.requestableSeasonNumbers
    val requestedSeasonBadgeText = remember(tvDetails) { buildRequestedSeasonBadgeText(tvDetails) }
    val metadataEntries =
        remember(tvDetails) {
            buildList {
                tvDetails.statusText?.takeIf { it.isNotBlank() }?.let {
                    add(
                        ExploreMetadataEntry(
                            labelRes = R.string.explore_series_status_label,
                            value = it,
                        )
                    )
                }
                formatDisplayDate(tvDetails.firstAirDate)?.let {
                    add(
                        ExploreMetadataEntry(
                            labelRes = R.string.explore_first_air_date_label,
                            value = it,
                        )
                    )
                }
                formatDisplayDate(tvDetails.nextAirDate)?.let {
                    add(
                        ExploreMetadataEntry(
                            labelRes = R.string.explore_next_air_date_label,
                            value = it,
                        )
                    )
                }
                formatLanguage(tvDetails.originalLanguage)?.let {
                    add(
                        ExploreMetadataEntry(
                            labelRes = R.string.explore_original_language_label,
                            value = it,
                        )
                    )
                }
                formatListValue(tvDetails.productionCountries)?.let {
                    add(
                        ExploreMetadataEntry(
                            labelRes = R.string.explore_production_countries_label,
                            value = it,
                        )
                    )
                }
                formatListValue(tvDetails.networks)?.let {
                    add(
                        ExploreMetadataEntry(
                            labelRes = R.string.explore_networks_label,
                            value = it,
                        )
                    )
                }
                add(
                    ExploreMetadataEntry(
                        labelRes = R.string.explore_total_seasons_label,
                        value = tvDetails.seasons.size.toString(),
                    )
                )
            }
        }

    ExploreDetailContent(
        headerItem = media.toExploreHeaderItem(),
        title = tvDetails.title,
        subtitle = mediaDetailSubtitle(media),
        onBackClick = onBackClick,
    ) {
        ExploreHeaderMetaRow(
            media = media,
            statusDetail = requestedSeasonBadgeText,
        )
        Spacer(Modifier.height(MaterialTheme.spacings.small))

        ExplorePrimaryActionButton(
            media = media,
            onRequestClick = {},
            onWatchClick = onWatchClick,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(MaterialTheme.spacings.medium))

        OverviewText(
            text = tvDetails.overview.ifBlank { stringResource(R.string.explore_no_overview) },
            maxCollapsedLines = 4,
        )
        Spacer(Modifier.height(MaterialTheme.spacings.medium))

        tvDetails.ratings?.takeIf { it.hasAny }?.let { ratings ->
            ExploreRatingsSection(ratings = ratings)
            Spacer(Modifier.height(MaterialTheme.spacings.medium))
        }

        ExploreMetadataSection(entries = metadataEntries)
        Spacer(Modifier.height(MaterialTheme.spacings.medium))

        Text(
            text = stringResource(CoreR.string.seasons),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(MaterialTheme.spacings.small))

        if (requestableSeasonNumbers.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onSelectAllClick,
                    enabled = !isSubmittingRequest,
                ) {
                    Text(text = stringResource(R.string.explore_select_all_requestable))
                }
                TextButton(
                    onClick = onClearSelectionClick,
                    enabled = !isSubmittingRequest && selectedSeasonNumbers.isNotEmpty(),
                ) {
                    Text(text = stringResource(R.string.explore_clear_selection))
                }
            }
            Spacer(Modifier.height(MaterialTheme.spacings.extraSmall))
            RequestButton(
                text =
                    buildSeasonRequestButtonLabel(
                        selectedSeasonNumbers = selectedSeasonNumbers,
                        requestableSeasonNumbers = requestableSeasonNumbers,
                    ),
                onClick = onSubmitRequestClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSubmittingRequest,
            )
            if (isSubmittingRequest) {
                Spacer(Modifier.height(MaterialTheme.spacings.small))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(R.string.explore_submitting_request),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(Modifier.height(MaterialTheme.spacings.small))
        } else {
            Text(
                text = stringResource(R.string.explore_no_requestable_seasons),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.74f),
            )
            Spacer(Modifier.height(MaterialTheme.spacings.small))
        }

        errorMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(MaterialTheme.spacings.small))
        }

        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small)) {
            tvDetails.seasons.forEach { season ->
                TvSeasonCard(
                    season = season,
                    isExpanded = expandedSeasonNumber == season.seasonNumber,
                    isSelected = season.seasonNumber in selectedSeasonNumbers,
                    seasonDetails = seasonDetails[season.seasonNumber],
                    onExpandToggle = { onSeasonExpandToggle(season.seasonNumber) },
                    onSelectionToggle = { onSeasonSelectionToggle(season.seasonNumber) },
                )
            }
        }
    }
}

@Composable
private fun ExploreDetailContent(
    headerItem: FindroidItem,
    title: String,
    subtitle: String?,
    onBackClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val safePadding = rememberSafePadding()
    val paddingStart = safePadding.start + MaterialTheme.spacings.default
    val paddingEnd = safePadding.end + MaterialTheme.spacings.default
    val paddingBottom = safePadding.bottom + MaterialTheme.spacings.default
    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(scrollState)) {
            ItemHeader(
                item = headerItem,
                scrollState = scrollState,
                content = {
                    Column(
                        modifier =
                            Modifier.align(Alignment.BottomStart)
                                .padding(start = paddingStart, end = paddingEnd),
                    ) {
                        Text(
                            text = title,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 3,
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        subtitle?.let {
                            Text(
                                text = it,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                },
            )

            Column(modifier = Modifier.padding(start = paddingStart, end = paddingEnd)) {
                Spacer(Modifier.height(MaterialTheme.spacings.small))
                content()
                Spacer(Modifier.height(MaterialTheme.spacings.medium))
            }
            Spacer(Modifier.height(paddingBottom))
        }

        ItemTopBar(
            hasBackButton = true,
            hasHomeButton = false,
            onBackClick = onBackClick,
            onHomeClick = {},
        )
    }
}

@Composable
private fun ExploreHeaderMetaRow(
    media: JellyseerrMedia,
    statusDetail: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExploreStatusChip(
            media = media,
            detail = statusDetail,
        )
        media.voteAverage?.let { voteAverage ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.extraSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_star),
                    contentDescription = null,
                    tint = Color("#F2C94C".toColorInt()),
                )
                Text(
                    text = "%.1f".format(voteAverage),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ExploreMetadataSection(entries: List<ExploreMetadataEntry>) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column {
            entries.forEachIndexed { index, entry ->
                ExploreMetadataRow(
                    label = stringResource(entry.labelRes),
                    value = entry.value,
                    showDivider = index != entries.lastIndex,
                )
            }
        }
    }
}

@Composable
private fun ExploreMetadataRow(
    label: String,
    value: String,
    showDivider: Boolean = true,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacings.default),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(MaterialTheme.spacings.default))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showDivider) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun ExploreRatingsSection(ratings: JellyseerrRatings) {
    val items =
        buildList {
            ratings.rottenTomatoesCriticsScore?.let {
                add(
                    ExploreRatingCardModel(
                        title = stringResource(R.string.explore_rotten_tomatoes_label),
                        subtitle = stringResource(R.string.explore_critics_label),
                        score = "$it%",
                        accent = Color(0xFFE74C3C),
                        progress = it / 100f,
                    )
                )
            }
            ratings.rottenTomatoesAudienceScore?.let {
                add(
                    ExploreRatingCardModel(
                        title = stringResource(R.string.explore_rotten_tomatoes_label),
                        subtitle = stringResource(R.string.explore_audience_label),
                        score = "$it%",
                        accent = Color(0xFFF39C12),
                        progress = it / 100f,
                    )
                )
            }
            ratings.tmdbScore?.let {
                add(
                    ExploreRatingCardModel(
                        title = stringResource(R.string.explore_tmdb_rating_label),
                        subtitle = null,
                        score = "$it%",
                        accent = Color(0xFF21D1C0),
                        progress = it / 100f,
                    )
                )
            }
        }

    if (items.isEmpty()) {
        return
    }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacings.default),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
        ) {
            items.forEachIndexed { index, item ->
                ExploreRatingRow(item = item)
                if (index != items.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
private fun ExploreRatingRow(
    item: ExploreRatingCardModel,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.extraSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier.size(10.dp)
                            .background(item.accent, CircleShape),
                )
                Text(
                    text =
                        item.subtitle?.let { "${item.title} · $it" }
                            ?: item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = item.score,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        LinearProgressIndicator(
            progress = { item.progress },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = item.accent,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExploreChipSection(
    title: String,
    values: List<String>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
        ) {
            values.forEach { value ->
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(
                        text = value,
                        modifier =
                            Modifier.padding(
                                horizontal = MaterialTheme.spacings.default,
                                vertical = MaterialTheme.spacings.small,
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        Spacer(Modifier.height(MaterialTheme.spacings.extraSmall))
    }
}

@Composable
private fun TvSeasonCard(
    season: JellyseerrTvSeason,
    isExpanded: Boolean,
    isSelected: Boolean,
    seasonDetails: ExploreSeasonDetailState?,
    onExpandToggle: () -> Unit,
    onSelectionToggle: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacings.default),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f).clickable(onClick = onExpandToggle),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.extraSmall),
                ) {
                    Text(
                        text = season.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = {
                            Text(
                                text =
                                    stringResource(
                                        R.string.explore_episode_count,
                                        season.episodeCount,
                                    )
                            )
                        },
                        colors =
                            AssistChipDefaults.assistChipColors(
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                    )
                }

                Spacer(Modifier.width(MaterialTheme.spacings.small))
                SeasonSelectionIcon(
                    season = season,
                    isSelected = isSelected,
                    onClick = onSelectionToggle,
                )
                Spacer(Modifier.width(MaterialTheme.spacings.extraSmall))
                Icon(
                    painter =
                        painterResource(
                            if (isExpanded) CoreR.drawable.ic_chevron_up else CoreR.drawable.ic_chevron_down
                        ),
                    contentDescription = null,
                    modifier = Modifier.clickable(onClick = onExpandToggle),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (isExpanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                when (seasonDetails) {
                    ExploreSeasonDetailState.Loading -> {
                        Row(
                            modifier = Modifier.padding(MaterialTheme.spacings.default),
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(
                                text = stringResource(R.string.explore_loading_episodes),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    is ExploreSeasonDetailState.Error -> {
                        Text(
                            text = seasonDetails.message,
                            modifier = Modifier.padding(MaterialTheme.spacings.default),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    is ExploreSeasonDetailState.Success -> {
                        Column {
                            seasonDetails.details.sortedEpisodes.forEachIndexed { index, episode ->
                                TvEpisodeRow(episode = episode)
                                if (index != seasonDetails.details.sortedEpisodes.lastIndex) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                    }
                    null -> Unit
                }
            }
        }
    }
}

@Composable
private fun SeasonSelectionIcon(
    season: JellyseerrTvSeason,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val (iconRes, containerColor, contentColor) =
        when {
            season.isRequestable && isSelected ->
                Triple(
                    CoreR.drawable.ic_minus_fat,
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.onPrimary,
                )
            season.isRequestable ->
                Triple(
                    CoreR.drawable.ic_plus,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    MaterialTheme.colorScheme.primary,
                )
            season.status == JellyseerrMediaStatus.PENDING ->
                Triple(
                    CoreR.drawable.ic_clock,
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.onSecondaryContainer,
                )
            season.status == JellyseerrMediaStatus.PROCESSING ->
                Triple(
                    CoreR.drawable.ic_download,
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.onSecondaryContainer,
                )
            else ->
                Triple(
                    CoreR.drawable.ic_check,
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer,
                )
        }

    Box(
        modifier =
            Modifier.size(32.dp)
                .background(containerColor, CircleShape)
                .then(
                    if (season.isRequestable) {
                        Modifier.clickable(onClick = onClick)
                    } else {
                        Modifier
                    }
                ),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(18.dp).align(Alignment.Center),
            tint = contentColor,
        )
    }
}

@Composable
private fun TvEpisodeRow(episode: JellyseerrTvEpisode) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacings.default),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.extraSmall),
    ) {
        Text(
            text =
                stringResource(
                    CoreR.string.episode_name,
                    episode.episodeNumber,
                    episode.name,
                ),
            style = MaterialTheme.typography.bodyLarge,
        )
        formatDisplayDate(episode.airDate)?.let { airDate ->
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text(text = airDate) },
                colors =
                    AssistChipDefaults.assistChipColors(
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
            )
        }
        if (episode.overview.isNotBlank()) {
            Text(
                text = episode.overview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ExploreDetailLoading(onBackClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        ItemTopBar(
            hasBackButton = true,
            hasHomeButton = false,
            onBackClick = onBackClick,
            onHomeClick = {},
        )
    }
}

@Composable
private fun ExploreDetailUnavailable(
    onBackClick: () -> Unit,
    message: String? = null,
) {
    val safePadding = rememberSafePadding()

    Box(modifier = Modifier.fillMaxSize()) {
        Text(
            text = message ?: stringResource(R.string.explore_detail_unavailable),
            modifier =
                Modifier.align(Alignment.Center)
                    .padding(horizontal = safePadding.start + MaterialTheme.spacings.default),
            style = MaterialTheme.typography.bodyLarge,
        )

        ItemTopBar(
            hasBackButton = true,
            hasHomeButton = false,
            onBackClick = onBackClick,
            onHomeClick = {},
        )
    }
}

@Composable
private fun ExploreStatusChip(
    media: JellyseerrMedia,
    detail: String? = null,
) {
    val (labelRes, iconRes) =
        when {
            media.canWatch || media.isAvailableWithoutLink ->
                R.string.explore_available to CoreR.drawable.ic_check
            media.status == JellyseerrMediaStatus.PENDING ->
                R.string.explore_requested to CoreR.drawable.ic_clock
            media.status == JellyseerrMediaStatus.PROCESSING ->
                R.string.explore_downloading to CoreR.drawable.ic_download
            media.canRequestMovie ->
                R.string.explore_not_requested to CoreR.drawable.ic_plus
            else -> R.string.explore_series_request_select_seasons to CoreR.drawable.ic_tv
        }
    val resolvedDetail =
        detail?.takeIf {
            (media.status == JellyseerrMediaStatus.PENDING ||
                media.status == JellyseerrMediaStatus.PROCESSING) &&
                it.isNotBlank()
        }

    AssistChip(
        onClick = {},
        enabled = false,
        label = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = stringResource(labelRes))
                resolvedDetail?.let { badgeText ->
                    Box(
                        modifier =
                            Modifier.background(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                shape = CircleShape,
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        },
        leadingIcon = {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
            )
        },
        colors =
            AssistChipDefaults.assistChipColors(
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                disabledLabelColor = MaterialTheme.colorScheme.onSurface,
                disabledLeadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
    )
}

@Composable
private fun ExplorePrimaryActionButton(
    media: JellyseerrMedia,
    isSubmittingRequest: Boolean = false,
    onRequestClick: () -> Unit,
    onWatchClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        media.canWatch -> {
            RequestButton(
                text = stringResource(R.string.explore_watch),
                onClick = onWatchClick,
                modifier = modifier,
            )
        }
        media.isAvailableWithoutLink -> {
            RequestButton(
                text = stringResource(R.string.explore_available),
                onClick = {},
                modifier = modifier,
                enabled = false,
            )
        }
        media.status == JellyseerrMediaStatus.PENDING -> {
            RequestButton(
                text = stringResource(R.string.explore_requested),
                onClick = {},
                modifier = modifier,
                enabled = false,
            )
        }
        media.status == JellyseerrMediaStatus.PROCESSING -> {
            RequestButton(
                text = stringResource(R.string.explore_downloading),
                onClick = {},
                modifier = modifier,
                enabled = false,
            )
        }
        media.canRequestMovie && isSubmittingRequest -> {
            RequestButton(
                text = stringResource(R.string.explore_submitting_request),
                onClick = {},
                modifier = modifier,
                enabled = false,
            )
        }
        media.canRequestMovie -> {
            RequestButton(
                text = stringResource(R.string.explore_request),
                onClick = onRequestClick,
                modifier = modifier,
            )
        }
        else -> {
            FilledTonalButton(
                onClick = {},
                enabled = false,
                modifier = modifier,
            ) {
                Text(text = stringResource(R.string.explore_series_request_select_seasons))
            }
        }
    }
}

private fun mediaDetailSubtitle(media: JellyseerrMedia): String? {
    return media.year?.let { year -> "$year - ${media.mediaType.label}" } ?: media.mediaType.label
}

@Composable
private fun ExploreActionSupportText(media: JellyseerrMedia): String? {
    return when {
        media.canWatch -> stringResource(R.string.explore_detail_watch_hint)
        media.isAvailableWithoutLink -> stringResource(R.string.explore_detail_available_hint)
        media.status == JellyseerrMediaStatus.PENDING ->
            stringResource(R.string.explore_detail_requested_hint)
        media.status == JellyseerrMediaStatus.PROCESSING ->
            stringResource(R.string.explore_detail_processing_hint)
        media.mediaType == JellyseerrMediaType.TV ->
            stringResource(R.string.explore_series_request_select_seasons_hint)
        else -> stringResource(R.string.explore_detail_request_hint)
    }
}

internal fun buildSeasonRequestButtonLabel(
    selectedSeasonNumbers: Set<Int>,
    requestableSeasonNumbers: List<Int>,
): String {
    val effectiveSelection =
        if (selectedSeasonNumbers.isEmpty()) {
            requestableSeasonNumbers
        } else {
            requestableSeasonNumbers.filter { it in selectedSeasonNumbers }
        }

    return when (effectiveSelection.size) {
        0 -> "Request all seasons"
        1 -> "Request Season ${effectiveSelection.single()}"
        requestableSeasonNumbers.size -> "Request all seasons"
        else -> "Request ${effectiveSelection.size} seasons"
    }
}

internal fun buildRequestedSeasonBadgeText(tvDetails: JellyseerrTvDetails): String? {
    val requestedSeasons =
        tvDetails.seasons
            .asSequence()
            .filter {
                it.status == JellyseerrMediaStatus.PENDING ||
                    it.status == JellyseerrMediaStatus.PROCESSING
            }
            .map { it.seasonNumber }
            .distinct()
            .sorted()
            .toList()

    if (requestedSeasons.isEmpty()) {
        return null
    }

    val allSeasonNumbers =
        tvDetails.seasons
            .asSequence()
            .map { it.seasonNumber }
            .filter { it > 0 }
            .distinct()
            .sorted()
            .toList()

    if (requestedSeasons == allSeasonNumbers && allSeasonNumbers.size > 2) {
        return "All"
    }

    return formatSeasonNumberSummary(requestedSeasons)
}

internal fun formatSeasonNumberSummary(seasonNumbers: List<Int>): String {
    if (seasonNumbers.isEmpty()) {
        return ""
    }

    val ranges = mutableListOf<String>()
    var rangeStart = seasonNumbers.first()
    var previous = seasonNumbers.first()

    for (index in 1 until seasonNumbers.size) {
        val current = seasonNumbers[index]
        if (current == previous + 1) {
            previous = current
            continue
        }

        ranges += formatSeasonRange(rangeStart, previous)
        rangeStart = current
        previous = current
    }

    ranges += formatSeasonRange(rangeStart, previous)

    return when {
        ranges.size <= 2 -> ranges.joinToString(", ")
        else -> ranges.take(2).joinToString(", ") + " +${ranges.size - 2}"
    }
}

private fun formatSeasonRange(start: Int, end: Int): String {
    return if (start == end) {
        "S$start"
    } else {
        "S$start-$end"
    }
}

private fun JellyseerrMedia.toExploreHeaderItem(): FindroidItem {
    return ExploreHeaderItem(
        title = title,
        overview = overview,
        images =
            FindroidImages(
                primary = posterUrl?.toUri(),
                backdrop = backdropUrl?.toUri(),
            ),
    )
}

private fun formatDisplayDate(value: String?): String? {
    val rawValue = value?.substringBefore('T')?.takeIf { it.isNotBlank() } ?: return null
    return runCatching {
        LocalDate.parse(rawValue)
            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
    }.getOrNull() ?: rawValue
}

private fun formatRuntime(runtimeMinutes: Int?): String? {
    val minutes = runtimeMinutes?.takeIf { it > 0 } ?: return null
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return when {
        hours == 0 -> "${remainingMinutes}m"
        remainingMinutes == 0 -> "${hours}h"
        else -> "${hours}h ${remainingMinutes}m"
    }
}

private fun formatLanguage(value: String?): String? {
    val rawValue = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val locale = Locale.forLanguageTag(rawValue.replace('_', '-'))
    val displayLanguage =
        locale.getDisplayLanguage(Locale.getDefault()).takeIf {
            it.isNotBlank() && !it.equals("und", ignoreCase = true)
        }
    return displayLanguage ?: rawValue.replaceFirstChar { it.titlecase() }
}

private fun formatListValue(values: List<String>): String? {
    return values.filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.joinToString()
}

private val JellyseerrMediaType.label: String
    get() =
        when (this) {
            JellyseerrMediaType.MOVIE -> "Movie"
            JellyseerrMediaType.TV -> "Series"
        }

private data class ExploreHeaderItem(
    private val title: String,
    override val overview: String,
    override val images: FindroidImages,
) : FindroidItem {
    override val id = UUID.nameUUIDFromBytes("$title::$overview".toByteArray())
    override val name = title
    override val originalTitle: String? = null
    override val played = false
    override val favorite = false
    override val canPlay = false
    override val canDownload = false
    override val sources: List<FindroidSource> = emptyList()
    override val runtimeTicks: Long = 0L
    override val playbackPositionTicks: Long = 0L
    override val unplayedItemCount: Int? = null
    override val chapters: List<FindroidChapter> = emptyList()
}

private data class ExploreMetadataEntry(
    val labelRes: Int,
    val value: String,
)

private data class ExploreRatingCardModel(
    val title: String,
    val subtitle: String?,
    val score: String,
    val accent: Color,
    val progress: Float,
)

@PreviewScreenSizes
@Composable
private fun ExploreMovieDetailScreenPreview() {
    FindroidTheme {
        ExploreMovieDetailScreenLayout(
            movieDetails =
                JellyseerrMovieDetails(
                    tmdbId = 603,
                    title = "The Matrix",
                    originalTitle = "The Matrix",
                    overview =
                        "A computer hacker learns about the true nature of reality and his role in the war against its controllers.",
                    tagline = "Welcome to the Real World.",
                    posterUrl = null,
                    backdropUrl = null,
                    releaseDate = "1999-03-31",
                    runtimeMinutes = 136,
                    statusText = "Released",
                    voteAverage = 8.7,
                    voteCount = 25000,
                    originalLanguage = "en",
                    genres = listOf("Action", "Science Fiction"),
                    productionCountries = listOf("United States"),
                    spokenLanguages = listOf("English"),
                    mediaStatus = JellyseerrMediaStatus.UNTRACKED,
                    jellyfinMediaId = null,
                    ratings =
                        JellyseerrRatings(
                            rottenTomatoesCriticsScore = 83,
                            rottenTomatoesAudienceScore = 85,
                            tmdbScore = 87,
                        ),
                ),
            isSubmittingRequest = false,
            errorMessage = null,
            onBackClick = {},
            onRequestClick = {},
            onWatchClick = {},
        )
    }
}

@PreviewScreenSizes
@Composable
private fun ExploreTvDetailScreenPreview() {
    FindroidTheme {
        ExploreTvDetailScreenLayout(
            tvDetails =
                JellyseerrTvDetails(
                    tmdbId = 1,
                    title = "From",
                    overview = "Unwilling residents fight to survive and search for answers.",
                    posterUrl = null,
                    backdropUrl = null,
                    firstAirDate = "2022-02-20",
                    lastAirDate = null,
                    statusText = "Returning Series",
                    voteAverage = 8.1,
                    originalLanguage = "en",
                    networks = listOf("Epix", "MGM+"),
                    productionCountries = listOf("United States"),
                    nextAirDate = "2026-05-10",
                    mediaStatus = JellyseerrMediaStatus.PARTIALLY_AVAILABLE,
                    jellyfinMediaId = "series-id",
                    ratings =
                        JellyseerrRatings(
                            rottenTomatoesCriticsScore = 97,
                            rottenTomatoesAudienceScore = 81,
                            tmdbScore = 82,
                        ),
                    seasons =
                        listOf(
                            JellyseerrTvSeason(
                                seasonNumber = 4,
                                name = "Season 4",
                                overview = "",
                                airDate = "2026-01-01",
                                episodeCount = 10,
                                posterUrl = null,
                                status = JellyseerrMediaStatus.UNTRACKED,
                            ),
                            JellyseerrTvSeason(
                                seasonNumber = 3,
                                name = "Season 3",
                                overview = "",
                                airDate = "2025-01-01",
                                episodeCount = 10,
                                posterUrl = null,
                                status = JellyseerrMediaStatus.AVAILABLE,
                            ),
                        ),
                ),
            expandedSeasonNumber = 4,
            seasonDetails =
                mapOf(
                    4 to
                        ExploreSeasonDetailState.Success(
                            dev.jdtech.jellyfin.jellyseerr.model.JellyseerrTvSeasonDetails(
                                seasonNumber = 4,
                                name = "Season 4",
                                overview = "",
                                airDate = "2026-01-01",
                                posterUrl = null,
                                episodes =
                                    listOf(
                                        JellyseerrTvEpisode(
                                            episodeNumber = 10,
                                            name = "If a Tree Falls in the Forest...",
                                            overview = "",
                                            airDate = "2026-06-21",
                                        )
                                    ),
                            )
                        )
                ),
            selectedSeasonNumbers = setOf(4),
            isSubmittingRequest = false,
            errorMessage = null,
            onBackClick = {},
            onWatchClick = {},
            onSeasonExpandToggle = {},
            onSeasonSelectionToggle = {},
            onSelectAllClick = {},
            onClearSelectionClick = {},
            onSubmitRequestClick = {},
        )
    }
}
