package com.app.smishing.ui.messages

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.app.smishing.R
import com.app.smishing.domain.messaging.SendOutcome
import com.app.smishing.ui.designsystem.theme.SmishingPalette
import com.app.smishing.ui.designsystem.theme.SmishingTypography
import com.app.smishing.ui.designsystem.theme.Spacing
import com.app.smishing.ui.designsystem.theme.Status

/** Lo que salió mal al enviar, en palabras para la persona. `Sent` no es un error. */
fun SendOutcome.errorTextId(): Int? = when (this) {
    is SendOutcome.Sent -> null
    SendOutcome.InvalidRecipient -> R.string.send_error_invalid_recipient
    SendOutcome.EmptyMessage -> null // el botón de enviar no se activa sin texto
    SendOutcome.NotAllowed -> R.string.send_error_not_allowed
    SendOutcome.Failed -> R.string.send_error_failed
}

@Composable
fun SendErrorText(error: SendOutcome?, modifier: Modifier = Modifier) {
    val id = error?.errorTextId() ?: return
    Text(
        stringResource(id),
        style = SmishingTypography.BodyMedium,
        color = SmishingPalette.statusColors(Status.Danger).onContainer,
        modifier = modifier.padding(horizontal = Spacing.Md, vertical = Spacing.Xs),
    )
}
