package dev.jdtech.jellyfin.setup.data

import dev.jdtech.jellyfin.api.JellyfinApi
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.User
import dev.jdtech.jellyfin.setup.domain.LoginPasswordSaver
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.Response
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.operations.AuthenticationApi
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.UserDto
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Saving the login (current user in the database) switches the app to Home and removes the login
 * screen. The password must be stored before that, inside the sign-in itself.
 */
class SetupRepositoryLoginTest {
    private val userId = UUID(0L, 7L)
    private val authenticationApi = mockk<AuthenticationApi>()
    private val jellyfinApi =
        mockk<JellyfinApi>(relaxed = true) {
            every { authenticationApi } returns this@SetupRepositoryLoginTest.authenticationApi
        }
    private val database = mockk<ServerDatabaseDao>(relaxed = true)
    private val passwordSaver = mockk<LoginPasswordSaver>(relaxed = true)

    @Test
    fun `password is stored before the login opens home`() = runTest {
        coEvery { authenticationApi.authenticateUserByName(any()) } returns signedIn()

        repository(passwordSaver).login("dragon", "secret")

        coVerifyOrder {
            passwordSaver.onSignedIn("server-id", "dragon", "secret")
            database.insertUser(User(userId, "dragon", "server-id", "token"))
            database.updateServerCurrentUser("server-id", userId)
        }
    }

    @Test
    fun `password survives the login screen going away when home opens`() = runTest {
        coEvery { authenticationApi.authenticateUserByName(any()) } returns signedIn()
        val loginScreenScope = CoroutineScope(Job())
        // Recording the signed-in user switches the app to Home, which removes the login screen and
        // cancels whatever it was still running.
        coEvery { database.updateServerCurrentUser(any(), any()) } answers { loginScreenScope.cancel() }

        loginScreenScope.launch { repository(passwordSaver).login("dragon", "secret") }.join()

        verify { passwordSaver.onSignedIn("server-id", "dragon", "secret") }
    }

    @Test
    fun `quick connect forgets the old password before the login opens home`() = runTest {
        coEvery { authenticationApi.authenticateWithQuickConnect(any()) } returns signedIn()

        repository(passwordSaver).loginWithSecret("quick-connect-secret")

        coVerifyOrder {
            passwordSaver.onSignedIn("server-id", "dragon", null)
            database.updateServerCurrentUser("server-id", userId)
        }
    }

    @Test
    fun `rejected sign-in stores nothing`() = runTest {
        coEvery { authenticationApi.authenticateUserByName(any()) } throws
            InvalidStatusException(401)

        val error = runCatching { repository(passwordSaver).login("dragon", "wrong") }.exceptionOrNull()

        assertTrue(error is InvalidStatusException)

        verify(exactly = 0) { passwordSaver.onSignedIn(any(), any(), any()) }
        coVerify(exactly = 0) { database.updateServerCurrentUser(any(), any()) }
    }

    @Test
    fun `without a saver (TV) the sign-in still works`() = runTest {
        coEvery { authenticationApi.authenticateUserByName(any()) } returns signedIn()

        repository(passwordSaver = null).login("dragon", "secret")

        coVerify { database.updateServerCurrentUser("server-id", userId) }
    }

    private fun repository(passwordSaver: LoginPasswordSaver?) =
        SetupRepositoryImpl(
            jellyfinApi = jellyfinApi,
            database = database,
            appPreferences = mockk(relaxed = true),
            secureCredentialsStore = mockk(relaxed = true),
            loginPasswordSaver = passwordSaver,
        )

    private fun signedIn(): Response<AuthenticationResult> {
        val user = mockk<UserDto> {
            every { id } returns userId
            every { name } returns "dragon"
        }
        val result = AuthenticationResult(user = user, accessToken = "token", serverId = "server-id")
        return Response(result, 200, emptyMap())
    }
}
