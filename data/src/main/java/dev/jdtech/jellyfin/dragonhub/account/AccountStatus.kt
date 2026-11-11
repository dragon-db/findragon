package dev.jdtech.jellyfin.dragonhub.account

import dev.jdtech.jellyfin.dragonhub.api.dto.DragonHubAccountDto
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** Why the account cannot watch right now. */
enum class AccountBlock {
    /** Subscription lapsed. Jellyfin login still works, the library is just empty. */
    EXPIRED,

    /** Disabled by the admin (or the admin removed their library access). */
    DISABLED_BY_ADMIN,

    /** Disabled directly in the Jellyfin dashboard. */
    DISABLED_ON_SERVER,
}

/** The signed-in user's account as DragonHub sees it. */
data class AccountStatus(
    /** False: a Jellyfin user DragonHub does not manage (no subscription data). */
    val managed: Boolean,
    val username: String?,
    val email: String?,
    val planName: String?,
    /** Null = never expires. */
    val subscriptionEndEpochSeconds: Long?,
    val daysRemaining: Int?,
    val expiringSoon: Boolean,
    /** Null = can watch. */
    val block: AccountBlock?,
    /** Admin-editable text to show the user. */
    val message: String?,
    val actionUrl: String?,
    val actionLabel: String?,
)

sealed interface AccountStatusResult {
    data class Known(val status: AccountStatus) : AccountStatusResult

    /** The hub never saw this login (or forgot it): it cannot say whose it is. */
    data object TokenUnknown : AccountStatusResult

    /** Hub not configured, unreachable, or answered with an error. */
    data class Unavailable(val reason: String) : AccountStatusResult
}

/** Asks DragonHub about the signed-in user. Works with a dead Jellyfin login too. */
interface AccountStatusSource {
    suspend fun fetch(): AccountStatusResult
}

internal fun DragonHubAccountDto.toAccountStatus(): AccountStatus =
    AccountStatus(
        managed = managed,
        username = username?.takeIf { it.isNotBlank() },
        email = email?.takeIf { it.isNotBlank() },
        planName = plan?.name?.takeIf { it.isNotBlank() },
        subscriptionEndEpochSeconds = subscriptionEnd?.let(::parseHubDateTime),
        daysRemaining = daysRemaining,
        expiringSoon = expiringSoon,
        block =
            when (disabledReason) {
                null -> null
                "expired" -> AccountBlock.EXPIRED
                "admin" -> AccountBlock.DISABLED_BY_ADMIN
                // "external", and any reason a newer hub may add.
                else -> AccountBlock.DISABLED_ON_SERVER
            },
        message = message?.takeIf { it.isNotBlank() },
        actionUrl = actionUrl?.takeIf { it.isNotBlank() },
        actionLabel = actionLabel?.takeIf { it.isNotBlank() },
    )

/**
 * Epoch seconds of a DragonHub date. The hub sends ISO dates with or without an offset (e.g.
 * `2026-11-02T00:00:00+00:00`, `2026-11-02T00:00:00.123456`); one without an offset is UTC.
 */
internal fun parseHubDateTime(value: String): Long? {
    val text = value.trim().replaceFirst(' ', 'T')
    return runCatching { OffsetDateTime.parse(text).toEpochSecond() }
        .recoverCatching { LocalDateTime.parse(text).toEpochSecond(ZoneOffset.UTC) }
        .recoverCatching { LocalDate.parse(text).atStartOfDay().toEpochSecond(ZoneOffset.UTC) }
        .getOrNull()
}
