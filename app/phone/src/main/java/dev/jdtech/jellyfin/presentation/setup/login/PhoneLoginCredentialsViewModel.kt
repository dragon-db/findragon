package dev.jdtech.jellyfin.presentation.setup.login

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.auth.SecureCredentialsStore
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltViewModel
class PhoneLoginCredentialsViewModel
@Inject
constructor(
    private val appPreferences: AppPreferences,
    private val database: ServerDatabaseDao,
    private val secureCredentialsStore: SecureCredentialsStore,
) : ViewModel() {
    suspend fun savePasswordForCurrentUser(password: String) {
        withContext(Dispatchers.IO) {
            val serverId = appPreferences.getValue(appPreferences.currentServer) ?: return@withContext
            val currentUser = database.getServerWithAddressAndUser(serverId)?.user ?: return@withContext

            secureCredentialsStore.saveUserPassword(
                serverId = serverId,
                username = currentUser.name,
                password = password,
            )
            secureCredentialsStore.clearJellyseerrSession(serverId, currentUser.name)
        }
    }

    suspend fun clearPasswordForCurrentUser() {
        withContext(Dispatchers.IO) {
            val serverId = appPreferences.getValue(appPreferences.currentServer) ?: return@withContext
            val currentUser = database.getServerWithAddressAndUser(serverId)?.user ?: return@withContext

            secureCredentialsStore.clearUserPassword(serverId, currentUser.name)
            secureCredentialsStore.clearJellyseerrSession(serverId, currentUser.name)
        }
    }
}
