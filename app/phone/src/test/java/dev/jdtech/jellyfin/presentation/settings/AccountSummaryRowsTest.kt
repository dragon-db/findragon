package dev.jdtech.jellyfin.presentation.settings

import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.settings.R as SettingsR
import dev.jdtech.jellyfin.settings.domain.models.AccountDetails
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
                indexes = intArrayOf(CoreR.string.title_settings),
            )
        )
    }

    @Test
    fun `configured account details show only at settings root`() {
        assertTrue(
            shouldShowAccountDetails(
                isConfigured = true,
                indexes = intArrayOf(CoreR.string.title_settings),
            )
        )
        assertFalse(
            shouldShowAccountDetails(
                isConfigured = true,
                indexes = intArrayOf(CoreR.string.title_settings, SettingsR.string.account),
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
}
