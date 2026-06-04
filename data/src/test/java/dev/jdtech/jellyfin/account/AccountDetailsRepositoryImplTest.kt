package dev.jdtech.jellyfin.account

import android.content.SharedPreferences
import dev.jdtech.jellyfin.account.api.JfaGoApiService
import dev.jdtech.jellyfin.account.api.JfaGoApiException
import dev.jdtech.jellyfin.account.api.dto.JfaGoContactMethodDto
import dev.jdtech.jellyfin.account.api.dto.JfaGoMyDetailsDto
import dev.jdtech.jellyfin.account.api.dto.JfaGoTokenDto
import dev.jdtech.jellyfin.auth.SecureCredentialsStore
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.Server
import dev.jdtech.jellyfin.models.ServerWithAddressAndUser
import dev.jdtech.jellyfin.models.User
import dev.jdtech.jellyfin.settings.domain.AccountDetailsResult
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AccountDetailsRepositoryImplTest {
    private val apiService = mockk<JfaGoApiService>()
    private val serverDatabase = mockk<ServerDatabaseDao>()
    private val secureCredentialsStore = mockk<SecureCredentialsStore>()
    private val sharedPreferences = FakeSharedPreferences()
    private val appPreferences = AppPreferences(sharedPreferences)

    private lateinit var repository: AccountDetailsRepositoryImpl

    @Before
    fun setUp() {
        repository =
            AccountDetailsRepositoryImpl(
                apiService = apiService,
                serverDatabase = serverDatabase,
                appPreferences = appPreferences,
                secureCredentialsStore = secureCredentialsStore,
            )

        sharedPreferences.edit().putString("pref_current_server", "server-id").apply()
        every { serverDatabase.getServerWithAddressAndUser("server-id") } returns serverWithUser()
        every { secureCredentialsStore.getUserPassword("server-id", "dragon") } returns "password"
    }

    @Test
    fun `loads account details with email and expiry`() = runTest {
        coEvery { apiService.login("dragon", "password") } returns JfaGoTokenDto("jwt")
        coEvery { apiService.getMyDetails("jwt") } returns
            JfaGoMyDetailsDto(
                username = "dragon",
                expiry = 1_748_908_800,
                email = JfaGoContactMethodDto(value = "dragon@example.com"),
            )

        val result = repository.getAccountDetails() as AccountDetailsResult.Success
        val details = result.details

        assertEquals("dragon", details.username)
        assertEquals("dragon@example.com", details.email)
        assertEquals(1_748_908_800L, details.expiryEpochSeconds)
        coVerify(exactly = 1) { apiService.login("dragon", "password") }
        coVerify(exactly = 1) { apiService.getMyDetails("jwt") }
    }

    @Test
    fun `returns diagnostic failure when stored password is missing`() = runTest {
        every { secureCredentialsStore.getUserPassword("server-id", "dragon") } returns null

        val result = repository.getAccountDetails() as AccountDetailsResult.Failure

        assertEquals(
            "No stored Jellyfin password is available for this user. Sign in with username and password first.",
            result.message,
        )
        coVerify(exactly = 0) { apiService.login(any(), any()) }
    }

    @Test
    fun `keeps no expiry value from details response`() = runTest {
        coEvery { apiService.login("dragon", "password") } returns JfaGoTokenDto("jwt")
        coEvery { apiService.getMyDetails("jwt") } returns
            JfaGoMyDetailsDto(username = "dragon", expiry = 0)

        val result = repository.getAccountDetails() as AccountDetailsResult.Success
        val details = result.details

        assertEquals(0L, details.expiryEpochSeconds)
    }

    @Test
    fun `maps missing email to null`() = runTest {
        coEvery { apiService.login("dragon", "password") } returns JfaGoTokenDto("jwt")
        coEvery { apiService.getMyDetails("jwt") } returns
            JfaGoMyDetailsDto(username = "dragon", email = JfaGoContactMethodDto(value = ""))

        val result = repository.getAccountDetails() as AccountDetailsResult.Success
        val details = result.details

        assertNull(details.email)
    }

    @Test
    fun `returns API failure status without exposing server response body`() = runTest {
        coEvery { apiService.login("dragon", "password") } throws
            JfaGoApiException(500, "Contact Admin")

        val result = repository.getAccountDetails() as AccountDetailsResult.Failure

        assertEquals("JFA-GO login failed (HTTP 500).", result.message)
    }

    private fun serverWithUser(): ServerWithAddressAndUser {
        val userId = UUID.randomUUID()
        return ServerWithAddressAndUser(
            server =
                Server(
                    id = "server-id",
                    name = "DragonDB",
                    currentServerAddressId = null,
                    currentUserId = userId,
                ),
            address = null,
            user = User(id = userId, name = "dragon", serverId = "server-id"),
        )
    }
}

private class FakeSharedPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values

    override fun getString(key: String?, defValue: String?): String? =
        values[key] as String? ?: defValue

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        @Suppress("UNCHECKED_CAST") (values[key] as MutableSet<String>?) ?: defValues

    override fun getInt(key: String?, defValue: Int): Int = values[key] as Int? ?: defValue

    override fun getLong(key: String?, defValue: Long): Long = values[key] as Long? ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float = values[key] as Float? ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        values[key] as Boolean? ?: defValue

    override fun contains(key: String?): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor(values)

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = Unit

    private class Editor(private val values: MutableMap<String, Any?>) : SharedPreferences.Editor {
        override fun putString(key: String?, value: String?): SharedPreferences.Editor =
            apply { values[key!!] = value }

        override fun putStringSet(
            key: String?,
            values: MutableSet<String>?
        ): SharedPreferences.Editor =
            apply { this.values[key!!] = values }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor =
            apply { values[key!!] = value }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor =
            apply { values[key!!] = value }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor =
            apply { values[key!!] = value }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor =
            apply { values[key!!] = value }

        override fun remove(key: String?): SharedPreferences.Editor = apply { values.remove(key) }

        override fun clear(): SharedPreferences.Editor = apply { values.clear() }

        override fun commit(): Boolean = true

        override fun apply() = Unit
    }
}
