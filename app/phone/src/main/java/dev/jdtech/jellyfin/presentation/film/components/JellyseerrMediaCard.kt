package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.jdtech.jellyfin.R
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrMedia
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrMediaStatus
import dev.jdtech.jellyfin.presentation.theme.spacings

@Composable
fun JellyseerrMediaCard(
    media: JellyseerrMedia,
    onClick: (JellyseerrMedia) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier.width(150.dp).clickable { onClick(media) }
    ) {
        Surface(
            modifier = Modifier.clip(MaterialTheme.shapes.small),
            shape = MaterialTheme.shapes.small,
        ) {
            Box {
                AsyncImage(
                    model = media.posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.aspectRatio(0.66f),
                    placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceContainer),
                )

                ExploreStatusIcon(
                    media = media,
                    modifier =
                        Modifier.padding(MaterialTheme.spacings.extraSmall),
                )
            }
        }
        Spacer(modifier = Modifier.height(MaterialTheme.spacings.extraSmall))
        Text(
            text = media.title,
            style = MaterialTheme.typography.bodyMedium,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (media.year != null || media.statusLabelRes != null) {
            Row {
                media.year?.let { year ->
                    Text(
                        text = year,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                        maxLines = 1,
                    )
                }
                media.statusLabelRes?.let { statusLabel ->
                    val statusTextColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f)
                    if (media.year != null) {
                        Text(
                            text = "  •  ",
                            style = MaterialTheme.typography.bodySmall,
                            color = statusTextColor,
                            maxLines = 1,
                        )
                    }
                    Text(
                        text = stringResource(statusLabel),
                        style = MaterialTheme.typography.bodySmall,
                        color = statusTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExploreStatusIcon(media: JellyseerrMedia, modifier: Modifier = Modifier) {
    val iconRes =
        when (media.status) {
            JellyseerrMediaStatus.PARTIALLY_AVAILABLE,
            JellyseerrMediaStatus.AVAILABLE -> CoreR.drawable.ic_check
            JellyseerrMediaStatus.PENDING -> CoreR.drawable.ic_clock
            JellyseerrMediaStatus.PROCESSING ->
                CoreR.drawable.ic_download
            else -> null
        } ?: return

    val containerColor =
        when (media.status) {
            JellyseerrMediaStatus.PARTIALLY_AVAILABLE,
            JellyseerrMediaStatus.AVAILABLE -> MaterialTheme.colorScheme.primaryContainer
            JellyseerrMediaStatus.PENDING,
            JellyseerrMediaStatus.PROCESSING -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHighest
        }

    val contentColor =
        when (media.status) {
            JellyseerrMediaStatus.PARTIALLY_AVAILABLE,
            JellyseerrMediaStatus.AVAILABLE -> MaterialTheme.colorScheme.onPrimaryContainer
            JellyseerrMediaStatus.PENDING,
            JellyseerrMediaStatus.PROCESSING -> MaterialTheme.colorScheme.onSecondaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }

    Box(
        modifier =
            modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(containerColor),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(16.dp).align(Alignment.Center),
            tint = contentColor,
        )
    }
}

private val JellyseerrMedia.statusLabelRes: Int?
    get() =
        when {
            canWatch || isAvailableWithoutLink -> R.string.explore_available
            status == JellyseerrMediaStatus.PENDING -> R.string.explore_requested
            status == JellyseerrMediaStatus.PROCESSING -> R.string.explore_downloading
            else -> null
        }
