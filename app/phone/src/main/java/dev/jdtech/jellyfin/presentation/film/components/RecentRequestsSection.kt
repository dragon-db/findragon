package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.jdtech.jellyfin.R
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.film.presentation.explore.RecentRequestsState
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrMedia
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrMediaType
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrRecentRequest
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrRecentRequestDisplayStatus
import dev.jdtech.jellyfin.presentation.theme.spacings

@Composable
fun RecentRequestsSection(
    state: RecentRequestsState,
    itemsPadding: PaddingValues,
    onMediaClick: (JellyseerrMedia) -> Unit,
    onCancelRequest: (JellyseerrRecentRequest) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state is RecentRequestsState.Hidden) {
        return
    }

    Column(modifier = modifier) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(itemsPadding)
                    .height(42.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.explore_recent_requests),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(modifier = Modifier.height(MaterialTheme.spacings.extraSmall))

        when (state) {
            RecentRequestsState.Hidden -> Unit
            RecentRequestsState.Loading -> RecentRequestsLoadingRow(itemsPadding = itemsPadding)
            is RecentRequestsState.Success -> {
                LazyRow(
                    contentPadding = itemsPadding,
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
                ) {
                    items(state.items, key = { it.requestId }) { item ->
                        RecentRequestCard(
                            request = item,
                            onClick = { onMediaClick(item.media) },
                            onCancelClick = { onCancelRequest(item) },
                        )
                    }
                }
            }
            is RecentRequestsState.Error -> {
                Box(
                    modifier =
                        Modifier.padding(itemsPadding)
                            .clip(MaterialTheme.shapes.large)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.explore_section_unavailable),
                        modifier = Modifier.padding(MaterialTheme.spacings.default),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentRequestsLoadingRow(itemsPadding: PaddingValues) {
    LazyRow(
        contentPadding = itemsPadding,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
    ) {
        items(3) {
            Surface(
                modifier = Modifier.width(336.dp).height(190.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Box(
                    modifier =
                        Modifier.fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.surfaceContainerHigh,
                                        MaterialTheme.colorScheme.surfaceContainerLow,
                                    )
                                )
                            )
                )
            }
        }
    }
}

@Composable
private fun RecentRequestCard(
    request: JellyseerrRecentRequest,
    onClick: () -> Unit,
    onCancelClick: () -> Unit,
) {
    Surface(
        modifier =
            Modifier.width(336.dp)
                .height(190.dp)
                .clip(MaterialTheme.shapes.large)
                .clickable(onClick = onClick)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                    shape = MaterialTheme.shapes.large,
                ),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Box {
            AsyncImage(
                model = request.media.backdropUrl ?: request.media.posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceContainer),
            )
            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.76f),
                                    Color.Black.copy(alpha = 0.62f),
                                    Color.Black.copy(alpha = 0.34f),
                                )
                            )
                        )
            )
            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.18f),
                                0.62f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.5f),
                            )
                        )
            )

            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .padding(MaterialTheme.spacings.default),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = mediaTypeLabel(request.media),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.76f),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        requestSeasonLabel(request)?.let { label ->
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White.copy(alpha = 0.76f),
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                        Text(
                            text = request.media.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RecentRequestStatusPill(
                        request = request,
                        modifier = Modifier.weight(1f),
                    )
                    if (request.canCancel) {
                        CancelRequestButton(
                            onClick = onCancelClick,
                            modifier = Modifier.weight(0.9f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentRequestStatusPill(
    request: JellyseerrRecentRequest,
    modifier: Modifier = Modifier,
) {
    val spec = request.displayStatus.statusPillSpec()

    Surface(
        modifier = modifier.heightIn(min = 32.dp),
        shape = MaterialTheme.shapes.medium,
        color = spec.containerColor,
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(spec.iconRes),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = spec.contentColor,
            )
            Text(
                text = stringResource(spec.labelRes),
                style = MaterialTheme.typography.labelLarge,
                color = spec.contentColor,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CancelRequestButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .heightIn(min = 32.dp)
                .widthIn(min = 92.dp)
                .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(CoreR.drawable.ic_x),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = stringResource(R.string.explore_cancel_request),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun JellyseerrRecentRequestDisplayStatus.statusPillSpec(): RecentRequestPillSpec {
    return when (this) {
        JellyseerrRecentRequestDisplayStatus.PENDING ->
            RecentRequestPillSpec(
                labelRes = R.string.explore_pending,
                iconRes = CoreR.drawable.ic_clock,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        JellyseerrRecentRequestDisplayStatus.PROCESSING ->
            RecentRequestPillSpec(
                labelRes = R.string.explore_downloading,
                iconRes = CoreR.drawable.ic_download,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        JellyseerrRecentRequestDisplayStatus.AVAILABLE ->
            RecentRequestPillSpec(
                labelRes = R.string.explore_available,
                iconRes = CoreR.drawable.ic_check,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        JellyseerrRecentRequestDisplayStatus.DECLINED ->
            RecentRequestPillSpec(
                labelRes = R.string.explore_declined,
                iconRes = CoreR.drawable.ic_x,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        JellyseerrRecentRequestDisplayStatus.FAILED ->
            RecentRequestPillSpec(
                labelRes = R.string.explore_failed,
                iconRes = CoreR.drawable.ic_x,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        JellyseerrRecentRequestDisplayStatus.COMPLETED ->
            RecentRequestPillSpec(
                labelRes = R.string.explore_completed,
                iconRes = CoreR.drawable.ic_check,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
    }
}

@Composable
private fun mediaTypeLabel(media: JellyseerrMedia): String {
    return buildString {
        media.year?.let { append(it) }
        if (isNotEmpty()) {
            append(" - ")
        }
        append(
            when (media.mediaType) {
                JellyseerrMediaType.MOVIE -> stringResource(R.string.explore_movie)
                JellyseerrMediaType.TV -> stringResource(R.string.explore_series)
            }
        )
    }
}

@Composable
private fun requestSeasonLabel(request: JellyseerrRecentRequest): String? {
    if (request.media.mediaType != JellyseerrMediaType.TV) {
        return null
    }

    if (request.seasonNumbers.isEmpty()) {
        return stringResource(R.string.explore_recent_request_all_seasons)
    }

    return when (request.seasonNumbers.size) {
        1 -> stringResource(R.string.explore_recent_request_season_single, request.seasonNumbers.single())
        2 -> request.seasonNumbers.joinToString(", ") { "S$it" }
        else -> stringResource(R.string.explore_recent_request_season_count, request.seasonNumbers.size)
    }
}

private data class RecentRequestPillSpec(
    val labelRes: Int,
    val iconRes: Int,
    val containerColor: Color,
    val contentColor: Color,
)
