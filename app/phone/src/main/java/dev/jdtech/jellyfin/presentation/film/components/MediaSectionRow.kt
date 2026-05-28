package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.R
import dev.jdtech.jellyfin.film.presentation.explore.SectionState
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrMedia
import dev.jdtech.jellyfin.presentation.theme.spacings

@Composable
fun MediaSectionRow(
    title: String,
    state: SectionState,
    itemsPadding: PaddingValues,
    onMediaClick: (JellyseerrMedia) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Box(modifier = Modifier.fillMaxWidth().height(42.dp).padding(itemsPadding)) {
            Text(
                text = title,
                modifier = Modifier.align(Alignment.CenterStart),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(modifier = Modifier.height(MaterialTheme.spacings.extraSmall))
        when (state) {
            is SectionState.Loading -> {
                LazyRow(
                    contentPadding = itemsPadding,
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
                ) {
                    items(4) {
                        Column(modifier = Modifier.width(150.dp)) {
                            Box(
                                modifier =
                                    Modifier.aspectRatio(0.66f)
                                        .clip(MaterialTheme.shapes.small)
                                        .background(MaterialTheme.colorScheme.surfaceContainer)
                            )
                        }
                    }
                }
            }
            is SectionState.Success -> {
                LazyRow(
                    contentPadding = itemsPadding,
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
                ) {
                    items(state.items, key = { "${it.mediaType}-${it.tmdbId}" }) { item ->
                        JellyseerrMediaCard(
                            media = item,
                            onClick = onMediaClick,
                        )
                    }
                }
            }
            is SectionState.Empty -> {
                Text(
                    text = stringResource(R.string.explore_empty),
                    modifier = Modifier.padding(itemsPadding),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                )
            }
            is SectionState.Error -> {
                Box(
                    modifier =
                        Modifier.padding(itemsPadding)
                            .clip(MaterialTheme.shapes.medium)
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
