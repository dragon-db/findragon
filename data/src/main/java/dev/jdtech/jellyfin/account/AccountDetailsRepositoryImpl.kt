package dev.jdtech.jellyfin.account

import dev.jdtech.jellyfin.account.api.JfaGoApiService
import dev.jdtech.jellyfin.auth.SecureCredentialsStore
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.settings.domain.AccountDetailsRepository
import dev.jdtech.jellyfin.settings.domain.AccountDetailsResult
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.settings.domain.models.AccountDetails
import kotlinx.coroutines.CancellationException
import timber.log.Timber

class AccountDetailsRepositoryImpl(
    private val apiService: JfaGoApiService,
    private val serverDatabase: ServerDatabaseDao,
    private val appPreferences: AppPreferences,
    private val secureCredentialsStore: SecureCredentialsStore,
) : AccountDetailsRepository {
    override suspend fun getAccountDetails(): AccountDetailsResult {
        return try {
            val context = currentContext()
            val token =
                try {
                    apiService.login(context.username, context.password).token
                } catch (e: dev.jdtech.jellyfin.account.api.JfaGoApiException) {
                    return failure("JFA-GO login", e)
                }
            val details =
                try {
                    apiService.getMyDetails(token)
                } catch (e: dev.jdtech.jellyfin.account.api.JfaGoApiException) {
                    return failure("JFA-GO account details", e)
                }

            AccountDetailsResult.Success(
                AccountDetails(
                    username = details.username.takeIf { it.isNotBlank() } ?: context.username,
                    email = details.email?.value?.takeIf { it.isNotBlank() },
                    expiryEpochSeconds = details.expiry,
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: AccountDetailsContextException) {
            Timber.w(e.message.orEmpty())
            AccountDetailsResult.Failure(e.message.orEmpty())
        } catch (e: Exception) {
            Timber.w(e, "Failed to load JFA-GO account details")
            AccountDetailsResult.Failure("Failed to load JFA-GO account details.")
        }
    }

    private fun currentContext(): AccountDetailsContext {
        val serverId =
            appPreferences.getValue(appPreferences.currentServer)
                ?: throw AccountDetailsContextException("No current Jellyfin server is selected.")
        val serverData =
            serverDatabase.getServerWithAddressAndUser(serverId)
                ?: throw AccountDetailsContextException("Current Jellyfin server data was not found.")
        val username =
            serverData.user?.name
                ?: throw AccountDetailsContextException("No current Jellyfin user is selected.")
        val password =
            secureCredentialsStore.getUserPassword(serverId, username)
                ?: throw AccountDetailsContextException(
                    "No stored Jellyfin password is available for this user. Sign in with username and password first."
                )

        return AccountDetailsContext(username = username, password = password)
    }

    private fun failure(
        operation: String,
        error: dev.jdtech.jellyfin.account.api.JfaGoApiException,
    ): AccountDetailsResult.Failure {
        Timber.w("%s failed with HTTP %s", operation, error.code)
        return AccountDetailsResult.Failure("$operation failed (HTTP ${error.code}).")
    }

    private data class AccountDetailsContext(
        val username: String,
        val password: String,
    )

    private class AccountDetailsContextException(message: String) : IllegalStateException(message)
}
