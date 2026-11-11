package dev.jdtech.jellyfin.presentation.settings

import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.settings.R as SettingsR
import dev.jdtech.jellyfin.settings.domain.models.AccountDetails
import dev.jdtech.jellyfin.settings.domain.models.AccountState
import java.text.SimpleDateFormat
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountSummaryRowsTest {
    @Test
    fun `account details are hidden when service is not configured`() {
        assertFalse(
            shouldShowAccountDetails(
                isConfigured = false,
                indexes = listOf(CoreR.string.title_settings),
            )
        )
    }

    @Test
    fun `configured account details show only at settings root`() {
        assertTrue(
            shouldShowAccountDetails(
                isConfigured = true,
                indexes = listOf(CoreR.string.title_settings),
            )
        )
        assertFalse(
            shouldShowAccountDetails(
                isConfigured = true,
                indexes = listOf(CoreR.string.title_settings, SettingsR.string.account),
            )
        )
    }

    @Test
    fun `account summary rows contain username email and expiry`() {
        val rows =
            accountSummaryRows(
                accountDetails =
                    AccountDetails(
                        username = "dragon",
                        email = "dragon@example.com",
                        expiryEpochSeconds = 1_748_908_800,
                    ),
                notSet = "Not set",
                noExpiry = "No expiry",
                dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US),
            )

        assertEquals(
            listOf(
                AccountSummaryRow(SettingsR.string.account_username, "dragon"),
                AccountSummaryRow(SettingsR.string.account_email, "dragon@example.com"),
                AccountSummaryRow(SettingsR.string.account_expiry, "2025-06-03"),
            ),
            rows,
        )
    }

    @Test
    fun `account summary rows show fallbacks for missing email and no expiry`() {
        val rows =
            accountSummaryRows(
                accountDetails =
                    AccountDetails(
                        username = "dragon",
                        email = null,
                        expiryEpochSeconds = 0,
                    ),
                notSet = "Not set",
                noExpiry = "No expiry",
                dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US),
            )

        assertEquals("Not set", rows[1].value)
        assertEquals("No expiry", rows[2].value)
    }

    @Test
    fun `hub account rows add plan, status and days left`() {
        val rows =
            accountSummaryRows(
                accountDetails =
                    AccountDetails(
                        username = "dragon",
                        email = null,
                        expiryEpochSeconds = 1_748_908_800,
                        planName = "Monthly",
                        state = AccountState.ACTIVE,
                        daysRemaining = 5,
                        expiringSoon = true,
                    ),
                notSet = "Not set",
                noExpiry = "No expiry",
                dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US),
            )

        assertEquals(
            listOf(
                AccountSummaryRow(SettingsR.string.account_username, "dragon"),
                AccountSummaryRow(SettingsR.string.account_email, "Not set"),
                AccountSummaryRow(SettingsR.string.account_plan, "Monthly"),
                AccountSummaryRow(SettingsR.string.account_status, "Active"),
                AccountSummaryRow(
                    SettingsR.string.account_expiry,
                    "2025-06-03 (5 days left)",
                    isWarning = true,
                ),
            ),
            rows,
        )
    }

    @Test
    fun `expired account status is a warning without days left`() {
        val rows =
            accountSummaryRows(
                accountDetails =
                    AccountDetails(
                        username = "dragon",
                        email = null,
                        expiryEpochSeconds = 1_748_908_800,
                        state = AccountState.EXPIRED,
                        daysRemaining = 0,
                    ),
                notSet = "Not set",
                noExpiry = "No expiry",
                dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US),
            )

        assertEquals(AccountSummaryRow(SettingsR.string.account_status, "Expired", true), rows[2])
        assertEquals(AccountSummaryRow(SettingsR.string.account_expiry, "2025-06-03"), rows[3])
    }
}
