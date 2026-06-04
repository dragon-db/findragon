package dev.jdtech.jellyfin.presentation.setup.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR

@Composable
fun SetupBrandHeader(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Image(
            painter = painterResource(CoreR.drawable.ic_logo),
            contentDescription = stringResource(CoreR.string.app_name),
            modifier = Modifier.width(78.dp),
        )
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = stringResource(CoreR.string.app_name),
                style =
                    MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Light,
                    ),
                maxLines = 1,
                textAlign = TextAlign.End,
            )
            Text(
                text = "by Dragon DB",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                textAlign = TextAlign.End,
            )
        }
    }
}
