package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.R
import dev.jdtech.jellyfin.film.presentation.issue.IssueReportForm
import dev.jdtech.jellyfin.film.presentation.issue.IssueReportTarget
import dev.jdtech.jellyfin.film.presentation.issue.IssueReporterState
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrIssue
import dev.jdtech.jellyfin.jellyseerr.model.JellyseerrIssueType
import dev.jdtech.jellyfin.presentation.theme.spacings

@Composable
fun IssueReporterDialogs(
    state: IssueReporterState,
    onIssueClick: (Int) -> Unit,
    onReportNewClick: () -> Unit,
    onSubmit: (IssueReportForm) -> Unit,
    onDismiss: () -> Unit,
) {
    if (state.showExistingIssues) {
        ExistingIssuesDialog(
            issues = state.sortedIssues,
            selectedIssue = state.selectedIssue,
            onIssueClick = onIssueClick,
            onReportNewClick = onReportNewClick,
            onDismiss = onDismiss,
        )
    }
    val target = state.target
    if (state.showReportDialog && target != null) {
        ReportIssueDialog(
            target = target,
            isSubmitting = state.isSubmitting,
            onSubmit = onSubmit,
            onDismiss = onDismiss,
        )
    }
}

@Composable
fun ExistingIssuesDialog(
    issues: List<JellyseerrIssue>,
    selectedIssue: JellyseerrIssue?,
    onIssueClick: (Int) -> Unit,
    onReportNewClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    IssueDialogFrame(
        title = stringResource(R.string.issue_existing_title),
        onDismiss = onDismiss,
        negativeButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.issue_close)) } },
        positiveButton = {
            TextButton(onClick = onReportNewClick) { Text(stringResource(R.string.issue_report_new)) }
        },
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
        ) {
            issues.forEachIndexed { index, issue ->
                IssueRow(
                    issue = issue,
                    expanded = selectedIssue?.id == issue.id,
                    onClick = { onIssueClick(issue.id) },
                )
                if (index != issues.lastIndex) {
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun IssueRow(
    issue: JellyseerrIssue,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = MaterialTheme.spacings.small)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = issue.type.label(), style = MaterialTheme.typography.titleSmall)
            Text(text = issue.contextLabel(), style = MaterialTheme.typography.bodySmall)
        }
        if (issue.message.isNotBlank()) {
            Spacer(Modifier.height(MaterialTheme.spacings.extraSmall))
            Text(
                text = issue.message,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportIssueDialog(
    target: IssueReportTarget,
    isSubmitting: Boolean,
    onSubmit: (IssueReportForm) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedType by remember { mutableStateOf(JellyseerrIssueType.VIDEO) }
    var message by remember { mutableStateOf("") }
    var selectedSeason by remember(target) { mutableIntStateOf(target.initialSeasonNumber) }
    var selectedEpisode by remember(target) { mutableIntStateOf(target.initialEpisodeNumber) }

    IssueDialogFrame(
        title = stringResource(R.string.issue_report_title),
        onDismiss = onDismiss,
        negativeButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.issue_close)) } },
        positiveButton = {
            Button(
                enabled = !isSubmitting && message.isNotBlank(),
                onClick = {
                    onSubmit(
                        IssueReportForm(
                            type = selectedType,
                            message = message,
                            seasonNumber = selectedSeason,
                            episodeNumber = selectedEpisode,
                        )
                    )
                },
            ) {
                Text(
                    text =
                        if (isSubmitting) {
                            stringResource(R.string.issue_submitting)
                        } else {
                            stringResource(R.string.issue_submit)
                        }
                )
            }
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.extraSmall)) {
                Text(text = target.mediaTypeLabel(), style = MaterialTheme.typography.labelLarge)
                Text(text = target.title, style = MaterialTheme.typography.titleMedium)
            }
            if (target.isSeries) {
                SeasonDropdown(
                    target = target,
                    selectedSeason = selectedSeason,
                    onSelect = {
                        selectedSeason = it
                        selectedEpisode = 0
                    },
                )
                EpisodeDropdown(
                    target = target,
                    selectedSeason = selectedSeason,
                    selectedEpisode = selectedEpisode,
                    onSelect = { selectedEpisode = it },
                )
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
                maxItemsInEachRow = 2,
            ) {
                JellyseerrIssueType.entries.forEach { type ->
                    IssueTypeRow(
                        type = type,
                        selected = selectedType == type,
                        onSelect = { selectedType = type },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 128.dp),
                label = { Text(stringResource(R.string.issue_message_label)) },
                placeholder = { Text(stringResource(R.string.issue_message_placeholder)) },
                minLines = 4,
            )
        }
    }
}

@Composable
private fun IssueDialogFrame(
    title: String,
    onDismiss: () -> Unit,
    negativeButton: @Composable () -> Unit,
    positiveButton: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacings.medium)) {
            Card(
                modifier =
                    Modifier.fillMaxWidth()
                        .heightIn(max = maxHeight * 0.9f),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = MaterialTheme.spacings.default)) {
                    Text(
                        text = title,
                        modifier =
                            Modifier.padding(horizontal = MaterialTheme.spacings.default)
                                .fillMaxWidth(),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Spacer(modifier = Modifier.height(MaterialTheme.spacings.medium))
                    Column(
                        modifier =
                            Modifier.fillMaxWidth()
                                .weight(1f, fill = false)
                                .padding(horizontal = MaterialTheme.spacings.default)
                    ) {
                        content()
                    }
                    Spacer(modifier = Modifier.height(MaterialTheme.spacings.default))
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(horizontal = MaterialTheme.spacings.default),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        negativeButton()
                        positiveButton()
                    }
                    Spacer(modifier = Modifier.height(MaterialTheme.spacings.default))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeasonDropdown(
    target: IssueReportTarget,
    selectedSeason: Int,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val seasons = target.seasons.sortedByDescending { it.seasonNumber }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = stringResource(R.string.issue_season_number, selectedSeason),
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            label = { Text(stringResource(R.string.issue_affected_season)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            seasons.forEach { season ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.issue_season_number, season.seasonNumber)) },
                    onClick = {
                        onSelect(season.seasonNumber)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EpisodeDropdown(
    target: IssueReportTarget,
    selectedSeason: Int,
    selectedEpisode: Int,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val episodeCount = target.seasons.firstOrNull { it.seasonNumber == selectedSeason }?.episodeCount ?: 0
    val episodes = (1..episodeCount).toList().let { values ->
        if (selectedEpisode > 0 && selectedEpisode !in values) values + selectedEpisode else values
    }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value =
                if (selectedEpisode == 0) {
                    stringResource(R.string.issue_all_episodes)
                } else {
                    stringResource(R.string.issue_episode_number, selectedEpisode)
                },
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            label = { Text(stringResource(R.string.issue_affected_episode)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.issue_all_episodes)) },
                onClick = {
                    onSelect(0)
                    expanded = false
                },
            )
            episodes.sorted().forEach { episodeNumber ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.issue_episode_number, episodeNumber)) },
                    onClick = {
                        onSelect(episodeNumber)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun IssueTypeRow(
    type: JellyseerrIssueType,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .selectable(selected = selected, onClick = onSelect)
                .padding(vertical = MaterialTheme.spacings.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(text = type.label(), style = MaterialTheme.typography.bodyLarge)
    }
}

private fun IssueReportTarget.mediaTypeLabel(): String {
    return when (mediaType) {
        dev.jdtech.jellyfin.film.presentation.issue.IssueReportMediaType.MOVIE -> "Movie"
        dev.jdtech.jellyfin.film.presentation.issue.IssueReportMediaType.SERIES -> "Series"
    }
}

private fun JellyseerrIssueType.label(): String {
    return when (this) {
        JellyseerrIssueType.VIDEO -> "Video"
        JellyseerrIssueType.AUDIO -> "Audio"
        JellyseerrIssueType.SUBTITLE -> "Subtitle"
        JellyseerrIssueType.OTHER -> "Other"
    }
}

private fun JellyseerrIssue.contextLabel(): String {
    return when {
        problemSeason == 0 -> ""
        problemEpisode == 0 -> "S$problemSeason - All"
        else -> "S$problemSeason E$problemEpisode"
    }
}
