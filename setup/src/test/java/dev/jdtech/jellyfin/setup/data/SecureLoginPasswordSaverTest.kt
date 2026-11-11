package dev.jdtech.jellyfin.setup.data

import dev.jdtech.jellyfin.auth.SecureCredentialsStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class SecureLoginPasswordSaverTest {
    private val store = mockk<SecureCredentialsStore>(relaxed = true)
    private val saver = SecureLoginPasswordSaver(store)

    @Test
    fun `password sign-in stores the password and drops the old Seerr session`() {
        saver.onSignedIn("server-id", "dragon", "secret")

        verify { store.saveUserPassword("server-id", "dragon", "secret") }
        verify { store.clearJellyseerrSession("server-id", "dragon") }
        verify(exactly = 0) { store.clearUserPassword(any(), any()) }
    }

    @Test
    fun `quick connect sign-in forgets the old password`() {
        saver.onSignedIn("server-id", "dragon", null)

        verify { store.clearUserPassword("server-id", "dragon") }
        verify { store.clearJellyseerrSession("server-id", "dragon") }
        verify(exactly = 0) { store.saveUserPassword(any(), any(), any()) }
    }

    @Test
    fun `a broken store does not fail the sign-in`() {
        every { store.saveUserPassword(any(), any(), any()) } throws SecurityException("keystore")

        saver.onSignedIn("server-id", "dragon", "secret")
    }
}
