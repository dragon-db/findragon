package dev.jdtech.jellyfin.presentation.setup.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.auth.SecureCredentialsStore
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@HiltViewModel
class PhoneLoginCredentialsViewModel
@Inject
constructor(
    private val appPreferences: AppPreferences,
    private val database: ServerDatabaseDao,
    private val secureCredentialsStore: SecureCredentialsStore,
) : ViewModel() {
    fun savePasswordForCurrentUser(password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val serverId = appPreferences.getValue(appPreferences.currentServer) ?: return@launch
            val currentUser = database.getServerWithAddressAndUser(serverId)?.user ?: return@launch

            secureCredentialsStore.saveUserPassword(
                serverId = serverId,
                username = currentUser.name,
                password = password,
            )
            secureCredentialsStore.clearJellyseerrSession(serverId, currentUser.name)
        }
    }

    fun clearPasswordForCurrentUser() {
        viewModelScope.launch(Dispatchers.IO) {
            val serverId = appPreferences.getValue(appPreferences.currentServer) ?: return@launch
            val currentUser = database.getServerWithAddressAndUser(serverId)?.user ?: return@launch

            secureCredentialsStore.clearUserPassword(serverId, currentUser.name)
            secureCredentialsStore.clearJellyseerrSession(serverId, currentUser.name)
        }
    }
}
