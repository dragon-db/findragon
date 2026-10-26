package dev.jdtech.jellyfin.setup.session

import dev.jdtech.jellyfin.setup.domain.session.SessionCheckResult
import dev.jdtech.jellyfin.setup.domain.session.SessionRecovery
import dev.jdtech.jellyfin.setup.domain.session.SignInReason
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.exception.TimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRecoveryTest {
    @Test
    fun `offline mode skips the check`() = runTest {
        val access = FakeSessionAccess(offline = true)

        assertEquals(SessionCheckResult.Skipped, SessionRecovery(access).check())
        assertEquals(0, access.verifyCalls)
    }

    @Test
    fun `no signed-in user skips the check`() = runTest {
        val access = FakeSessionAccess(user = null)

        assertEquals(SessionCheckResult.Skipped, SessionRecovery(access).check())
        assertEquals(0, access.verifyCalls)
    }

    @Test
    fun `working login is valid and never signs in again`() = runTest {
        val access = FakeSessionAccess()

        assertEquals(SessionCheckResult.Valid(testUser.id), SessionRecovery(access).check())
        assertTrue(access.logins.isEmpty())
    }

    @Test
    fun `unreachable server keeps the saved login`() = runTest {
        val access = FakeSessionAccess(verifyError = TimeoutException("offline"))

        assertEquals(SessionCheckResult.Unreachable(testUser.id), SessionRecovery(access).check())
        assertTrue(access.logins.isEmpty())
    }

    @Test
    fun `server error keeps the saved login`() = runTest {
        val access = FakeSessionAccess(verifyError = InvalidStatusException(503))

        assertEquals(SessionCheckResult.Unreachable(testUser.id), SessionRecovery(access).check())
        assertTrue(access.logins.isEmpty())
    }

    @Test
    fun `dead login is replaced with the saved password`() = runTest {
        val access = FakeSessionAccess(verifyError = InvalidStatusException(401))

        assertEquals(SessionCheckResult.Recovered(testUser.id), SessionRecovery(access).check())
        assertEquals(listOf("dragon" to "secret"), access.logins)
    }

    @Test
    fun `refused login is replaced with the saved password`() = runTest {
        val access = FakeSessionAccess(verifyError = InvalidStatusException(403))

        assertEquals(SessionCheckResult.Recovered(testUser.id), SessionRecovery(access).check())
        assertEquals(1, access.logins.size)
    }

    @Test
    fun `dead login without a saved password asks to sign in`() = runTest {
        val access = FakeSessionAccess(verifyError = InvalidStatusException(401), password = null)

        assertEquals(
            SessionCheckResult.SignInRequired("dragon", SignInReason.SESSION_ENDED),
            SessionRecovery(access).check(),
        )
        assertTrue(access.logins.isEmpty())
    }

    @Test
    fun `rejected saved password is deleted and never retried`() = runTest {
        val access =
            FakeSessionAccess(
                verifyError = InvalidStatusException(401),
                loginError = InvalidStatusException(401),
            )
        val recovery = SessionRecovery(access)

        assertEquals(
            SessionCheckResult.SignInRequired("dragon", SignInReason.PASSWORD_CHANGED),
            recovery.check(),
        )
        assertNull(access.password)

        assertEquals(
            SessionCheckResult.SignInRequired("dragon", SignInReason.SESSION_ENDED),
            recovery.check(),
        )
        assertEquals(1, access.logins.size)
    }

    @Test
    fun `blocked account asks to sign in and keeps the password`() = runTest {
        val access =
            FakeSessionAccess(
                verifyError = InvalidStatusException(401),
                loginError = InvalidStatusException(403),
            )

        assertEquals(
            SessionCheckResult.SignInRequired("dragon", SignInReason.ACCOUNT_BLOCKED),
            SessionRecovery(access).check(),
        )
        assertEquals("secret", access.password)
    }

    @Test
    fun `network failure during the silent sign-in asks to sign in and keeps the password`() =
        runTest {
            val access =
                FakeSessionAccess(
                    verifyError = InvalidStatusException(401),
                    loginError = ApiClientException("io", IOException()),
                )

            assertEquals(
                SessionCheckResult.SignInRequired("dragon", SignInReason.SESSION_ENDED),
                SessionRecovery(access).check(),
            )
            assertEquals("secret", access.password)
        }
}
