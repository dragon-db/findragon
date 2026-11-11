package dev.jdtech.jellyfin.setup.session

import dev.jdtech.jellyfin.dragonhub.account.AccountBlock
import dev.jdtech.jellyfin.dragonhub.account.AccountStatus
import dev.jdtech.jellyfin.dragonhub.account.AccountStatusResult
import dev.jdtech.jellyfin.dragonhub.account.AccountStatusSource
import dev.jdtech.jellyfin.models.User
import dev.jdtech.jellyfin.setup.domain.session.SessionRecovery
import dev.jdtech.jellyfin.setup.domain.session.SignInReason
import dev.jdtech.jellyfin.setup.presentation.session.SessionClock
import dev.jdtech.jellyfin.setup.presentation.session.SessionGateViewModel
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionGateViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = FakeClock()
    private val accountSource = FakeAccountStatusSource()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `valid login shows the app without sign-in`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeSessionAccess())

        viewModel.start()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.isReady)
        assertNull(state.pendingSignIn)
        assertNull(state.dialog)
    }

    @Test
    fun `valid login is not signed out`() = runTest(dispatcher) {
        val access = FakeSessionAccess()
        val viewModel = viewModel(access)

        viewModel.start()
        advanceUntilIdle()

        assertEquals(0, access.signOutCalls)
    }

    @Test
    fun `dead login is signed out after the hub saw it`() = runTest(dispatcher) {
        val access =
            FakeSessionAccess(
                verifyError = InvalidStatusException(401),
                loginError = InvalidStatusException(403),
            )
        var userAtHubCall: User? = null
        accountSource.onFetch = { userAtHubCall = access.user }
        accountSource.result = known(AccountBlock.DISABLED_BY_ADMIN)
        val viewModel = viewModel(access)

        viewModel.start()
        advanceUntilIdle()

        assertEquals(testUser, userAtHubCall)
        assertEquals(1, access.signOutCalls)
        assertNull(access.user)
        assertEquals("dragon", viewModel.state.value.pendingSignIn)
    }

    @Test
    fun `dead login without password opens sign-in with the username`() = runTest(dispatcher) {
        val viewModel =
            viewModel(FakeSessionAccess(verifyError = InvalidStatusException(401), password = null))

        viewModel.start()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.isReady)
        assertEquals("dragon", state.pendingSignIn)
        assertEquals(SignInReason.SESSION_ENDED, state.dialog)

        viewModel.onSignInOpened()
        viewModel.dismissDialog()
        assertNull(viewModel.state.value.pendingSignIn)
        assertNull(viewModel.state.value.dialog)
    }

    @Test
    fun `slow check shows the app after the timeout`() = runTest(dispatcher) {
        val access = FakeSessionAccess(verifyDelayMs = 60_000L)
        val viewModel = viewModel(access)

        viewModel.start()
        advanceTimeBy(SessionGateViewModel.READY_TIMEOUT_MS - 1)
        assertFalse(viewModel.state.value.isReady)

        advanceTimeBy(2)
        assertTrue(viewModel.state.value.isReady)
    }

    @Test
    fun `login recovered after the app showed reloads home`() = runTest(dispatcher) {
        val access =
            FakeSessionAccess(verifyError = InvalidStatusException(401), verifyDelayMs = 60_000L)
        val viewModel = viewModel(access)

        viewModel.start()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.reloadHome)
        viewModel.onHomeReloaded()
        assertFalse(viewModel.state.value.reloadHome)
    }

    @Test
    fun `login recovered before the app showed needs no reload`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeSessionAccess(verifyError = InvalidStatusException(401)))

        viewModel.start()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isReady)
        assertFalse(viewModel.state.value.reloadHome)
    }

    @Test
    fun `home checks only a user that was not checked yet`() = runTest(dispatcher) {
        val access = FakeSessionAccess()
        val viewModel = viewModel(access)
        viewModel.start()
        advanceUntilIdle()

        viewModel.onHomeShown()
        advanceUntilIdle()
        assertEquals(1, access.verifyCalls)

        access.user = User(id = UUID(0L, 2L), name = "other", serverId = "server-id")
        viewModel.onHomeShown()
        advanceUntilIdle()
        assertEquals(2, access.verifyCalls)
    }

    @Test
    fun `home does not check while the sign-in screen is about to open`() = runTest(dispatcher) {
        val access = FakeSessionAccess(verifyError = InvalidStatusException(401), password = null)
        val viewModel = viewModel(access)
        viewModel.start()
        advanceUntilIdle()

        viewModel.onHomeShown()
        advanceUntilIdle()
        assertEquals(1, access.verifyCalls)

        // Back on home after the redirect (signed in, or picked the saved user again): check.
        // The dead login was signed out; either way the user is the current user again.
        assertEquals(1, access.signOutCalls)
        access.user = testUser
        viewModel.onSignInOpened()
        viewModel.onHomeShown()
        advanceUntilIdle()
        assertEquals(2, access.verifyCalls)
    }

    @Test
    fun `coming back to the app checks again only after the interval`() = runTest(dispatcher) {
        val access = FakeSessionAccess()
        val viewModel = viewModel(access)
        viewModel.start()
        advanceUntilIdle()

        clock.now += SessionGateViewModel.RECHECK_INTERVAL_MS - 1
        viewModel.onAppForegrounded()
        advanceUntilIdle()
        assertEquals(1, access.verifyCalls)

        clock.now += 1
        viewModel.onAppForegrounded()
        advanceUntilIdle()
        assertEquals(2, access.verifyCalls)
    }

    @Test
    fun `start runs once`() = runTest(dispatcher) {
        val access = FakeSessionAccess()
        val viewModel = viewModel(access)

        viewModel.start()
        viewModel.start()
        advanceUntilIdle()

        assertEquals(1, access.verifyCalls)
    }

    @Test
    fun `expired account shows the account notice`() = runTest(dispatcher) {
        accountSource.result = known(AccountBlock.EXPIRED)
        val viewModel = viewModel(FakeSessionAccess())

        viewModel.start()
        advanceUntilIdle()

        assertEquals(AccountBlock.EXPIRED, viewModel.state.value.accountNotice?.block)
        assertNull(viewModel.state.value.pendingSignIn)

        viewModel.dismissAccountNotice()
        assertNull(viewModel.state.value.accountNotice)
    }

    @Test
    fun `active account shows no notice`() = runTest(dispatcher) {
        accountSource.result = known(block = null)
        val viewModel = viewModel(FakeSessionAccess())

        viewModel.start()
        advanceUntilIdle()

        assertNull(viewModel.state.value.accountNotice)
    }

    @Test
    fun `hub outage changes nothing`() = runTest(dispatcher) {
        accountSource.result = AccountStatusResult.Unavailable("offline")
        val viewModel = viewModel(FakeSessionAccess())

        viewModel.start()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isReady)
        assertNull(viewModel.state.value.accountNotice)
    }

    @Test
    fun `renewal clears the notice and reloads home`() = runTest(dispatcher) {
        accountSource.result = known(AccountBlock.EXPIRED)
        val viewModel = viewModel(FakeSessionAccess())
        viewModel.start()
        advanceUntilIdle()
        viewModel.dismissAccountNotice()

        accountSource.result = known(block = null)
        clock.now += SessionGateViewModel.RECHECK_INTERVAL_BLOCKED_MS
        viewModel.onAppForegrounded()
        advanceUntilIdle()

        assertNull(viewModel.state.value.accountNotice)
        assertTrue(viewModel.state.value.reloadHome)
    }

    @Test
    fun `blocked account is checked again sooner when the app comes back`() =
        runTest(dispatcher) {
            accountSource.result = known(AccountBlock.EXPIRED)
            val access = FakeSessionAccess()
            val viewModel = viewModel(access)
            viewModel.start()
            advanceUntilIdle()

            clock.now += SessionGateViewModel.RECHECK_INTERVAL_BLOCKED_MS
            viewModel.onAppForegrounded()
            advanceUntilIdle()

            assertEquals(2, access.verifyCalls)
        }

    @Test
    fun `explore shows the notice again once per visit`() = runTest(dispatcher) {
        accountSource.result = known(AccountBlock.EXPIRED)
        val access = FakeSessionAccess()
        val viewModel = viewModel(access)
        viewModel.start()
        advanceUntilIdle()
        viewModel.dismissAccountNotice()

        viewModel.onExploreShown()
        advanceUntilIdle()

        assertEquals(AccountBlock.EXPIRED, viewModel.state.value.accountNotice?.block)
        assertEquals(1, access.verifyCalls)
    }

    @Test
    fun `explore refreshes a stale blocked status`() = runTest(dispatcher) {
        accountSource.result = known(AccountBlock.EXPIRED)
        val access = FakeSessionAccess()
        val viewModel = viewModel(access)
        viewModel.start()
        advanceUntilIdle()
        viewModel.dismissAccountNotice()

        accountSource.result = known(block = null)
        clock.now += SessionGateViewModel.RECHECK_INTERVAL_BLOCKED_MS
        viewModel.onExploreShown()
        advanceUntilIdle()

        assertEquals(2, access.verifyCalls)
        assertNull(viewModel.state.value.accountNotice)
    }

    @Test
    fun `explore shows nothing for an active account`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeSessionAccess())
        viewModel.start()
        advanceUntilIdle()

        viewModel.onExploreShown()
        advanceUntilIdle()

        assertNull(viewModel.state.value.accountNotice)
    }

    @Test
    fun `admin-disabled account explains itself instead of the sign-in message`() =
        runTest(dispatcher) {
            accountSource.result = known(AccountBlock.DISABLED_BY_ADMIN)
            val viewModel =
                viewModel(
                    FakeSessionAccess(
                        verifyError = InvalidStatusException(401),
                        loginError = InvalidStatusException(403),
                    )
                )

            viewModel.start()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals("dragon", state.pendingSignIn)
            assertNull(state.dialog)
            assertEquals(AccountBlock.DISABLED_BY_ADMIN, state.accountNotice?.block)
        }

    @Test
    fun `blocked sign-in without hub details keeps the generic message`() =
        runTest(dispatcher) {
            accountSource.result = AccountStatusResult.TokenUnknown
            val viewModel =
                viewModel(
                    FakeSessionAccess(
                        verifyError = InvalidStatusException(401),
                        loginError = InvalidStatusException(403),
                    )
                )

            viewModel.start()
            advanceUntilIdle()

            assertEquals(SignInReason.ACCOUNT_BLOCKED, viewModel.state.value.dialog)
            assertNull(viewModel.state.value.accountNotice)
        }

    @Test
    fun `changed password keeps its message and skips the hub`() = runTest(dispatcher) {
        accountSource.result = known(AccountBlock.DISABLED_BY_ADMIN)
        val viewModel =
            viewModel(
                FakeSessionAccess(
                    verifyError = InvalidStatusException(401),
                    loginError = InvalidStatusException(401),
                )
            )

        viewModel.start()
        advanceUntilIdle()

        assertEquals(SignInReason.PASSWORD_CHANGED, viewModel.state.value.dialog)
        assertNull(viewModel.state.value.accountNotice)
        assertEquals(0, accountSource.fetchCalls)
    }

    @Test
    fun `no background re-checks while waiting for sign-in`() = runTest(dispatcher) {
        val access = FakeSessionAccess(verifyError = InvalidStatusException(401), password = null)
        val viewModel = viewModel(access)
        viewModel.start()
        advanceUntilIdle()

        clock.now += SessionGateViewModel.RECHECK_INTERVAL_MS
        viewModel.onAppForegrounded()
        advanceUntilIdle()

        assertEquals(1, access.verifyCalls)
    }

    private fun viewModel(access: FakeSessionAccess) =
        SessionGateViewModel(
            sessionRecovery = SessionRecovery(access),
            accountStatusSource = accountSource,
            clock = clock,
        )

    private fun known(block: AccountBlock?) =
        AccountStatusResult.Known(
            AccountStatus(
                managed = true,
                username = "dragon",
                email = null,
                planName = null,
                subscriptionEndEpochSeconds = null,
                daysRemaining = null,
                expiringSoon = false,
                block = block,
                message = null,
                actionUrl = null,
                actionLabel = null,
            )
        )

    private class FakeClock(var now: Long = 0L) : SessionClock {
        override fun elapsedMillis(): Long = now
    }

    private class FakeAccountStatusSource(
        var result: AccountStatusResult = AccountStatusResult.Unavailable("not set")
    ) : AccountStatusSource {
        var fetchCalls = 0
            private set

        var onFetch: () -> Unit = {}

        override suspend fun fetch(): AccountStatusResult {
            fetchCalls++
            onFetch()
            return result
        }
    }
}
