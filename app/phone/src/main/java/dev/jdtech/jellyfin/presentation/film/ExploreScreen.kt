package dev.jdtech.jellyfin.presentation.film

import android.widget.Toast
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.R
import dev.jdtech.jellyfin.film.presentation.explore.ExploreEvent
import dev.jdtech.jellyfin.film.presentation.explore.ExploreUiState
import dev.jdtech.jellyfin.film.presentation.explore.ExploreViewModel
import dev.jdtech.jellyfin.film.presentation.explore.RecentRequestsState
import dev.jdtech.jellyfin.film.presentation.explore.SectionState
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrMedia
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrRecentRequest
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.presentation.components.ErrorDialog
import dev.jdtech.jellyfin.presentation.film.components.ErrorCard
import dev.jdtech.jellyfin.presentation.film.components.ExploreSearchOverlay
import dev.jdtech.jellyfin.presentation.film.components.ExploreSearchBar
import dev.jdtech.jellyfin.presentation.film.components.MediaSectionRow
import dev.jdtech.jellyfin.presentation.film.components.RecentRequestsSection
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.presentation.utils.rememberSafePadding
import dev.jdtech.jellyfin.utils.ObserveAsEvents

@Composable
fun ExploreScreen(
    onItemClick: (FindroidItem) -> Unit,
    onMediaClick: (JellyseerrMedia) -> Unit,
    viewModel: ExploreViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(true) { viewModel.loadData() }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is ExploreEvent.NavigateToItem -> onItemClick(event.item)
            is ExploreEvent.ShowMessage ->
                Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
        }
    }

    ExploreScreenLayout(
        state = state,
        onRetryClick = { viewModel.loadData(isRefreshing = true) },
        onSearchQueryChanged = viewModel::search,
        onMediaClick = onMediaClick,
        onCancelRecentRequest = viewModel::cancelRecentRequest,
    )
}

@Composable
private fun ExploreScreenLayout(
    state: ExploreUiState,
    onRetryClick: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onMediaClick: (JellyseerrMedia) -> Unit,
    onCancelRecentRequest: (JellyseerrRecentRequest) -> Unit,
) {
    val safePadding = rememberSafePadding(handleStartInsets = false)
    val paddingStart = safePadding.start + MaterialTheme.spacings.default
    val paddingTop = safePadding.top + MaterialTheme.spacings.default
    val paddingEnd = safePadding.end + MaterialTheme.spacings.default
    val paddingBottom = safePadding.bottom + MaterialTheme.spacings.default
    val itemsPadding = PaddingValues(start = paddingStart, end = paddingEnd)
    val density = LocalDensity.current
    var headerHeightPx by remember { mutableStateOf(0) }
    val contentPaddingTop by
        animateDpAsState(
            targetValue =
                if (headerHeightPx == 0) {
                    safePadding.top + 140.dp
                } else {
                    with(density) { headerHeightPx.toDp() } + MaterialTheme.spacings.small
                },
            label = "explore_content_padding_top",
        )
    var showErrorDialog by rememberSaveable { mutableStateOf(false) }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    val sectionSpacing = MaterialTheme.spacings.medium

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.authRequired -> {
                ExplorePlaceholderMessage(
                    message = stringResource(R.string.explore_login_required),
                    modifier =
                        Modifier.align(Alignment.TopStart)
                            .padding(
                                start = paddingStart,
                                top = paddingTop,
                                end = paddingEnd,
                            ),
                )
            }
            state.authError -> {
                Column(
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(
                                start = paddingStart,
                                top = paddingTop,
                                end = paddingEnd,
                            ),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
                ) {
                    ErrorCard(
                        onShowStacktrace = { showErrorDialog = true },
                        onRetryClick = onRetryClick,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    state.error?.message?.takeIf { it.isNotBlank() }?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                        )
                    }
                }
            }
            else -> {
                PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = onRetryClick) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (!searchExpanded) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding =
                                    PaddingValues(
                                        top = contentPaddingTop,
                                        bottom = paddingBottom,
                                ),
                                verticalArrangement = Arrangement.spacedBy(sectionSpacing),
                            ) {
                                if (state.recentRequests !is RecentRequestsState.Hidden) {
                                    item {
                                        RecentRequestsSection(
                                            state = state.recentRequests,
                                            itemsPadding = itemsPadding,
                                            onMediaClick = onMediaClick,
                                            onCancelRequest = onCancelRecentRequest,
                                        )
                                    }
                                }
                                item {
                                    MediaSectionRow(
                                        title = stringResource(R.string.explore_trending),
                                        state = state.trending,
                                        itemsPadding = itemsPadding,
                                        onMediaClick = onMediaClick,
                                    )
                                }
                                item {
                                    MediaSectionRow(
                                        title = stringResource(R.string.explore_popular_movies),
                                        state = state.popularMovies,
                                        itemsPadding = itemsPadding,
                                        onMediaClick = onMediaClick,
                                    )
                                }
                                item {
                                    MediaSectionRow(
                                        title = stringResource(R.string.explore_popular_series),
                                        state = state.popularSeries,
                                        itemsPadding = itemsPadding,
                                        onMediaClick = onMediaClick,
                                    )
                                }
                                item {
                                    MediaSectionRow(
                                        title = stringResource(R.string.explore_upcoming_series),
                                        state = state.upcomingSeries,
                                        itemsPadding = itemsPadding,
                                        onMediaClick = onMediaClick,
                                    )
                                }
                                item { Spacer(modifier = Modifier.height(MaterialTheme.spacings.small)) }
                            }
                        } else {
                            ExploreSearchOverlay(
                                state = state.search,
                                onMediaClick = onMediaClick,
                                modifier =
                                    Modifier.fillMaxSize()
                                        .padding(top = contentPaddingTop),
                            )
                        }

                        Column(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .zIndex(1f)
                                    .onSizeChanged { headerHeightPx = it.height }
                                    .padding(
                                        start = paddingStart,
                                        top = paddingTop,
                                        end = paddingEnd,
                                    ),
                            verticalArrangement =
                                Arrangement.spacedBy(MaterialTheme.spacings.small),
                        ) {
                            ExploreSearchBar(
                                state = state.search,
                                expanded = searchExpanded,
                                onExpand = { searchExpanded = it },
                                onQueryChanged = onSearchQueryChanged,
                                modifier = Modifier.fillMaxWidth(),
                                placeholderText =
                                    stringResource(R.string.explore_search_placeholder),
                            )
                        }
                    }
                }
            }
        }

        val error = state.error
        if (showErrorDialog && error != null) {
            ErrorDialog(
                exception = error,
                onDismissRequest = { showErrorDialog = false },
            )
        }
    }
}

@Composable
private fun ExplorePlaceholderMessage(
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
    ) {
        androidx.compose.material3.Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
        )
    }
}

@PreviewScreenSizes
@Composable
private fun ExploreScreenLayoutPreview() {
    FindroidTheme {
        ExploreScreenLayout(
            state =
                ExploreUiState(
                    recentRequests = dev.jdtech.jellyfin.film.presentation.explore.RecentRequestsState.Loading,
                    trending = SectionState.Loading,
                    popularMovies = SectionState.Loading,
                    popularSeries = SectionState.Loading,
                    upcomingSeries = SectionState.Loading,
                ),
            onRetryClick = {},
            onSearchQueryChanged = {},
            onMediaClick = {},
            onCancelRecentRequest = {},
        )
    }
}
