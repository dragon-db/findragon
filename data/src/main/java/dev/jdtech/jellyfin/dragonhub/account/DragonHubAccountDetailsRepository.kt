package dev.jdtech.jellyfin.dragonhub.account

import dev.jdtech.jellyfin.settings.domain.AccountDetailsRepository
import dev.jdtech.jellyfin.settings.domain.AccountDetailsResult
import dev.jdtech.jellyfin.settings.domain.models.AccountDetails
import dev.jdtech.jellyfin.settings.domain.models.AccountState

/** Settings account card from DragonHub (replaces JFA-GO). */
class DragonHubAccountDetailsRepository(private val accountStatusSource: AccountStatusSource) :
    AccountDetailsRepository {
    override suspend fun getAccountDetails(): AccountDetailsResult {
        return when (val result = accountStatusSource.fetch()) {
            is AccountStatusResult.Known -> {
                val status = result.status
                val username =
                    status.username
                        ?: return AccountDetailsResult.Failure("Dragon Hub did not return a username.")
                AccountDetailsResult.Success(
                    AccountDetails(
                        username = username,
                        email = status.email,
                        expiryEpochSeconds = status.subscriptionEndEpochSeconds ?: 0L,
                        planName = status.planName,
                        state = status.toAccountState(),
                        daysRemaining = status.daysRemaining,
                        expiringSoon = status.expiringSoon,
                    )
                )
            }
            AccountStatusResult.TokenUnknown ->
                AccountDetailsResult.Failure("Your session ended. Please sign in again.")
            is AccountStatusResult.Unavailable -> AccountDetailsResult.Failure(result.reason)
        }
    }
}

private fun AccountStatus.toAccountState(): AccountState? =
    when {
        !managed -> null
        block == AccountBlock.EXPIRED -> AccountState.EXPIRED
        block != null -> AccountState.DISABLED
        else -> AccountState.ACTIVE
    }
