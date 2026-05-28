package dev.jdtech.jellyfin.presentation.setup.welcome

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.setup.components.LoadingButton
import dev.jdtech.jellyfin.presentation.setup.components.RootLayout
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.setup.R as SetupR
import dev.jdtech.jellyfin.setup.presentation.welcome.WelcomeAction
import dev.jdtech.jellyfin.setup.presentation.welcome.WelcomeEvent
import dev.jdtech.jellyfin.setup.presentation.welcome.WelcomeState
import dev.jdtech.jellyfin.setup.presentation.welcome.WelcomeViewModel
import dev.jdtech.jellyfin.utils.ObserveAsEvents

@Composable
fun WelcomeScreen(
    navigateToUsers: () -> Unit,
    onAddServerClick: () -> Unit,
    viewModel: WelcomeViewModel = hiltViewModel(),
) {
    val uriHandler = LocalUriHandler.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is WelcomeEvent.ContinueSuccess -> navigateToUsers()
            is WelcomeEvent.OpenManualServerSetup -> onAddServerClick()
        }
    }

    WelcomeScreenLayout(
        state = state,
        onAddServerClick = onAddServerClick,
        onAction = { action ->
            when (action) {
                is WelcomeAction.OnLearnMoreClick -> {
                    uriHandler.openUri("https://jellyfin.org/")
                }
                else -> Unit
            }
            viewModel.onAction(action)
        }
    )
}

@Composable
private fun WelcomeScreenLayout(
    state: WelcomeState,
    onAddServerClick: () -> Unit,
    onAction: (WelcomeAction) -> Unit,
) {
    val context = LocalContext.current
    val error = state.error

    RootLayout(padding = PaddingValues(horizontal = 24.dp)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.Center).verticalScroll(rememberScrollState()),
        ) {
            Image(
                painter = painterResource(id = CoreR.drawable.ic_banner),
                contentDescription = null,
                modifier = Modifier.width(250.dp),
            )
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = stringResource(SetupR.string.welcome),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(SetupR.string.welcome_text),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            if (error != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = error.joinToString { it.asString(context.resources) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
            Column(modifier = Modifier.widthIn(max = 480.dp)) {
                OutlinedButton(
                    onClick = { onAction(WelcomeAction.OnLearnMoreClick) },
                    enabled = !state.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(SetupR.string.welcome_btn_learn_more))
                }
                Spacer(modifier = Modifier.height(4.dp))
                if (state.showManualServerFallback) {
                    OutlinedButton(
                        onClick = onAddServerClick,
                        enabled = !state.isLoading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(SetupR.string.welcome_btn_add_server_manually))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                LoadingButton(
                    text = stringResource(SetupR.string.welcome_btn_continue),
                    onClick = { onAction(WelcomeAction.OnContinueClick) },
                    isLoading = state.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@PreviewScreenSizes
@Composable
private fun WelcomeScreenLayoutPreview() {
    FindroidTheme {
        WelcomeScreenLayout(state = WelcomeState(), onAddServerClick = {}, onAction = {})
    }
}
