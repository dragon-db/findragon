package dev.jdtech.jellyfin.dragonhub.account

import android.content.SharedPreferences
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.dragonhub.api.DragonHubAccountResponse
import dev.jdtech.jellyfin.dragonhub.api.DragonHubApiService
import dev.jdtech.jellyfin.dragonhub.api.dto.DragonHubAccountDto
import dev.jdtech.jellyfin.dragonhub.api.dto.DragonHubPlanDto
import dev.jdtech.jellyfin.models.Server
import dev.jdtech.jellyfin.models.ServerWithAddressAndUser
import dev.jdtech.jellyfin.models.User
import dev.jdtech.jellyfin.settings.domain.AccountDetailsResult
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.settings.domain.models.AccountState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DragonHubAccountTest {
    private val apiService = mockk<DragonHubApiService>()
    private val database = mockk<ServerDatabaseDao>()
    private val sharedPreferences =
        mockk<SharedPreferences> {
            every { getString("pref_current_server", any()) } returns "server-id"
        }

    @Test
    fun `maps a blocked reason to an account block`() {
        assertNull(dto(disabledReason = null).toAccountStatus().block)
        assertEquals(AccountBlock.EXPIRED, dto(disabledReason = "expired").toAccountStatus().block)
        assertEquals(
            AccountBlock.DISABLED_BY_ADMIN,
            dto(disabledReason = "admin").toAccountStatus().block,
        )
        assertEquals(
            AccountBlock.DISABLED_ON_SERVER,
            dto(disabledReason = "external").toAccountStatus().block,
        )
        assertEquals(
            AccountBlock.DISABLED_ON_SERVER,
            dto(disabledReason = "something_new").toAccountStatus().block,
        )
    }

    @Test
    fun `parses the subscription end and drops blank texts`() {
        val status =
            dto(subscriptionEnd = "2026-11-01T00:00:00+00:00", message = " ", actionUrl = "")
                .toAccountStatus()

        assertEquals(1_793_491_200L, status.subscriptionEndEpochSeconds)
        assertNull(status.message)
        assertNull(status.actionUrl)
        assertNull(dto(subscriptionEnd = null).toAccountStatus().subscriptionEndEpochSeconds)
        assertNull(dto(subscriptionEnd = "not a date").toAccountStatus().subscriptionEndEpochSeconds)
    }

    @Test
    fun `reads hub dates with or without an offset`() {
        // 2026-11-02T00:00:00Z
        val expected = 1_793_577_600L

        assertEquals(expected, parseHubDateTime("2026-11-02T00:00:00+00:00"))
        assertEquals(expected, parseHubDateTime("2026-11-02T00:00:00Z"))
        assertEquals(expected + 3_600, parseHubDateTime("2026-11-02T00:00:00-01:00"))
        // What the hub actually sends (no offset, like its notification dates): UTC.
        assertEquals(expected, parseHubDateTime("2026-11-02T00:00:00"))
        assertEquals(expected, parseHubDateTime("2026-11-02T00:00:00.038813"))
        assertEquals(expected, parseHubDateTime("2026-11-02 00:00:00"))
        assertEquals(expected, parseHubDateTime("2026-11-02"))
        assertNull(parseHubDateTime("soon"))
    }

    @Test
    fun `account card shows the expiry of a hub date without an offset`() = runTest {
        val repository =
            DragonHubAccountDetailsRepository(
                fakeSource(
                    AccountStatusResult.Known(
                        dto(subscriptionEnd = "2026-11-02T00:00:00").toAccountStatus()
                    )
                )
            )

        val details = (repository.getAccountDetails() as AccountDetailsResult.Success).details

        assertEquals(1_793_577_600L, details.expiryEpochSeconds)
    }

    @Test
    fun `sends the saved Jellyfin token of the current user`() = runTest {
        coEvery { database.getServerWithAddressAndUser("server-id") } returns serverWithUser("token-1")
        coEvery { apiService.getAccount("token-1") } returns
            DragonHubAccountResponse.Success(dto(disabledReason = "expired"))

        val result = source().fetch() as AccountStatusResult.Known

        assertEquals(AccountBlock.EXPIRED, result.status.block)
        coVerify(exactly = 1) { apiService.getAccount("token-1") }
    }

    @Test
    fun `unknown token is reported separately`() = runTest {
        coEvery { database.getServerWithAddressAndUser("server-id") } returns serverWithUser("token-1")
        coEvery { apiService.getAccount("token-1") } returns
            DragonHubAccountResponse.Error(401, "token_unknown")

        assertEquals(AccountStatusResult.TokenUnknown, source().fetch())
    }

    @Test
    fun `hub errors and outages are unavailable`() = runTest {
        coEvery { database.getServerWithAddressAndUser("server-id") } returns serverWithUser("token-1")
        coEvery { apiService.getAccount("token-1") } returns
            DragonHubAccountResponse.Error(502, "jellyfin_unreachable")
        assertTrue(source().fetch() is AccountStatusResult.Unavailable)

        coEvery { apiService.getAccount("token-1") } returns DragonHubAccountResponse.Unreachable
        assertTrue(source().fetch() is AccountStatusResult.Unavailable)
    }

    @Test
    fun `disabled hub or missing login never calls the hub`() = runTest {
        assertTrue(source(isEnabled = false).fetch() is AccountStatusResult.Unavailable)

        coEvery { database.getServerWithAddressAndUser("server-id") } returns serverWithUser(null)
        assertTrue(source().fetch() is AccountStatusResult.Unavailable)

        coVerify(exactly = 0) { apiService.getAccount(any()) }
    }

    @Test
    fun `account card shows plan state and days left`() = runTest {
        val repository =
            DragonHubAccountDetailsRepository(
                fakeSource(
                    AccountStatusResult.Known(
                        dto(
                                disabledReason = null,
                                subscriptionEnd = "2026-11-01T00:00:00+00:00",
                                daysRemaining = 5,
                                expiringSoon = true,
                            )
                            .toAccountStatus()
                    )
                )
            )

        val details = (repository.getAccountDetails() as AccountDetailsResult.Success).details

        assertEquals("dragon", details.username)
        assertEquals("dragon@example.com", details.email)
        assertEquals("Monthly", details.planName)
        assertEquals(AccountState.ACTIVE, details.state)
        assertEquals(1_793_491_200L, details.expiryEpochSeconds)
        assertEquals(5, details.daysRemaining)
        assertTrue(details.expiringSoon)
    }

    @Test
    fun `account card states for expired, disabled and unmanaged users`() = runTest {
        suspend fun stateFor(dto: DragonHubAccountDto): AccountState? {
            val repository =
                DragonHubAccountDetailsRepository(
                    fakeSource(AccountStatusResult.Known(dto.toAccountStatus()))
                )
            return (repository.getAccountDetails() as AccountDetailsResult.Success).details.state
        }

        assertEquals(AccountState.EXPIRED, stateFor(dto(disabledReason = "expired")))
        assertEquals(AccountState.DISABLED, stateFor(dto(disabledReason = "admin")))
        assertNull(stateFor(dto(disabledReason = null).copy(managed = false)))
    }

    @Test
    fun `account card explains a dead or unknown login`() = runTest {
        val failure =
            DragonHubAccountDetailsRepository(fakeSource(AccountStatusResult.TokenUnknown))
                .getAccountDetails() as AccountDetailsResult.Failure

        assertFalse(failure.message.isBlank())
    }

    private fun source(isEnabled: Boolean = true) =
        DragonHubAccountStatusSource(
            apiService = apiService,
            appPreferences = AppPreferences(sharedPreferences),
            database = database,
            isEnabled = isEnabled,
        )

    private fun fakeSource(result: AccountStatusResult) =
        object : AccountStatusSource {
            override suspend fun fetch(): AccountStatusResult = result
        }

    private fun serverWithUser(accessToken: String?) =
        ServerWithAddressAndUser(
            server =
                Server(
                    id = "server-id",
                    name = "Server",
                    currentServerAddressId = null,
                    currentUserId = null,
                ),
            address = null,
            user =
                User(
                    id = UUID(0L, 1L),
                    name = "dragon",
                    serverId = "server-id",
                    accessToken = accessToken,
                ),
        )

    private fun dto(
        disabledReason: String? = null,
        subscriptionEnd: String? = "2026-11-01T00:00:00+00:00",
        daysRemaining: Int? = 5,
        expiringSoon: Boolean = false,
        message: String? = null,
        actionUrl: String? = null,
    ) =
        DragonHubAccountDto(
            session = "valid",
            managed = true,
            username = "dragon",
            email = "dragon@example.com",
            status = "active",
            plan = DragonHubPlanDto(name = "Monthly"),
            subscriptionEnd = subscriptionEnd,
            daysRemaining = daysRemaining,
            expiringSoon = expiringSoon,
            disabledReason = disabledReason,
            message = message,
            actionUrl = actionUrl,
        )
}
