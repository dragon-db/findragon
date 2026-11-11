package dev.jdtech.jellyfin.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.settings.R as SettingsR
import dev.jdtech.jellyfin.settings.domain.models.AccountDetails
import dev.jdtech.jellyfin.settings.domain.models.AccountState
import dev.jdtech.jellyfin.settings.presentation.account.AccountDetailsViewModel
import java.text.DateFormat
import java.util.Date

/** Findragon account card (DragonHub). SettingsScreen only calls this below its first group. */
@Composable
fun AccountDetailsSection(
    modifier: Modifier = Modifier,
    viewModel: AccountDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.loadOnce() }

    if (state.accountDetails != null || state.isLoading || state.error != null) {
        Spacer(modifier = Modifier.height(MaterialTheme.spacings.medium))
        AccountDetailsCard(
            accountDetails = state.accountDetails,
            isLoading = state.isLoading,
            error = state.error,
            modifier = modifier,
        )
    }
}

@Composable
private fun AccountDetailsCard(
    accountDetails: AccountDetails?,
    isLoading: Boolean,
    error: String?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacings.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
        ) {
            Text(
                text = stringResource(SettingsR.string.account),
                style = MaterialTheme.typography.titleMedium,
            )
            when {
                accountDetails != null -> {
                    val resources = LocalContext.current.resources
                    val active = stringResource(SettingsR.string.account_status_active)
                    val expired = stringResource(SettingsR.string.account_status_expired)
                    val disabled = stringResource(SettingsR.string.account_status_disabled)
                    accountSummaryRows(
                            accountDetails = accountDetails,
                            notSet = stringResource(SettingsR.string.account_not_set),
                            noExpiry = stringResource(SettingsR.string.account_no_expiry),
                            stateLabel = { state ->
                                when (state) {
                                    AccountState.ACTIVE -> active
                                    AccountState.EXPIRED -> expired
                                    AccountState.DISABLED -> disabled
                                }
                            },
                            expiryWithDaysLeft = { date, days ->
                                resources.getQuantityString(
                                    SettingsR.plurals.account_expiry_days_left,
                                    days,
                                    date,
                                    days,
                                )
                            },
                        )
                        .forEach { row ->
                            AccountDetailsRow(
                                label = stringResource(row.labelRes),
                                value = row.value,
                                isWarning = row.isWarning,
                            )
                        }
                }
                isLoading -> {
                    Text(
                        text = stringResource(SettingsR.string.account_loading),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                error != null -> {
                    Text(
                        text = stringResource(SettingsR.string.account_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountDetailsRow(label: String, value: String, isWarning: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            modifier = Modifier.padding(start = MaterialTheme.spacings.medium).weight(2f),
            color = if (isWarning) MaterialTheme.colorScheme.error else Color.Unspecified,
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

internal data class AccountSummaryRow(
    val labelRes: Int,
    val value: String,
    val isWarning: Boolean = false,
)

internal fun shouldShowAccountDetails(isConfigured: Boolean, indexes: List<Int>): Boolean {
    return isConfigured &&
        (indexes.isEmpty() || (indexes.size == 1 && indexes.first() == CoreR.string.title_settings))
}

internal fun accountSummaryRows(
    accountDetails: AccountDetails,
    notSet: String,
    noExpiry: String,
    stateLabel: (AccountState) -> String = { it.name.lowercase().replaceFirstChar(Char::titlecase) },
    expiryWithDaysLeft: (date: String, days: Int) -> String = { date, days -> "$date ($days days left)" },
    dateFormat: DateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM),
): List<AccountSummaryRow> {
    val state = accountDetails.state
    val expiry = formatAccountExpiry(accountDetails.expiryEpochSeconds, noExpiry, dateFormat)
    val daysRemaining = accountDetails.daysRemaining

    return buildList {
        add(AccountSummaryRow(SettingsR.string.account_username, accountDetails.username))
        add(AccountSummaryRow(SettingsR.string.account_email, accountDetails.email ?: notSet))
        accountDetails.planName?.let { add(AccountSummaryRow(SettingsR.string.account_plan, it)) }
        state?.let {
            add(
                AccountSummaryRow(
                    SettingsR.string.account_status,
                    stateLabel(it),
                    isWarning = it != AccountState.ACTIVE,
                )
            )
        }
        add(
            AccountSummaryRow(
                SettingsR.string.account_expiry,
                if (state == AccountState.ACTIVE &&
                    daysRemaining != null &&
                    accountDetails.expiryEpochSeconds != 0L
                ) {
                    expiryWithDaysLeft(expiry, daysRemaining)
                } else {
                    expiry
                },
                isWarning = accountDetails.expiringSoon && state == AccountState.ACTIVE,
            )
        )
    }
}

private fun formatAccountExpiry(
    expiryEpochSeconds: Long,
    noExpiry: String,
    dateFormat: DateFormat,
): String {
    if (expiryEpochSeconds == 0L) {
        return noExpiry
    }

    return dateFormat.format(Date(expiryEpochSeconds * 1000L))
}
