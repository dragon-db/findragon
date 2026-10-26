package dev.jdtech.jellyfin.findragon

import dev.jdtech.jellyfin.data.BuildConfig

/**
 * The only place app code reads Findragon infra values from.
 *
 * Values are baked in at build time from the git-ignored `findragon.properties` (see
 * `buildSrc/src/main/kotlin/FindragonBuildConfig.kt`). When the build runs with
 * `FINDRAGON_INFRA=false` every URL is empty and every integration below reports disabled.
 */
object FindragonConfig {
    val isInfraBuild: Boolean = BuildConfig.FINDRAGON_INFRA

    val defaultServerUrl: String = BuildConfig.DEFAULT_SERVER_URL
    val seerrBaseUrl: String = BuildConfig.JELLYSEERR_BASE_URL
    val jfaGoBaseUrl: String = BuildConfig.JFA_GO_BASE_URL
    val dragonHubUrl: String = BuildConfig.DRAGON_HUB_URL

    /** One-tap connect to the DragonDB Jellyfin server on the welcome screen. */
    val isDefaultServerEnabled: Boolean
        get() = defaultServerUrl.isNotBlank()

    /** Explore tab, media requests and issue reporting (Seerr / Jellyseerr). */
    val isSeerrEnabled: Boolean
        get() = seerrBaseUrl.isNotBlank()

    /** Account card in Settings (JFA-GO). */
    val isAccountDetailsEnabled: Boolean
        get() = jfaGoBaseUrl.isNotBlank()

    /** Update prompt and notifications on app start (Dragon Hub). */
    val isDragonHubEnabled: Boolean
        get() = dragonHubUrl.isNotBlank()
}
