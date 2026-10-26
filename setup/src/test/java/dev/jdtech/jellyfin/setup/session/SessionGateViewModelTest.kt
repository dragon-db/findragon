package dev.jdtech.jellyfin.setup.session

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

    private fun viewModel(access: FakeSessionAccess) =
        SessionGateViewModel(sessionRecovery = SessionRecovery(access), clock = clock)

    private class FakeClock(var now: Long = 0L) : SessionClock {
        override fun elapsedMillis(): Long = now
    }
}
