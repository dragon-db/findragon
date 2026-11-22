package dev.jdtech.jellyfin.setup.presentation.session

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.dragonhub.account.AccountBlock
import dev.jdtech.jellyfin.dragonhub.account.AccountStatus
import dev.jdtech.jellyfin.dragonhub.account.AccountStatusResult
import dev.jdtech.jellyfin.dragonhub.account.AccountStatusSource
import dev.jdtech.jellyfin.setup.R
import dev.jdtech.jellyfin.setup.domain.session.SessionCheckResult
import dev.jdtech.jellyfin.setup.domain.session.SessionRecovery
import dev.jdtech.jellyfin.setup.domain.session.SignInReason
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/** Milliseconds since boot. A fake in tests. */
fun interface SessionClock {
    fun elapsedMillis(): Long
}

data class SessionGateState(
    /** False only during the short check at app start, so a dead login never flashes the home screen. */
    val isReady: Boolean = false,
    /** Username to open the sign-in screen for. Cleared with [SessionGateViewModel.onSignInOpened]. */
    val pendingSignIn: String? = null,
    /** The home screen loaded with a dead login (or an expired account) and must load again. */
    val reloadHome: Boolean = false,
    val dialog: SignInReason? = null,
    /** Expired or disabled account to explain to the user (from DragonHub). */
    val accountNotice: AccountStatus? = null,
)

/**
 * Runs [SessionRecovery] when the app starts, when another saved user is opened, and when the app
 * comes back to the foreground after a while. Then asks DragonHub whether the account is expired or
 * disabled, because Jellyfin itself never says: an expired user just sees an empty library.
 */
@HiltViewModel
class SessionGateViewModel
@Inject
constructor(
    private val sessionRecovery: SessionRecovery,
    private val accountStatusSource: AccountStatusSource,
    private val clock: SessionClock,
) : ViewModel() {
    private val _state = MutableStateFlow(SessionGateState())
    val state = _state.asStateFlow()

    private var started = false
    private var checkJob: Job? = null
    private var checkedUserId: UUID? = null
    private var lastCheckAt: Long? = null

    /** The app is waiting for the user to sign in; no background re-checks until they do. */
    private var awaitingSignIn = false

    /** Last known blocked status of a working login (an expired subscription, in practice). */
    private var blockedStatus: AccountStatus? = null

    fun start() {
        if (started) return
        started = true

        val check = launchCheck()
        viewModelScope.launch {
            withTimeoutOrNull(READY_TIMEOUT_MS) { check.join() }
            _state.update { it.copy(isReady = true) }
        }
    }

    /**
     * The home screen is showing. Checks the current user unless that user was checked already, or
     * the app is about to open the sign-in screen anyway.
     */
    fun onHomeShown() {
        if (!started || isChecking() || _state.value.pendingSignIn != null) return

        viewModelScope.launch {
            val userId = sessionRecovery.currentUserId()
            if (userId != null && userId != checkedUserId && !isChecking()) {
                launchCheck()
            }
        }
    }

    /** Explore is showing. Explains again why requests are refused while the account is blocked. */
    fun onExploreShown() {
        val blocked = blockedStatus ?: return
        if (!started || isChecking()) return

        if (isStale(RECHECK_INTERVAL_BLOCKED_MS)) {
            launchCheck()
        } else {
            _state.update { it.copy(accountNotice = blocked) }
        }
    }

    fun onAppForegrounded() {
        if (!started || isChecking() || awaitingSignIn) return
        val interval = if (blockedStatus != null) RECHECK_INTERVAL_BLOCKED_MS else RECHECK_INTERVAL_MS
        if (!isStale(interval)) return

        launchCheck()
    }

    fun onSignInOpened() {
        _state.update { it.copy(pendingSignIn = null) }
    }

    fun onHomeReloaded() {
        _state.update { it.copy(reloadHome = false) }
    }

    fun dismissDialog() {
        _state.update { it.copy(dialog = null) }
    }

    fun dismissAccountNotice() {
        _state.update { it.copy(accountNotice = null) }
    }

    private fun isChecking(): Boolean = checkJob?.isActive == true

    private fun isStale(interval: Long): Boolean {
        val lastCheckAt = lastCheckAt ?: return false
        return clock.elapsedMillis() - lastCheckAt >= interval
    }

    private fun launchCheck(): Job {
        val job =
            viewModelScope.launch {
                val result = sessionRecovery.check()

                when (result) {
                    is SessionCheckResult.Valid -> {
                        onWorkingLogin(result.userId)
                    }
                    is SessionCheckResult.Recovered -> {
                        if (_state.value.isReady) {
                            _state.update { it.copy(reloadHome = true) }
                        }
                        onWorkingLogin(result.userId)
                    }
                    is SessionCheckResult.Unreachable -> checkedUserId = result.userId
                    is SessionCheckResult.SignInRequired -> onSignInRequired(result)
                    SessionCheckResult.Skipped -> Unit
                }
                lastCheckAt = clock.elapsedMillis()
            }
        checkJob = job
        return job
    }

    private suspend fun onWorkingLogin(userId: UUID) {
        checkedUserId = userId
        awaitingSignIn = false

        val status = fetchAccountStatus() ?: return
        val wasBlocked = blockedStatus != null
        blockedStatus = status.takeIf { it.block != null }

        when {
            blockedStatus != null -> _state.update { it.copy(accountNotice = blockedStatus) }
            // Renewed: the same login sees the library again.
            wasBlocked ->
                _state.update {
                    it.copy(accountNotice = null, reloadHome = it.reloadHome || it.isReady)
                }
        }
    }

    private suspend fun onSignInRequired(result: SessionCheckResult.SignInRequired) {
        checkedUserId = null
        awaitingSignIn = true
        blockedStatus = null

        // The hub still knows whose dead login this is. A disabled account explains itself better
        // than the generic sign-in message.
        val disabled =
            if (result.reason == SignInReason.PASSWORD_CHANGED) {
                null
            } else {
                fetchAccountStatus()?.takeIf { it.block != null && it.block != AccountBlock.EXPIRED }
            }

        // After the hub call, which identifies the user by the dead login. Signing out makes the app
        // show the user list, where the sign-in screen opens on top.
        try {
            sessionRecovery.signOut()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Could not sign out the dead Jellyfin login")
        }

        _state.update {
            it.copy(
                isReady = true,
                pendingSignIn = result.username,
                dialog = if (disabled == null) result.reason else null,
                accountNotice = disabled,
            )
        }
    }

    private suspend fun fetchAccountStatus(): AccountStatus? =
        (accountStatusSource.fetch() as? AccountStatusResult.Known)?.status

    companion object {
        const val READY_TIMEOUT_MS = 3_000L
        const val RECHECK_INTERVAL_MS = 10 * 60 * 1_000L
        const val RECHECK_INTERVAL_BLOCKED_MS = 60 * 1_000L
    }
}

@StringRes
fun SignInReason.messageRes(): Int =
    when (this) {
        SignInReason.SESSION_ENDED -> R.string.session_sign_in_session_ended
        SignInReason.PASSWORD_CHANGED -> R.string.session_sign_in_password_changed
        SignInReason.ACCOUNT_BLOCKED -> R.string.session_sign_in_account_blocked
    }
