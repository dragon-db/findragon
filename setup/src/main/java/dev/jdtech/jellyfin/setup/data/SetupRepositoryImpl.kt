package dev.jdtech.jellyfin.setup.data

import dev.jdtech.jellyfin.api.JellyfinApi
import dev.jdtech.jellyfin.auth.SecureCredentialsStore
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.ExceptionUiText
import dev.jdtech.jellyfin.models.ExceptionUiTexts
import dev.jdtech.jellyfin.models.Server
import dev.jdtech.jellyfin.models.ServerAddress
import dev.jdtech.jellyfin.models.ServerWithAddresses
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.models.User
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.setup.R as SetupR
import dev.jdtech.jellyfin.setup.domain.SetupRepository
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.discovery.RecommendedServerInfo
import org.jellyfin.sdk.discovery.RecommendedServerInfoScore
import org.jellyfin.sdk.discovery.RecommendedServerIssue
import org.jellyfin.sdk.model.api.AuthenticateUserByName
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.QuickConnectDto
import org.jellyfin.sdk.model.api.QuickConnectResult
import org.jellyfin.sdk.model.api.ServerDiscoveryInfo
import timber.log.Timber

internal data class SavedServerResult(
    val server: Server,
    val serverAddress: ServerAddress,
    val shouldInsertServer: Boolean,
    val shouldInsertAddress: Boolean,
    val shouldUpdateServer: Boolean,
)

internal fun buildSavedServerResult(
    existingServer: Server?,
    existingAddresses: List<ServerAddress>,
    serverId: String,
    serverName: String,
    recommendedAddress: String,
    generatedAddressId: UUID = UUID.randomUUID(),
): SavedServerResult {
    if (existingServer == null) {
        val serverAddress =
            ServerAddress(
                id = generatedAddressId,
                serverId = serverId,
                address = recommendedAddress,
            )
        val server =
            Server(
                id = serverId,
                name = serverName,
                currentServerAddressId = serverAddress.id,
                currentUserId = null,
            )

        return SavedServerResult(
            server = server,
            serverAddress = serverAddress,
            shouldInsertServer = true,
            shouldInsertAddress = true,
            shouldUpdateServer = false,
        )
    }

    val existingAddress = existingAddresses.firstOrNull { it.address == recommendedAddress }
    val serverAddress =
        existingAddress
            ?: ServerAddress(
                id = generatedAddressId,
                serverId = existingServer.id,
                address = recommendedAddress,
            )
    val updatedServer = existingServer.copy(currentServerAddressId = serverAddress.id)

    return SavedServerResult(
        server = updatedServer,
        serverAddress = serverAddress,
        shouldInsertServer = false,
        shouldInsertAddress = existingAddress == null,
        shouldUpdateServer = updatedServer != existingServer,
    )
}

class SetupRepositoryImpl(
    private val jellyfinApi: JellyfinApi,
    private val database: ServerDatabaseDao,
    private val appPreferences: AppPreferences,
    private val secureCredentialsStore: SecureCredentialsStore,
) : SetupRepository {
    override fun discoverServers(): Flow<ServerDiscoveryInfo> {
        return jellyfinApi.jellyfin.discovery.discoverLocalServers()
    }

    override suspend fun getServers(): List<ServerWithAddresses> {
        return database.getServersWithAddresses()
    }

    override suspend fun getCurrentServer(): Server? {
        return appPreferences.getValue(appPreferences.currentServer)?.let { id -> database.get(id) }
    }

    override suspend fun deleteServer(serverId: String) {
        secureCredentialsStore.clearSecretsForServer(serverId)
        database.delete(serverId)
    }

    override suspend fun getIsQuickConnectEnabled(): Boolean =
        withContext(Dispatchers.IO) { jellyfinApi.quickConnectApi.getQuickConnectEnabled().content }

    override suspend fun initiateQuickConnect(): QuickConnectResult =
        withContext(Dispatchers.IO) { jellyfinApi.quickConnectApi.initiateQuickConnect().content }

    override suspend fun getQuickConnectState(secret: String): QuickConnectResult =
        withContext(Dispatchers.IO) {
            jellyfinApi.quickConnectApi.getQuickConnectState(secret).content
        }

    override suspend fun setCurrentServer(serverId: String) {
        val serverWithAddressAndUser = database.getServerWithAddressAndUser(serverId) ?: return
        val serverAddress = serverWithAddressAndUser.address ?: return
        val user = serverWithAddressAndUser.user

        jellyfinApi.apply {
            api.update(baseUrl = serverAddress.address, accessToken = user?.accessToken)
            userId = user?.id
        }
    }

    override suspend fun addServer(address: String): Server {
        // Check if address is not blank
        if (address.isBlank()) {
            throw ExceptionUiText(
                UiText.StringResource(SetupR.string.add_server_error_empty_address)
            )
        }

        val candidates = jellyfinApi.jellyfin.discovery.getAddressCandidates(address)
        val recommended =
            jellyfinApi.jellyfin.discovery.getRecommendedServers(
                candidates,
                RecommendedServerInfoScore.OK,
            )
        val goodServers = mutableListOf<RecommendedServerInfo>()
        val okServers = mutableListOf<RecommendedServerInfo>()

        for (recommendedServerInfo in recommended) {
            when (recommendedServerInfo.score) {
                RecommendedServerInfoScore.GREAT -> {
                    return saveServerInDatabase(recommendedServerInfo)
                }
                RecommendedServerInfoScore.GOOD -> goodServers.add(recommendedServerInfo)
                RecommendedServerInfoScore.OK -> okServers.add(recommendedServerInfo)
                RecommendedServerInfoScore.BAD -> Unit
            }
        }

        when {
            goodServers.isNotEmpty() -> {
                return saveServerInDatabase(goodServers.first())
            }
            okServers.isNotEmpty() -> {
                val okServer = okServers.first()
                throw ExceptionUiTexts(createIssuesString(okServer))
            }
            else -> {
                throw ExceptionUiText(
                    UiText.StringResource(SetupR.string.add_server_error_not_found)
                )
            }
        }
    }

    private fun saveServerInDatabase(recommendedServerInfo: RecommendedServerInfo): Server {
        val serverInfo =
            recommendedServerInfo.systemInfo.getOrNull()
                ?: throw ExceptionUiText(
                    UiText.StringResource(SetupR.string.add_server_error_no_id)
                )

        Timber.d("Connecting to server: ${serverInfo.serverName}")

        val serverInDatabase = database.get(serverInfo.id!!)
        val existingAddresses =
            serverInDatabase?.let { database.getServerWithAddresses(serverInDatabase.id).addresses }
                ?: emptyList()
        val savedServerResult =
            buildSavedServerResult(
                existingServer = serverInDatabase,
                existingAddresses = existingAddresses,
                serverId = serverInfo.id!!,
                serverName = serverInfo.serverName!!,
                recommendedAddress = recommendedServerInfo.address,
            )

        if (savedServerResult.shouldInsertServer) {
            database.insertServer(savedServerResult.server)
        }
        if (savedServerResult.shouldInsertAddress) {
            database.insertServerAddress(savedServerResult.serverAddress)
        }
        if (savedServerResult.shouldUpdateServer) {
            database.update(savedServerResult.server)
        }

        jellyfinApi.apply {
            api.update(baseUrl = recommendedServerInfo.address, accessToken = null)
        }

        return savedServerResult.server
    }

    /**
     * Create a presentable string of issues with a server
     *
     * @param server The server with issues
     * @return A presentable string of issues separated with \n
     */
    private fun createIssuesString(server: RecommendedServerInfo): Collection<UiText> {
        return server.issues.map {
            when (it) {
                is RecommendedServerIssue.OutdatedServerVersion -> {
                    UiText.StringResource(SetupR.string.add_server_error_outdated, it.version)
                }
                is RecommendedServerIssue.InvalidProductName -> {
                    UiText.StringResource(
                        SetupR.string.add_server_error_not_jellyfin,
                        it.productName ?: "",
                    )
                }
                is RecommendedServerIssue.UnsupportedServerVersion -> {
                    UiText.StringResource(SetupR.string.add_server_error_version, it.version)
                }
                is RecommendedServerIssue.SlowResponse -> {
                    UiText.StringResource(SetupR.string.add_server_error_slow, it.responseTime)
                }
                else -> {
                    UiText.StringResource(CoreR.string.unknown_error)
                }
            }
        }
    }

    override suspend fun loadDisclaimer(): String? =
        withContext(Dispatchers.IO) {
            jellyfinApi.brandingApi.getBrandingOptions().content.loginDisclaimer
        }

    override suspend fun login(username: String, password: String) {
        withContext(Dispatchers.IO) {
            val authenticationResult by
                jellyfinApi.userApi.authenticateUserByName(
                    data = AuthenticateUserByName(username = username, pw = password)
                )

            saveAuthenticationResult(authenticationResult)
        }
    }

    override suspend fun loginWithSecret(secret: String) {
        withContext(Dispatchers.IO) {
            val authenticationResult by
                jellyfinApi.userApi.authenticateWithQuickConnect(
                    data = QuickConnectDto(secret = secret)
                )

            saveAuthenticationResult(authenticationResult)
        }
    }

    private fun saveAuthenticationResult(authenticationResult: AuthenticationResult) {
        val user =
            User(
                id = authenticationResult.user!!.id,
                name = authenticationResult.user!!.name!!,
                serverId = authenticationResult.serverId!!,
                accessToken = authenticationResult.accessToken!!,
            )

        database.insertUser(user)
        database.updateServerCurrentUser(authenticationResult.serverId!!, user.id)

        jellyfinApi.apply {
            api.update(accessToken = authenticationResult.accessToken)
            userId = authenticationResult.user?.id
        }
    }

    override suspend fun getUsers(serverId: String): List<User> {
        return database.getUsers(serverId)
    }

    override suspend fun getPublicUsers(serverId: String): List<User> =
        withContext(Dispatchers.IO) {
            jellyfinApi.userApi.getPublicUsers().content.mapNotNull {
                User(id = it.id, name = it.name ?: return@mapNotNull null, serverId = serverId)
            }
        }

    override suspend fun getCurrentUser(): User? {
        val currentServer = getCurrentServer() ?: return null
        return database.getServerCurrentUser(currentServer.id)
    }

    override suspend fun deleteUser(userId: UUID) {
        val user = database.getUser(userId) ?: return
        secureCredentialsStore.clearUserPassword(user.serverId, user.name)
        secureCredentialsStore.clearJellyseerrSession(user.serverId, user.name)

        // Let the user delete the current active user for now
        /*val currentUser = getCurrentUser() ?: return
        if (userId == currentUser.id) {
            Timber.e("You cannot delete the current user")
            return
        }*/
        database.deleteUser(userId)
    }

    override suspend fun setCurrentUser(userId: UUID) {
        val server = getCurrentServer() ?: return
        val user = database.getUser(userId) ?: return
        server.currentUserId = user.id
        database.update(server)

        jellyfinApi.apply {
            api.update(accessToken = user.accessToken)
            this.userId = user.id
        }
    }

    override suspend fun setCurrentAddress(addressId: UUID) {
        val server = getCurrentServer() ?: return
        val address = database.getAddress(id = addressId)
        if (address.serverId != server.id) {
            return
        }
        server.currentServerAddressId = address.id
        database.update(server)
        jellyfinApi.apply { api.update(baseUrl = address.address) }
    }
}
