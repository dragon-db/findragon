package dev.jdtech.jellyfin.settings.presentation.settings

import dev.jdtech.jellyfin.settings.domain.models.AccountDetails
import dev.jdtech.jellyfin.settings.presentation.models.PreferenceGroup

data class SettingsState(
    val isLoading: Boolean = false,
    val preferenceGroups: List<PreferenceGroup> = emptyList(),
    val accountDetails: AccountDetails? = null,
    val isAccountDetailsLoading: Boolean = false,
    val accountDetailsError: String? = null,
)
