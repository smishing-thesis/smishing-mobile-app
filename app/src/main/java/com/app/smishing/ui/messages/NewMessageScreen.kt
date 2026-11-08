package com.app.smishing.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.app.smishing.R
import com.app.smishing.domain.messaging.SendOutcome
import com.app.smishing.ui.designsystem.components.PrimaryButton
import com.app.smishing.ui.designsystem.components.SmishingTextField
import com.app.smishing.ui.designsystem.components.SmishingTopBar
import com.app.smishing.ui.designsystem.components.TopBarStyle
import com.app.smishing.ui.designsystem.theme.SmishingPalette
import com.app.smishing.ui.designsystem.theme.SmishingTypography
import com.app.smishing.ui.designsystem.theme.Spacing

/**
 * "Nuevo mensaje" (#6): el destinatario, el texto y el botón de enviar, que no se activa hasta tener las dos cosas.
 * [error] es el motivo por el que el último intento no se envió.
 *
 * El diseño permite elegir un contacto con "+" y escribir su nombre; eso exige `READ_CONTACTS`, que esta historia no
 * justifica: hoy solo se escribe el número.
 */
@Composable
fun NewMessageScreen(
    recipient: String,
    onRecipientChange: (String) -> Unit,
    body: String,
    onBodyChange: (String) -> Unit,
    onSend: () -> Unit,
    onBack: () -> Unit,
    error: SendOutcome?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().background(SmishingPalette.Background).imePadding()) {
        SmishingTopBar(
            title = stringResource(R.string.new_message_title),
            style = TopBarStyle.Primary,
            onBack = onBack,
            backDescription = stringResource(R.string.conversation_back),
        )
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.Md),
            verticalArrangement = Arrangement.spacedBy(Spacing.Md),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.Xs)) {
                Text(stringResource(R.string.new_message_to), style = SmishingTypography.BodyMediumBold, color = SmishingPalette.TextPrimary)
                SmishingTextField(
                    value = recipient,
                    onValueChange = onRecipientChange,
                    placeholder = stringResource(R.string.new_message_recipient_hint),
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
            }
            SmishingTextField(
                value = body,
                onValueChange = onBodyChange,
                placeholder = stringResource(R.string.new_message_body_hint),
                modifier = Modifier.fillMaxWidth(),
                minLines = 6,
            )
            SendErrorText(error)
            PrimaryButton(
                text = stringResource(R.string.new_message_send),
                onClick = onSend,
                modifier = Modifier.fillMaxWidth(),
                enabled = recipient.isNotBlank() && body.isNotBlank(),
            )
        }
    }
}
