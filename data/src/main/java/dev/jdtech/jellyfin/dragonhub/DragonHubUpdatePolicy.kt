package dev.jdtech.jellyfin.dragonhub

import dev.jdtech.jellyfin.data.BuildConfig

object DragonHubUpdatePolicy {
    fun isUpdateAvailable(
        latestVersionCode: Int,
        installedVersionCode: Int = BuildConfig.VERSION_CODE,
    ): Boolean {
        return latestVersionCode > installedVersionCode
    }
}
