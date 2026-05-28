package dev.jdtech.jellyfin.setup.data

import dev.jdtech.jellyfin.models.Server
import dev.jdtech.jellyfin.models.ServerAddress
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupRepositoryImplTest {
    private val configuredServerUrl = "https://media.example.invalid"

    @Test
    fun `buildSavedServerResult reuses existing address without inserting duplicate`() {
        val existingAddressId = UUID.randomUUID()
        val existingServer =
            Server(
                id = "server-id",
                name = "DragonDB",
                currentServerAddressId = existingAddressId,
                currentUserId = null,
            )
        val existingAddress =
            ServerAddress(
                id = existingAddressId,
                serverId = existingServer.id,
                address = configuredServerUrl,
            )

        val result =
            buildSavedServerResult(
                existingServer = existingServer,
                existingAddresses = listOf(existingAddress),
                serverId = existingServer.id,
                serverName = existingServer.name,
                recommendedAddress = existingAddress.address,
            )

        assertEquals(existingServer.id, result.server.id)
        assertEquals(existingAddressId, result.server.currentServerAddressId)
        assertEquals(existingAddress, result.serverAddress)
        assertFalse(result.shouldInsertServer)
        assertFalse(result.shouldInsertAddress)
        assertFalse(result.shouldUpdateServer)
    }

    @Test
    fun `buildSavedServerResult adds missing address and switches current address`() {
        val currentAddressId = UUID.randomUUID()
        val newAddressId = UUID.randomUUID()
        val existingServer =
            Server(
                id = "server-id",
                name = "DragonDB",
                currentServerAddressId = currentAddressId,
                currentUserId = null,
            )
        val existingAddress =
            ServerAddress(
                id = currentAddressId,
                serverId = existingServer.id,
                address = "http://old-server:8096",
            )

        val result =
            buildSavedServerResult(
                existingServer = existingServer,
                existingAddresses = listOf(existingAddress),
                serverId = existingServer.id,
                serverName = existingServer.name,
                recommendedAddress = configuredServerUrl,
                generatedAddressId = newAddressId,
            )

        assertEquals(existingServer.id, result.server.id)
        assertEquals(newAddressId, result.server.currentServerAddressId)
        assertEquals(configuredServerUrl, result.serverAddress.address)
        assertTrue(result.shouldInsertAddress)
        assertTrue(result.shouldUpdateServer)
        assertFalse(result.shouldInsertServer)
    }
}
