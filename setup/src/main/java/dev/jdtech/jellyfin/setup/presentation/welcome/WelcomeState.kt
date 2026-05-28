package dev.jdtech.jellyfin.setup.presentation.welcome

import dev.jdtech.jellyfin.models.UiText

data class WelcomeState(
    val isLoading: Boolean = false,
    val error: Collection<UiText>? = null,
    val showManualServerFallback: Boolean = false,
)
