package dev.jdtech.jellyfin.setup.session

import dev.jdtech.jellyfin.models.User
import dev.jdtech.jellyfin.setup.domain.session.SessionAccess
import java.util.UUID
import kotlinx.coroutines.delay

internal val testUser = User(id = UUID(0L, 1L), name = "dragon", serverId = "server-id", accessToken = "dead")

internal class FakeSessionAccess(
    var offline: Boolean = false,
    var user: User? = testUser,
    var verifyError: Exception? = null,
    var verifyDelayMs: Long = 0L,
    var password: String? = "secret",
    var loginError: Exception? = null,
) : SessionAccess {
    var verifyCalls = 0
        private set

    val logins = mutableListOf<Pair<String, String>>()

    var signOutCalls = 0
        private set

    override suspend fun isOfflineMode(): Boolean = offline

    override suspend fun currentUser(): User? = user

    override suspend fun verifySession() {
        verifyCalls++
        if (verifyDelayMs > 0) delay(verifyDelayMs)
        verifyError?.let { throw it }
    }

    override suspend fun storedPassword(user: User): String? = password

    override suspend fun clearStoredPassword(user: User) {
        password = null
    }

    override suspend fun login(username: String, password: String) {
        logins += username to password
        loginError?.let { throw it }
    }

    override suspend fun signOutCurrentUser() {
        signOutCalls++
        user = null
    }
}
