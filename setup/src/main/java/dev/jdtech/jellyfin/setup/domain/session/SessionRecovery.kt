package dev.jdtech.jellyfin.setup.domain.session

import dev.jdtech.jellyfin.models.User
import java.util.UUID
import kotlinx.coroutines.CancellationException
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import timber.log.Timber

/** What [SessionRecovery] needs from the app. The real implementation is `DefaultSessionAccess`. */
interface SessionAccess {
    suspend fun isOfflineMode(): Boolean

    suspend fun currentUser(): User?

    /** Asks Jellyfin who the saved login belongs to. Throws when the request fails. */
    suspend fun verifySession()

    suspend fun storedPassword(user: User): String?

    suspend fun clearStoredPassword(user: User)

    /** A normal username/password sign-in that saves the new login (upstream `SetupRepository`). */
    suspend fun login(username: String, password: String)

    /**
     * Keeps the user saved but leaves the server without a signed-in user, so the app shows the
     * user list (upstream's setup flow) instead of screens that fail.
     */
    suspend fun signOutCurrentUser()
}

sealed interface SessionCheckResult {
    /** Offline mode, or nobody is signed in: nothing to check. */
    data object Skipped : SessionCheckResult

    data class Valid(val userId: UUID) : SessionCheckResult

    /** The saved login was dead and a silent sign-in with the saved password replaced it. */
    data class Recovered(val userId: UUID) : SessionCheckResult

    /** The server could not be reached. The saved login is kept; it is checked again later. */
    data class Unreachable(val userId: UUID) : SessionCheckResult

    data class SignInRequired(val username: String, val reason: SignInReason) : SessionCheckResult
}

enum class SignInReason {
    /** The login was revoked and no saved password could replace it (e.g. a Quick Connect user). */
    SESSION_ENDED,

    /** Jellyfin rejected the saved password. */
    PASSWORD_CHANGED,

    /** Jellyfin refuses this account (disabled, or otherwise not allowed to sign in). */
    ACCOUNT_BLOCKED,
}

/**
 * Detects a dead Jellyfin login and replaces it with one silent sign-in using the saved password.
 *
 * Jellyfin deletes a user's logins when an admin disables the account or the password changes, and
 * re-enabling the account does not bring them back. Upstream keeps using the dead login forever, so
 * every screen fails with HTTP 401. A password Jellyfin rejects is never retried: it is deleted, so
 * Seerr and the account card stop using it too.
 */
class SessionRecovery(private val access: SessionAccess) {
    suspend fun currentUserId(): UUID? = access.currentUser()?.id

    /** Call after [check] returned [SessionCheckResult.SignInRequired] and its details are read. */
    suspend fun signOut() = access.signOutCurrentUser()

    suspend fun check(): SessionCheckResult {
        if (access.isOfflineMode()) return SessionCheckResult.Skipped
        val user = access.currentUser() ?: return SessionCheckResult.Skipped

        try {
            access.verifySession()
            return SessionCheckResult.Valid(user.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e.httpStatus() !in REJECTED_STATUSES) {
                Timber.w(e, "Could not verify the saved Jellyfin login")
                return SessionCheckResult.Unreachable(user.id)
            }
        }

        val password =
            access.storedPassword(user) ?: return signInRequired(user, SignInReason.SESSION_ENDED)

        return try {
            access.login(user.name, password)
            SessionCheckResult.Recovered(user.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            when (e.httpStatus()) {
                401 -> {
                    access.clearStoredPassword(user)
                    signInRequired(user, SignInReason.PASSWORD_CHANGED)
                }
                403 -> signInRequired(user, SignInReason.ACCOUNT_BLOCKED)
                else -> {
                    Timber.w(e, "Silent Jellyfin sign-in failed")
                    signInRequired(user, SignInReason.SESSION_ENDED)
                }
            }
        }
    }

    private fun signInRequired(user: User, reason: SignInReason) =
        SessionCheckResult.SignInRequired(username = user.name, reason = reason)
}

private val REJECTED_STATUSES = setOf(401, 403)

private fun Throwable.httpStatus(): Int? = (this as? InvalidStatusException)?.status
