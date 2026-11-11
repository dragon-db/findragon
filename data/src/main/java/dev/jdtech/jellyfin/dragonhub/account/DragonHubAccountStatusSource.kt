package dev.jdtech.jellyfin.dragonhub.account

import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.dragonhub.api.DragonHubAccountResponse
import dev.jdtech.jellyfin.dragonhub.api.DragonHubApiService
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sends the signed-in user's saved Jellyfin token to DragonHub. No password is involved, so this
 * also works for Quick Connect users and for a login Jellyfin already revoked.
 */
class DragonHubAccountStatusSource(
    private val apiService: DragonHubApiService,
    private val appPreferences: AppPreferences,
    private val database: ServerDatabaseDao,
    private val isEnabled: Boolean,
) : AccountStatusSource {
    override suspend fun fetch(): AccountStatusResult {
        if (!isEnabled) return AccountStatusResult.Unavailable("Dragon Hub is not configured.")

        val token =
            currentAccessToken()
                ?: return AccountStatusResult.Unavailable("No Jellyfin user is signed in.")

        return when (val response = apiService.getAccount(token)) {
            is DragonHubAccountResponse.Success ->
                AccountStatusResult.Known(response.account.toAccountStatus())
            is DragonHubAccountResponse.Error ->
                if (response.code == TOKEN_UNKNOWN || response.code == TOKEN_MISSING) {
                    AccountStatusResult.TokenUnknown
                } else {
                    AccountStatusResult.Unavailable(
                        "Dragon Hub answered HTTP ${response.httpStatus}" +
                            (response.code?.let { " ($it)" } ?: "") +
                            "."
                    )
                }
            DragonHubAccountResponse.Unreachable ->
                AccountStatusResult.Unavailable("Dragon Hub is unreachable.")
        }
    }

    private suspend fun currentAccessToken(): String? =
        withContext(Dispatchers.IO) {
            val serverId =
                appPreferences.getValue(appPreferences.currentServer) ?: return@withContext null
            database.getServerWithAddressAndUser(serverId)?.user?.accessToken?.takeIf {
                it.isNotBlank()
            }
        }

    private companion object {
        const val TOKEN_UNKNOWN = "token_unknown"
        const val TOKEN_MISSING = "token_missing"
    }
}
