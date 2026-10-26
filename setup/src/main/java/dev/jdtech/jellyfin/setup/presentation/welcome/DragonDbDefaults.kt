package dev.jdtech.jellyfin.setup.presentation.welcome

import dev.jdtech.jellyfin.findragon.FindragonConfig

interface DefaultServerConfig {
    val serverUrl: String
}

class BuildConfigDefaultServerConfig : DefaultServerConfig {
    override val serverUrl: String
        get() = FindragonConfig.defaultServerUrl
}
