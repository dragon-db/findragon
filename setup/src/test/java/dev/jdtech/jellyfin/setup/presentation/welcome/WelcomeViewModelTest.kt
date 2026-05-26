package dev.jdtech.jellyfin.setup.presentation.welcome

import android.content.SharedPreferences
import dev.jdtech.jellyfin.models.ExceptionUiText
import dev.jdtech.jellyfin.models.Server
import dev.jdtech.jellyfin.models.ServerWithAddresses
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.models.User
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.setup.R
import dev.jdtech.jellyfin.setup.domain.SetupRepository
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jellyfin.sdk.model.api.QuickConnectResult
import org.jellyfin.sdk.model.api.ServerDiscoveryInfo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WelcomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val configuredServerUrl = "https://media.example.invalid"

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `continue connects to DragonDB and navigates to users`() = runTest(dispatcher) {
        val repository = FakeSetupRepository()
        val sharedPreferences = FakeSharedPreferences()
        val viewModel =
            WelcomeViewModel(
                repository = repository,
                appPreferences = AppPreferences(sharedPreferences),
                defaultServerConfig = FakeDefaultServerConfig(configuredServerUrl),
            )

        val event = async { viewModel.events.first() }
        viewModel.onAction(WelcomeAction.OnContinueClick)
        advanceUntilIdle()

        assertEquals(WelcomeEvent.ContinueSuccess, event.await())
        assertEquals(configuredServerUrl, repository.addedAddresses.single())
        assertEquals("server-id", sharedPreferences.storedString("pref_current_server"))
        assertFalse(viewModel.state.value.isLoading)
        assertEquals(null, viewModel.state.value.error)
        assertFalse(viewModel.state.value.showManualServerFallback)
    }

    @Test
    fun `continue failure shows error and manual fallback`() = runTest(dispatcher) {
        val repository =
            FakeSetupRepository(
                addServerError =
                    ExceptionUiText(
                        UiText.StringResource(R.string.add_server_error_not_found)
                    )
            )
        val viewModel =
            WelcomeViewModel(
                repository = repository,
                appPreferences = AppPreferences(FakeSharedPreferences()),
                defaultServerConfig = FakeDefaultServerConfig(configuredServerUrl),
            )

        viewModel.onAction(WelcomeAction.OnContinueClick)
        advanceUntilIdle()

        assertEquals(configuredServerUrl, repository.addedAddresses.single())
        assertFalse(viewModel.state.value.isLoading)
        assertTrue(viewModel.state.value.showManualServerFallback)
        assertNotNull(viewModel.state.value.error)
        val error = viewModel.state.value.error!!.single() as UiText.StringResource
        assertEquals(R.string.add_server_error_not_found, error.resId)
    }

    @Test
    fun `continue without configured server opens manual setup without connecting`() =
        runTest(dispatcher) {
            val repository = FakeSetupRepository()
            val viewModel =
                WelcomeViewModel(
                    repository = repository,
                    appPreferences = AppPreferences(FakeSharedPreferences()),
                    defaultServerConfig = FakeDefaultServerConfig(""),
                )

            val event = async { viewModel.events.first() }
            viewModel.onAction(WelcomeAction.OnContinueClick)
            advanceUntilIdle()

            assertEquals(WelcomeEvent.OpenManualServerSetup, event.await())
            assertTrue(repository.addedAddresses.isEmpty())
        }
}

private class FakeDefaultServerConfig(override val serverUrl: String) : DefaultServerConfig

private class FakeSetupRepository(
    private val addServerError: Exception? = null,
) : SetupRepository {
    val addedAddresses = mutableListOf<String>()

    override fun discoverServers(): Flow<ServerDiscoveryInfo> = emptyFlow()

    override suspend fun getServers(): List<ServerWithAddresses> = emptyList()

    override suspend fun getCurrentServer(): Server? = null

    override suspend fun deleteServer(serverId: String) = Unit

    override suspend fun getIsQuickConnectEnabled(): Boolean = false

    override suspend fun initiateQuickConnect(): QuickConnectResult {
        error("Not used in test")
    }

    override suspend fun getQuickConnectState(secret: String): QuickConnectResult {
        error("Not used in test")
    }

    override suspend fun setCurrentServer(serverId: String) = Unit

    override suspend fun addServer(address: String): Server {
        addedAddresses += address
        addServerError?.let { throw it }

        return Server(
            id = "server-id",
            name = "DragonDB",
            currentServerAddressId = null,
            currentUserId = null,
        )
    }

    override suspend fun loadDisclaimer(): String? = null

    override suspend fun login(username: String, password: String) = Unit

    override suspend fun loginWithSecret(secret: String) = Unit

    override suspend fun getUsers(serverId: String): List<User> = emptyList()

    override suspend fun getPublicUsers(serverId: String): List<User> = emptyList()

    override suspend fun getCurrentUser(): User? = null

    override suspend fun deleteUser(userId: UUID) = Unit

    override suspend fun setCurrentUser(userId: UUID) = Unit

    override suspend fun setCurrentAddress(addressId: UUID) = Unit
}

private class FakeSharedPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    fun storedString(key: String): String? = values[key] as String?

    override fun getAll(): MutableMap<String, *> = values

    override fun getString(key: String?, defValue: String?): String? =
        values[key] as String? ?: defValue

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        @Suppress("UNCHECKED_CAST") (values[key] as MutableSet<String>?) ?: defValues

    override fun getInt(key: String?, defValue: Int): Int = values[key] as Int? ?: defValue

    override fun getLong(key: String?, defValue: Long): Long = values[key] as Long? ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float =
        values[key] as Float? ?: defValue

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
            apply {
                values[key!!] = value
            }

        override fun putStringSet(
            key: String?,
            values: MutableSet<String>?
        ): SharedPreferences.Editor =
            apply {
                this.values[key!!] = values
            }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor =
            apply {
                values[key!!] = value
            }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor =
            apply {
                values[key!!] = value
            }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor =
            apply {
                values[key!!] = value
            }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor =
            apply {
                values[key!!] = value
            }

        override fun remove(key: String?): SharedPreferences.Editor =
            apply {
                values.remove(key)
            }

        override fun clear(): SharedPreferences.Editor =
            apply {
                values.clear()
            }

        override fun commit(): Boolean = true

        override fun apply() = Unit
    }
}
