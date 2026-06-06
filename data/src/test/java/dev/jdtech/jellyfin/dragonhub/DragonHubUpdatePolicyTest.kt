package dev.jdtech.jellyfin.dragonhub

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DragonHubUpdatePolicyTest {
    @Test
    fun `newer version code shows update`() {
        assertTrue(
            DragonHubUpdatePolicy.isUpdateAvailable(
                latestVersionCode = 32006,
                installedVersionCode = 32005,
            )
        )
    }

    @Test
    fun `same version code does not show update`() {
        assertFalse(
            DragonHubUpdatePolicy.isUpdateAvailable(
                latestVersionCode = 32005,
                installedVersionCode = 32005,
            )
        )
    }

    @Test
    fun `older version code does not show update`() {
        assertFalse(
            DragonHubUpdatePolicy.isUpdateAvailable(
                latestVersionCode = 32004,
                installedVersionCode = 32005,
            )
        )
    }
}
