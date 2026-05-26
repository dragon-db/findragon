package dev.jdtech.jellyfin.setup.presentation.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.models.ExceptionUiText
import dev.jdtech.jellyfin.models.ExceptionUiTexts
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.setup.domain.SetupRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@HiltViewModel
class WelcomeViewModel
@Inject
constructor(
    private val repository: SetupRepository,
    private val appPreferences: AppPreferences,
    private val defaultServerConfig: DefaultServerConfig,
) : ViewModel() {
    private val _state = MutableStateFlow(WelcomeState())
    val state = _state.asStateFlow()

    private val eventsChannel = Channel<WelcomeEvent>()
    val events = eventsChannel.receiveAsFlow()

    private fun continueSetup() {
        val defaultServerUrl = defaultServerConfig.serverUrl
        if (defaultServerUrl.isBlank()) {
            viewModelScope.launch { eventsChannel.send(WelcomeEvent.OpenManualServerSetup) }
            return
        }
        connectToDragonDb(defaultServerUrl)
    }

    private fun connectToDragonDb(defaultServerUrl: String) {
        viewModelScope.launch {
            _state.emit(
                _state.value.copy(
                    isLoading = true,
                    error = null,
                    showManualServerFallback = false,
                )
            )

            try {
                val server = repository.addServer(defaultServerUrl)
                appPreferences.setValue(appPreferences.currentServer, server.id)
                _state.emit(WelcomeState())
                eventsChannel.send(WelcomeEvent.ContinueSuccess)
            } catch (_: CancellationException) {
            } catch (e: ExceptionUiText) {
                _state.emit(
                    WelcomeState(
                        error = listOf(e.uiText),
                        showManualServerFallback = true,
                    )
                )
            } catch (e: ExceptionUiTexts) {
                _state.emit(
                    WelcomeState(
                        error = e.uiTexts,
                        showManualServerFallback = true,
                    )
                )
            } catch (e: Exception) {
                _state.emit(
                    WelcomeState(
                        error =
                            listOf(
                                if (e.message != null) UiText.DynamicString(e.message!!)
                                else UiText.StringResource(CoreR.string.unknown_error)
                            ),
                        showManualServerFallback = true,
                    )
                )
            }
        }
    }

    fun onAction(action: WelcomeAction) {
        when (action) {
            is WelcomeAction.OnContinueClick -> continueSetup()
            else -> Unit
        }
    }
}
