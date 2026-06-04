package dev.jdtech.jellyfin.presentation.settings

import android.app.Activity
import android.app.UiModeManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.recalculateWindowInsets
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.data.BuildConfig as DataBuildConfig
import dev.jdtech.jellyfin.presentation.settings.components.SettingsGroupCard
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.presentation.utils.plus
import dev.jdtech.jellyfin.settings.domain.models.AccountDetails
import dev.jdtech.jellyfin.settings.R as SettingsR
import dev.jdtech.jellyfin.settings.presentation.enums.DeviceType
import dev.jdtech.jellyfin.settings.presentation.models.PreferenceCategory
import dev.jdtech.jellyfin.settings.presentation.models.PreferenceGroup
import dev.jdtech.jellyfin.settings.presentation.settings.SettingsAction
import dev.jdtech.jellyfin.settings.presentation.settings.SettingsEvent
import dev.jdtech.jellyfin.settings.presentation.settings.SettingsState
import dev.jdtech.jellyfin.settings.presentation.settings.SettingsViewModel
import dev.jdtech.jellyfin.utils.ObserveAsEvents
import dev.jdtech.jellyfin.utils.restart
import java.text.DateFormat
import java.util.Date
import timber.log.Timber

@Composable
fun SettingsScreen(
    indexes: IntArray = intArrayOf(),
    navigateToSettings: (indexes: IntArray) -> Unit,
    navigateToServers: () -> Unit,
    navigateToUsers: () -> Unit,
    navigateToAbout: () -> Unit,
    navigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current

    val state by viewModel.state.collectAsStateWithLifecycle()
    val showAccountDetails =
        shouldShowAccountDetails(
            isConfigured = DataBuildConfig.JFA_GO_BASE_URL.isNotBlank(),
            indexes = indexes,
        )

    LaunchedEffect(true) {
        viewModel.loadPreferences(
            indexes = indexes,
            deviceType = DeviceType.PHONE,
            loadAccountDetails = showAccountDetails,
        )
    }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is SettingsEvent.NavigateToSettings -> navigateToSettings(event.indexes)
            is SettingsEvent.NavigateToUsers -> navigateToUsers()
            is SettingsEvent.NavigateToServers -> navigateToServers()
            is SettingsEvent.NavigateToAbout -> navigateToAbout()
            is SettingsEvent.UpdateTheme -> {
                val uiModeManager = context.getSystemService(UiModeManager::class.java)
                val nightMode =
                    when (event.theme) {
                        "system" ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                                UiModeManager.MODE_NIGHT_AUTO
                            else AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                        "light" ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                                UiModeManager.MODE_NIGHT_NO
                            else AppCompatDelegate.MODE_NIGHT_NO
                        "dark" ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                                UiModeManager.MODE_NIGHT_YES
                            else AppCompatDelegate.MODE_NIGHT_YES
                        else ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                                UiModeManager.MODE_NIGHT_AUTO
                            else AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    uiModeManager.setApplicationNightMode(nightMode)
                } else {
                    AppCompatDelegate.setDefaultNightMode(nightMode)
                }
            }
            is SettingsEvent.LaunchIntent -> {
                try {
                    context.startActivity(event.intent)
                } catch (e: Exception) {
                    Timber.e(e)
                }
            }
            is SettingsEvent.RestartActivity -> {
                try {
                    (context as Activity).restart()
                } catch (_: Exception) {}
            }
        }
    }

    SettingsScreenLayout(
        title = indexes.last(),
        state = state,
        onAction = { action ->
            when (action) {
                is SettingsAction.OnBackClick -> navigateBack()
                is SettingsAction.OnUpdate -> {
                    viewModel.onAction(action)
                    viewModel.loadPreferences(
                        indexes = indexes,
                        deviceType = DeviceType.PHONE,
                        loadAccountDetails = showAccountDetails,
                    )
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreenLayout(
    @StringRes title: Int,
    state: SettingsState,
    onAction: (SettingsAction) -> Unit,
) {
    val contentPadding = PaddingValues(all = MaterialTheme.spacings.default)

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier =
            Modifier.fillMaxSize()
                .recalculateWindowInsets()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(title)) },
                navigationIcon = {
                    IconButton(onClick = { onAction(SettingsAction.OnBackClick) }) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_arrow_left),
                            contentDescription = null,
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = contentPadding + innerPadding,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            itemsIndexed(state.preferenceGroups) { index, group ->
                SettingsGroupCard(
                    group = group,
                    onAction = onAction,
                    modifier = Modifier.widthIn(max = 640.dp),
                )
                if (
                    index == 0 &&
                        (state.accountDetails != null ||
                            state.isAccountDetailsLoading ||
                            state.accountDetailsError != null)
                ) {
                    Spacer(modifier = Modifier.height(MaterialTheme.spacings.medium))
                    AccountDetailsCard(
                        accountDetails = state.accountDetails,
                        isLoading = state.isAccountDetailsLoading,
                        error = state.accountDetailsError,
                        modifier = Modifier.widthIn(max = 640.dp),
                    )
                }
            }
        }
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
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
                    accountSummaryRows(
                            accountDetails = accountDetails,
                            notSet = stringResource(SettingsR.string.account_not_set),
                            noExpiry = stringResource(SettingsR.string.account_no_expiry),
                        )
                        .forEach { row ->
                            AccountDetailsRow(
                                label = stringResource(row.labelRes),
                                value = row.value,
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
private fun AccountDetailsRow(label: String, value: String) {
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
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

internal data class AccountSummaryRow(val labelRes: Int, val value: String)

internal fun shouldShowAccountDetails(isConfigured: Boolean, indexes: IntArray): Boolean {
    return isConfigured &&
        (indexes.isEmpty() ||
            (indexes.size == 1 && indexes.first() == CoreR.string.title_settings))
}

internal fun accountSummaryRows(
    accountDetails: AccountDetails,
    notSet: String,
    noExpiry: String,
    dateFormat: DateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM),
): List<AccountSummaryRow> {
    return listOf(
        AccountSummaryRow(SettingsR.string.account_username, accountDetails.username),
        AccountSummaryRow(SettingsR.string.account_email, accountDetails.email ?: notSet),
        AccountSummaryRow(
            SettingsR.string.account_expiry,
            formatAccountExpiry(accountDetails.expiryEpochSeconds, noExpiry, dateFormat),
        ),
    )
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

@PreviewScreenSizes
@Composable
private fun SettingsScreenLayoutPreview() {
    FindroidTheme {
        SettingsScreenLayout(
            title = CoreR.string.title_settings,
            state =
                SettingsState(
                    accountDetails =
                        AccountDetails(
                            username = "dragon",
                            email = "dragon@example.com",
                            expiryEpochSeconds = 1_748_908_800,
                        ),
                    isAccountDetailsLoading = false,
                    preferenceGroups =
                        listOf(
                            PreferenceGroup(
                                nameStringResource = null,
                                preferences =
                                    listOf(
                                        PreferenceCategory(
                                            nameStringResource =
                                                SettingsR.string.settings_category_language,
                                            iconDrawableId = SettingsR.drawable.ic_languages,
                                        )
                                    ),
                            ),
                            PreferenceGroup(
                                nameStringResource = null,
                                preferences =
                                    listOf(
                                        PreferenceCategory(
                                            nameStringResource =
                                                SettingsR.string.settings_category_interface,
                                            iconDrawableId = SettingsR.drawable.ic_palette,
                                        )
                                    ),
                            ),
                        )
                ),
            onAction = {},
        )
    }
}
