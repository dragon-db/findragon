package dev.jdtech.jellyfin.setup.data.session

import dev.jdtech.jellyfin.api.JellyfinApi
import dev.jdtech.jellyfin.auth.SecureCredentialsStore
import dev.jdtech.jellyfin.models.User
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.setup.domain.SetupRepository
import dev.jdtech.jellyfin.setup.domain.session.SessionAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DefaultSessionAccess(
    private val jellyfinApi: JellyfinApi,
    private val setupRepository: SetupRepository,
    private val secureCredentialsStore: SecureCredentialsStore,
    private val appPreferences: AppPreferences,
) : SessionAccess {
    override suspend fun isOfflineMode(): Boolean =
        appPreferences.getValue(appPreferences.offlineMode)

    override suspend fun currentUser(): User? =
        withContext(Dispatchers.IO) { setupRepository.getCurrentUser() }

    override suspend fun verifySession() {
        withContext(Dispatchers.IO) { jellyfinApi.userApi.getCurrentUser() }
    }

    override suspend fun storedPassword(user: User): String? =
        withContext(Dispatchers.IO) { secureCredentialsStore.getUserPassword(user.serverId, user.name) }

    override suspend fun clearStoredPassword(user: User) {
        withContext(Dispatchers.IO) { secureCredentialsStore.clearUserPassword(user.serverId, user.name) }
    }

    override suspend fun login(username: String, password: String) {
        setupRepository.login(username, password)
    }
}
