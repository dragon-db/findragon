package dev.jdtech.jellyfin.settings.domain.models

data class AccountDetails(
    val username: String,
    val email: String?,
    /** 0 = no expiry. */
    val expiryEpochSeconds: Long,
    val planName: String? = null,
    /** Null when the account is not managed by DragonHub. */
    val state: AccountState? = null,
    val daysRemaining: Int? = null,
    val expiringSoon: Boolean = false,
)

enum class AccountState {
    ACTIVE,
    EXPIRED,
    DISABLED,
}
