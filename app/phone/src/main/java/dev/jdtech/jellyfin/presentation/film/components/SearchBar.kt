package dev.jdtech.jellyfin.presentation.film.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.R
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.film.R as FilmR
import dev.jdtech.jellyfin.film.presentation.explore.ExploreSearchState
import dev.jdtech.jellyfin.film.presentation.search.SearchAction
import dev.jdtech.jellyfin.film.presentation.search.SearchState
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrMedia
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.presentation.utils.GridCellsAdaptiveWithMinColumns
import dev.jdtech.jellyfin.presentation.utils.rememberSafePadding
import kotlinx.coroutines.delay

@Composable
fun FilmSearchBar(
    state: SearchState,
    expanded: Boolean,
    onExpand: (Boolean) -> Unit,
    onAction: (SearchAction) -> Unit,
    modifier: Modifier = Modifier,
    paddingStart: Dp = 0.dp,
    paddingEnd: Dp = 0.dp,
    placeholderText: String? = null,
    emptyStateText: String? = null,
) {
    val safePadding = rememberSafePadding()

    BaseFilmSearchBar(
        loading = state.loading,
        expanded = expanded,
        onExpand = onExpand,
        onQueryChanged = { onAction(SearchAction.Search(it)) },
        modifier = modifier,
        paddingStart = paddingStart,
        paddingEnd = paddingEnd,
        placeholderText = placeholderText,
    ) {
        if (emptyStateText != null) {
            SearchBarMessage(text = emptyStateText)
        } else {
            LazyVerticalGrid(
                columns = GridCellsAdaptiveWithMinColumns(minSize = 160.dp, minColumns = 2),
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        start = safePadding.start + MaterialTheme.spacings.default,
                        top = MaterialTheme.spacings.default,
                        end = safePadding.end + MaterialTheme.spacings.default,
                        bottom = safePadding.bottom + MaterialTheme.spacings.default,
                    ),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
            ) {
                items(items = state.items, key = { it.id }) { item ->
                    ItemCard(
                        item = item,
                        direction = Direction.VERTICAL,
                        onClick = { onAction(SearchAction.OnItemClick(item)) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ExploreSearchBar(
    state: ExploreSearchState,
    expanded: Boolean,
    onExpand: (Boolean) -> Unit,
    onQueryChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    paddingStart: Dp = 0.dp,
    paddingEnd: Dp = 0.dp,
    placeholderText: String? = null,
) {
    val focusRequester = remember { FocusRequester() }
    var query by rememberSaveable { mutableStateOf("") }
    val collapseSearch = {
        query = ""
        onExpand(false)
    }

    BackHandler(enabled = expanded) { collapseSearch() }

    LaunchedEffect(expanded) {
        if (expanded) {
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(query) {
        if (query.isNotBlank()) {
            delay(minOf(50L + (query.count() * 50L), 300L))
        }
        onQueryChanged(query)
    }

    Column(
        modifier =
            modifier
                .padding(start = paddingStart, end = paddingEnd),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color =
                if (expanded) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
            tonalElevation = if (expanded) 6.dp else 0.dp,
            shadowElevation = if (expanded) 8.dp else 0.dp,
        ) {
            SearchBarDefaults.InputField(
                query = query,
                onQueryChange = {
                    query = it
                    if (!expanded) {
                        onExpand(true)
                    }
                },
                onSearch = { onExpand(true) },
                expanded = expanded,
                onExpandedChange = { onExpand(it) },
                modifier =
                    Modifier.fillMaxWidth()
                        .focusRequester(focusRequester),
                placeholder = {
                    Text(
                        text = placeholderText ?: stringResource(FilmR.string.search_placeholder),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                    )
                },
                leadingIcon = {
                    AnimatedContent(targetState = expanded, label = "explore_search_nav_icon") {
                        targetExpanded ->
                        if (targetExpanded) {
                            IconButton(onClick = collapseSearch) {
                                Icon(
                                    painter = painterResource(CoreR.drawable.ic_arrow_left),
                                    contentDescription = null,
                                )
                            }
                        } else {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_search),
                                contentDescription = null,
                            )
                        }
                    }
                },
                trailingIcon = {
                    when {
                        state is ExploreSearchState.Loading -> {
                            Box(modifier = Modifier.size(32.dp)) {
                                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                            }
                        }
                        query.isNotEmpty() -> {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    painter = painterResource(CoreR.drawable.ic_x),
                                    contentDescription = null,
                                )
                            }
                        }
                    }
                },
            )
        }
    }
}

@Composable
fun ExploreSearchOverlay(
    state: ExploreSearchState,
    onMediaClick: (JellyseerrMedia) -> Unit,
    modifier: Modifier = Modifier,
) {
    val safePadding = rememberSafePadding()

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.background,
    ) {
        when (state) {
            ExploreSearchState.Idle -> {
                SearchBarMessage(
                    text = stringResource(R.string.explore_search_hint),
                    modifier = Modifier.fillMaxSize(),
                )
            }
            ExploreSearchState.Loading -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }
            is ExploreSearchState.Empty -> {
                SearchBarMessage(
                    text = stringResource(R.string.explore_search_empty),
                    modifier = Modifier.fillMaxSize(),
                )
            }
            is ExploreSearchState.Error -> {
                SearchBarMessage(
                    text =
                        state.message.ifBlank {
                            stringResource(R.string.explore_search_error)
                        },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            is ExploreSearchState.Success -> {
                LazyVerticalGrid(
                    columns = GridCellsAdaptiveWithMinColumns(minSize = 148.dp, minColumns = 2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding =
                        PaddingValues(
                            start = safePadding.start + MaterialTheme.spacings.default,
                            top = MaterialTheme.spacings.default,
                            end = safePadding.end + MaterialTheme.spacings.default,
                            bottom = safePadding.bottom + MaterialTheme.spacings.default,
                        ),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
                ) {
                    items(
                        items = state.items,
                        key = { "${it.mediaType.apiValue}-${it.tmdbId}" },
                    ) { item ->
                        JellyseerrMediaCard(
                            media = item,
                            onClick = onMediaClick,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun BaseFilmSearchBar(
    loading: Boolean,
    expanded: Boolean,
    onExpand: (Boolean) -> Unit,
    onQueryChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    paddingStart: Dp = 0.dp,
    paddingEnd: Dp = 0.dp,
    placeholderText: String? = null,
    content: @Composable () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val safePadding = rememberSafePadding()

    var query by rememberSaveable { mutableStateOf("") }

    val searchBarPaddingStart by
        animateDpAsState(
            targetValue = if (expanded) 0.dp else paddingStart,
            label = "search_bar_padding_start",
        )

    val searchBarPaddingEnd by
        animateDpAsState(
            targetValue = if (expanded) 0.dp else paddingEnd,
            label = "search_bar_padding_end",
        )

    val searchBarInputPaddingStart by
        animateDpAsState(
            targetValue = if (expanded) safePadding.start else 0.dp,
            label = "search_bar_padding_start",
        )

    val searchBarInputPaddingEnd by
        animateDpAsState(
            targetValue = if (expanded) safePadding.end else 0.dp,
            label = "search_bar_padding_end",
        )

    LaunchedEffect(expanded) {
        if (expanded) {
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(query) {
        if (query.isNotBlank()) {
            // Debounce scales with input length. Max debounce of 300ms.
            delay(minOf(50L + (query.count() * 50L), 300L))
        }
        onQueryChanged(query)
    }

    SearchBar(
        inputField = {
            SearchBarDefaults.InputField(
                query = query,
                onQueryChange = { query = it },
                onSearch = { onExpand(true) },
                expanded = expanded,
                onExpandedChange = { onExpand(it) },
                modifier =
                    Modifier.padding(
                            start = searchBarInputPaddingStart,
                            end = searchBarInputPaddingEnd,
                        )
                        .focusRequester(focusRequester),
                placeholder = {
                    Text(
                        text = placeholderText ?: stringResource(FilmR.string.search_placeholder),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                    )
                },
                leadingIcon = {
                    AnimatedContent(targetState = expanded, label = "search_to_back") {
                        targetExpanded ->
                        if (targetExpanded) {
                            IconButton(onClick = { onExpand(false) }) {
                                Icon(
                                    painter = painterResource(CoreR.drawable.ic_arrow_left),
                                    contentDescription = null,
                                )
                            }
                        } else {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_search),
                                contentDescription = null,
                            )
                        }
                    }
                },
                trailingIcon = {
                    if (loading) {
                        Box(modifier = Modifier.size(32.dp)) { CircularProgressIndicator() }
                    } else if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_x),
                                contentDescription = null,
                            )
                        }
                    }
                },
            )
        },
        expanded = expanded,
        onExpandedChange = { onExpand(it) },
        modifier = modifier.padding(start = searchBarPaddingStart, end = searchBarPaddingEnd),
    ) { content() }
}

@Composable
private fun SearchBarMessage(text: String, modifier: Modifier = Modifier) {
    val safePadding = rememberSafePadding()

    Box(modifier = modifier.fillMaxSize()) {
        Text(
            text = text,
            modifier =
                Modifier.align(Alignment.TopStart)
                    .padding(
                        start = safePadding.start + MaterialTheme.spacings.default,
                        top = MaterialTheme.spacings.default,
                        end = safePadding.end + MaterialTheme.spacings.default,
                    ),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
