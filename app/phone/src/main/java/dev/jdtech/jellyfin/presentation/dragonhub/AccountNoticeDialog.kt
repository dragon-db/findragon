package dev.jdtech.jellyfin.presentation.dragonhub

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.jdtech.jellyfin.R
import dev.jdtech.jellyfin.dragonhub.account.AccountBlock
import dev.jdtech.jellyfin.dragonhub.account.AccountStatus
import java.text.DateFormat
import java.util.Date

/**
 * Explains an expired or disabled DragonHub account: Jellyfin only shows an empty library (expired)
 * or refuses the sign-in (disabled) without saying why.
 */
@Composable
fun AccountNoticeDialog(status: AccountStatus, onDismiss: () -> Unit) {
    val block = status.block ?: return
    val context = LocalContext.current
    val actionUrl = status.actionUrl
    val actionLabel = status.actionLabel

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(block.titleRes())) },
        // One short sentence. The hub's own `message` is left out: it repeats the same thing.
        text = { Text(text = accountNoticeText(status, block)) },
        confirmButton = {
            if (actionUrl != null && actionLabel != null) {
                TextButton(
                    onClick = {
                        context.openHttpUrl(actionUrl)
                        onDismiss()
                    }
                ) {
                    Text(text = actionLabel)
                }
            }
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.account_notice_ok))
            }
        },
    )
}

@Composable
private fun accountNoticeText(status: AccountStatus, block: AccountBlock): String =
    when (block) {
        AccountBlock.EXPIRED -> {
            val end = status.subscriptionEndEpochSeconds
            if (end != null) {
                val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(end * 1000L))
                stringResource(R.string.account_notice_expired_on, date)
            } else {
                stringResource(R.string.account_notice_expired)
            }
        }
        AccountBlock.DISABLED_BY_ADMIN -> stringResource(R.string.account_notice_disabled_by_admin)
        AccountBlock.DISABLED_ON_SERVER -> stringResource(R.string.account_notice_disabled_on_server)
    }

private fun AccountBlock.titleRes(): Int =
    when (this) {
        AccountBlock.EXPIRED -> R.string.account_notice_expired_title
        AccountBlock.DISABLED_BY_ADMIN,
        AccountBlock.DISABLED_ON_SERVER -> R.string.account_notice_disabled_title
    }
