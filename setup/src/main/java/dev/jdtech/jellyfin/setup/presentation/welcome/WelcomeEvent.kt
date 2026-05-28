package dev.jdtech.jellyfin.setup.presentation.welcome

sealed interface WelcomeEvent {
    data object ContinueSuccess : WelcomeEvent

    data object OpenManualServerSetup : WelcomeEvent
}
