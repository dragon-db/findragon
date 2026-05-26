package dev.jdtech.jellyfin.setup.presentation.welcome

import dev.jdtech.jellyfin.setup.BuildConfig

interface DefaultServerConfig {
    val serverUrl: String
}

class BuildConfigDefaultServerConfig : DefaultServerConfig {
    override val serverUrl: String
        get() = BuildConfig.DEFAULT_SERVER_URL
}
