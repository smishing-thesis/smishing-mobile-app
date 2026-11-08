package com.app.smishing.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.app.smishing.R
import com.app.smishing.domain.messaging.ConversationMessage
import com.app.smishing.domain.messaging.MessageDirection
import com.app.smishing.domain.messaging.SendOutcome
import com.app.smishing.ui.designsystem.components.MessageBubble
import com.app.smishing.ui.designsystem.components.MessageInputBar
import com.app.smishing.ui.designsystem.components.SmishingTopBar
import com.app.smishing.ui.designsystem.components.TopBarStyle
import com.app.smishing.ui.designsystem.theme.SmishingPalette
import com.app.smishing.ui.designsystem.theme.SmishingTypography
import com.app.smishing.ui.designsystem.theme.Spacing
import com.app.smishing.ui.designsystem.theme.Status

/**
 * Una conversación (#5 y #6): el remitente arriba, los mensajes recibidos (a la izquierda) y enviados (a la derecha), del más
 * antiguo al más reciente, y abajo la barra para escribir y enviar. Abre en el último mensaje. [messages] es `null`
 * mientras se leen del buzón.
 *
 * Un mensaje enviado que no salió muestra "No se pudo enviar". [error] es el motivo por el que el último intento no se envió.
 * Todavía no tiene el menú ni el aviso de riesgo (#10).
 */
@Composable
fun ConversationScreen(
    address: String,
    messages: List<ConversationMessage>?,
    onBack: () -> Unit,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    error: SendOutcome?,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val count = messages?.size ?: 0
    // Abre en el mensaje más reciente, y sigue ahí cuando llega uno nuevo.
    LaunchedEffect(count) { if (count > 0) listState.scrollToItem(count - 1) }

    val received = stringResource(R.string.message_received)
    val sent = stringResource(R.string.message_sent)
    Column(modifier = modifier.fillMaxSize().background(SmishingPalette.Background).imePadding()) {
        SmishingTopBar(
            title = address,
            subtitle = stringResource(R.string.conversation_subtitle),
            style = TopBarStyle.Plain,
            onBack = onBack,
            backDescription = stringResource(R.string.conversation_back),
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(Spacing.Md),
            verticalArrangement = Arrangement.spacedBy(Spacing.Sm),
        ) {
            items(messages.orEmpty(), key = { it.id }) { message ->
                val outgoing = message.direction == MessageDirection.Outgoing
                Column(horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start) {
                    MessageBubble(
                        text = message.body,
                        outgoing = outgoing,
                        // El lector de pantalla dice si el mensaje es recibido o enviado, que el color no transmite.
                        modifier = Modifier.semantics(mergeDescendants = true) { stateDescription = if (outgoing) sent else received },
                    )
                    if (message.failed) {
                        Text(
                            stringResource(R.string.message_failed),
                            style = SmishingTypography.NoteBold,
                            color = SmishingPalette.statusColors(Status.Danger).onContainer,
                            modifier = Modifier.padding(top = Spacing.X2s, end = Spacing.Xs),
                        )
                    }
                }
            }
        }
        SendErrorText(error)
        HorizontalDivider(color = SmishingPalette.Border)
        MessageInputBar(
            value = draft,
            onValueChange = onDraftChange,
            onSend = { if (draft.isNotBlank()) onSend() },
            placeholder = stringResource(R.string.conversation_input_hint),
            sendDescription = stringResource(R.string.conversation_send),
        )
    }
}
