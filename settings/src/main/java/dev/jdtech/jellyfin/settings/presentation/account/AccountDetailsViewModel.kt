package dev.jdtech.jellyfin.settings.presentation.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.settings.domain.AccountDetailsRepository
import dev.jdtech.jellyfin.settings.domain.AccountDetailsResult
import dev.jdtech.jellyfin.settings.domain.models.AccountDetails
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AccountDetailsState(
    val accountDetails: AccountDetails? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

/**
 * Findragon account card (JFA-GO) on the phone Settings root page. Kept apart from
 * SettingsViewModel.
 */
@HiltViewModel
class AccountDetailsViewModel
@Inject
constructor(private val accountDetailsRepository: AccountDetailsRepository) : ViewModel() {
    private val _state = MutableStateFlow(AccountDetailsState())
    val state = _state.asStateFlow()

    private var hasLoaded = false

    /**
     * Fetches once per Settings visit; returning from a sub-page or toggling a setting does not
     * refetch.
     */
    fun loadOnce() {
        if (hasLoaded) return
        hasLoaded = true

        viewModelScope.launch {
            _state.value =
                when (val result = accountDetailsRepository.getAccountDetails()) {
                    is AccountDetailsResult.Success ->
                        AccountDetailsState(accountDetails = result.details, isLoading = false)
                    is AccountDetailsResult.Failure ->
                        AccountDetailsState(isLoading = false, error = result.message)
                }
        }
    }
}
