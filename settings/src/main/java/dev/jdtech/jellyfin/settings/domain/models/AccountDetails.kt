package dev.jdtech.jellyfin.settings.domain.models

data class AccountDetails(
    val username: String,
    val email: String?,
    val expiryEpochSeconds: Long,
)
