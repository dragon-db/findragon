package dev.jdtech.jellyfin.presentation.setup.welcome

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.core.R
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
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
    val state by viewModel.state.collectAsState()

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
    val focusRequester = remember { FocusRequester() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.Center).widthIn(max = 720.dp),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_banner),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.width(250.dp),
            )
            Spacer(modifier = Modifier.height(MaterialTheme.spacings.medium))
            Text(
                text = stringResource(dev.jdtech.jellyfin.setup.R.string.welcome),
                style = MaterialTheme.typography.displayMedium,
            )
            Spacer(modifier = Modifier.height(MaterialTheme.spacings.default))
            Text(
                text = stringResource(dev.jdtech.jellyfin.setup.R.string.welcome_text),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            if (error != null) {
                Spacer(modifier = Modifier.height(MaterialTheme.spacings.medium))
                Text(
                    text = error.joinToString { it.asString(context.resources) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.height(MaterialTheme.spacings.large))
            OutlinedButton(
                onClick = { onAction(WelcomeAction.OnLearnMoreClick) },
                enabled = !state.isLoading,
            ) {
                Text(
                    text = stringResource(dev.jdtech.jellyfin.setup.R.string.welcome_btn_learn_more)
                )
            }
            if (state.showManualServerFallback) {
                Spacer(modifier = Modifier.height(MaterialTheme.spacings.medium))
                OutlinedButton(onClick = onAddServerClick, enabled = !state.isLoading) {
                    Text(
                        text =
                            stringResource(
                                dev.jdtech.jellyfin.setup.R.string.welcome_btn_add_server_manually
                            )
                    )
                }
            }
            Spacer(modifier = Modifier.height(MaterialTheme.spacings.medium))
            Button(
                onClick = { onAction(WelcomeAction.OnContinueClick) },
                enabled = !state.isLoading,
                modifier = Modifier.focusRequester(focusRequester),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            color = LocalContentColor.current,
                            modifier =
                                Modifier.size(24.dp)
                                    .align(Alignment.CenterStart)
                                    .offset(x = (-96).dp),
                        )
                    }
                    Text(
                        text = stringResource(dev.jdtech.jellyfin.setup.R.string.welcome_btn_continue)
                    )
                }
            }
        }
    }

    LaunchedEffect(true) { focusRequester.requestFocus() }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun WelcomeScreenLayoutPreview() {
    FindroidTheme {
        WelcomeScreenLayout(state = WelcomeState(), onAddServerClick = {}, onAction = {})
    }
}
