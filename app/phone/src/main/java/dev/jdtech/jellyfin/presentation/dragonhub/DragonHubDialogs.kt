package dev.jdtech.jellyfin.presentation.dragonhub

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.jdtech.jellyfin.R
import dev.jdtech.jellyfin.dragonhub.api.dto.DragonHubNotificationDto
import dev.jdtech.jellyfin.dragonhub.api.dto.DragonHubVersionResponseDto
import dev.jdtech.jellyfin.presentation.theme.spacings
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import dev.jdtech.jellyfin.core.R as CoreR

@Composable
fun UpdateAvailableDialog(
    update: DragonHubVersionResponseDto,
    onUpdateClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(enabled = update.forceUpdate) {}

    AlertDialog(
        onDismissRequest = {
            if (!update.forceUpdate) {
                onDismiss()
            }
        },
        title = { Text(text = stringResource(R.string.update_available_title)) },
        text = {
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.update_available_version, update.latestVersion),
                    style = MaterialTheme.typography.bodyMedium,
                )

                val details =
                    listOf(update.changelog, update.notes)
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .joinToString(separator = "\n\n")

                if (details.isNotBlank()) {
                    Spacer(modifier = Modifier.height(MaterialTheme.spacings.medium))
                    Text(text = details, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(onClick = onUpdateClick) {
                Text(text = stringResource(R.string.update_available_update_now))
            }
        },
        dismissButton = {
            if (!update.forceUpdate) {
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.update_available_later))
                }
            }
        },
    )
}

@Composable
fun DragonHubNotificationsDialog(
    notifications: List<DragonHubNotificationDto>,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().widthIn(max = 460.dp),
            shape = RoundedCornerShape(28.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
        ) {
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(
                            start = MaterialTheme.spacings.default,
                            top = MaterialTheme.spacings.medium,
                            end = MaterialTheme.spacings.default,
                            bottom = MaterialTheme.spacings.medium,
                        )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.dragon_hub_notifications_title),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text =
                                stringResource(
                                    if (notifications.size == 1) {
                                        R.string.dragon_hub_notifications_count_single
                                    } else {
                                        R.string.dragon_hub_notifications_count_multiple
                                    },
                                    notifications.size,
                                ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    IconButton(
                        modifier = Modifier.size(40.dp),
                        onClick = onDismiss,
                    ) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_x),
                            contentDescription =
                                stringResource(R.string.dragon_hub_notifications_close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(MaterialTheme.spacings.medium))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 2.dp),
                ) {
                    items(notifications, key = { notification -> notification.id }) { notification ->
                        DragonHubNotificationRow(notification = notification)
                    }
                }

                Spacer(modifier = Modifier.height(MaterialTheme.spacings.small))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(text = stringResource(R.string.dragon_hub_notifications_dismiss))
                    }
                }
            }
        }
    }
}

@Composable
private fun DragonHubNotificationRow(notification: DragonHubNotificationDto) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = notification.message,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            formatNotificationTimestamp(notification.createdAt)?.let { timestamp ->
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = timestamp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

private fun formatNotificationTimestamp(createdAt: String): String? {
    if (createdAt.isBlank()) return null

    val formatter = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.getDefault())
    return runCatching { LocalDateTime.parse(createdAt).format(formatter) }.getOrNull()
}
