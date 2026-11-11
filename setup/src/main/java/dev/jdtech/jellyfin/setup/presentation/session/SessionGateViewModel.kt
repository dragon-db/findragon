package dev.jdtech.jellyfin.setup.presentation.session

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.setup.R
import dev.jdtech.jellyfin.setup.domain.session.SessionCheckResult
import dev.jdtech.jellyfin.setup.domain.session.SessionRecovery
import dev.jdtech.jellyfin.setup.domain.session.SignInReason
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Milliseconds since boot. A fake in tests. */
fun interface SessionClock {
    fun elapsedMillis(): Long
}

data class SessionGateState(
    /** False only during the short check at app start, so a dead login never flashes the home screen. */
    val isReady: Boolean = false,
    /** Username to open the sign-in screen for. Cleared with [SessionGateViewModel.onSignInOpened]. */
    val pendingSignIn: String? = null,
    /** The home screen loaded with a dead login and must load again. */
    val reloadHome: Boolean = false,
    val dialog: SignInReason? = null,
)

/**
 * Runs [SessionRecovery] when the app starts, when another saved user is opened, and when the app
 * comes back to the foreground after a while.
 */
@HiltViewModel
class SessionGateViewModel
@Inject
constructor(private val sessionRecovery: SessionRecovery, private val clock: SessionClock) :
    ViewModel() {
    private val _state = MutableStateFlow(SessionGateState())
    val state = _state.asStateFlow()

    private var started = false
    private var checkJob: Job? = null
    private var checkedUserId: UUID? = null
    private var lastCheckAt: Long? = null

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

    fun onAppForegrounded() {
        if (!started || isChecking()) return
        val lastCheckAt = lastCheckAt ?: return
        if (clock.elapsedMillis() - lastCheckAt < RECHECK_INTERVAL_MS) return

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

    private fun isChecking(): Boolean = checkJob?.isActive == true

    private fun launchCheck(): Job {
        val job =
            viewModelScope.launch {
                val result = sessionRecovery.check()
                lastCheckAt = clock.elapsedMillis()

                when (result) {
                    is SessionCheckResult.Valid -> checkedUserId = result.userId
                    is SessionCheckResult.Unreachable -> checkedUserId = result.userId
                    is SessionCheckResult.Recovered -> {
                        checkedUserId = result.userId
                        if (_state.value.isReady) {
                            _state.update { it.copy(reloadHome = true) }
                        }
                    }
                    is SessionCheckResult.SignInRequired -> {
                        checkedUserId = null
                        _state.update {
                            it.copy(
                                isReady = true,
                                pendingSignIn = result.username,
                                dialog = result.reason,
                            )
                        }
                    }
                    SessionCheckResult.Skipped -> Unit
                }
            }
        checkJob = job
        return job
    }

    companion object {
        const val READY_TIMEOUT_MS = 3_000L
        const val RECHECK_INTERVAL_MS = 10 * 60 * 1_000L
    }
}

@StringRes
fun SignInReason.messageRes(): Int =
    when (this) {
        SignInReason.SESSION_ENDED -> R.string.session_sign_in_session_ended
        SignInReason.PASSWORD_CHANGED -> R.string.session_sign_in_password_changed
        SignInReason.ACCOUNT_BLOCKED -> R.string.session_sign_in_account_blocked
    }
