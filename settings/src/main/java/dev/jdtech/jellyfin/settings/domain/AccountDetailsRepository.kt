package dev.jdtech.jellyfin.settings.domain

import dev.jdtech.jellyfin.settings.domain.models.AccountDetails

interface AccountDetailsRepository {
    suspend fun getAccountDetails(): AccountDetailsResult
}

sealed interface AccountDetailsResult {
    data class Success(val details: AccountDetails) : AccountDetailsResult

    data class Failure(val message: String) : AccountDetailsResult
}
