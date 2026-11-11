package dev.jdtech.jellyfin.dragonhub.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `GET /p/accountdb/app/account`. Only the fields the app uses; the full contract is in the local
 * doc `docs/features/dragon-hub.md`.
 */
@Serializable
data class DragonHubAccountDto(
    /** valid | revoked | unverified */
    val session: String? = null,
    /** Only when revoked: account_disabled | password_changed | unknown */
    @SerialName("session_reason") val sessionReason: String? = null,
    val managed: Boolean = false,
    val username: String? = null,
    val email: String? = null,
    /** active | expired | disabled (null if not managed) */
    val status: String? = null,
    val plan: DragonHubPlanDto? = null,
    @SerialName("subscription_end") val subscriptionEnd: String? = null,
    @SerialName("days_remaining") val daysRemaining: Int? = null,
    val expired: Boolean = false,
    @SerialName("expiring_soon") val expiringSoon: Boolean = false,
    /** null (can watch) | expired | admin | external */
    @SerialName("disabled_reason") val disabledReason: String? = null,
    val message: String? = null,
    @SerialName("action_url") val actionUrl: String? = null,
    @SerialName("action_label") val actionLabel: String? = null,
)

@Serializable data class DragonHubPlanDto(val name: String? = null)

/** Every hub error body: branch on [code], never on [detail]. */
@Serializable
data class DragonHubErrorDto(val detail: String? = null, val code: String? = null)
