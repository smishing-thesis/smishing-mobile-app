package com.app.smishing.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.app.smishing.R
import com.app.smishing.domain.messaging.ConversationMessage
import com.app.smishing.domain.messaging.MessageDirection
import com.app.smishing.ui.designsystem.components.MessageBubble
import com.app.smishing.ui.designsystem.components.SmishingTopBar
import com.app.smishing.ui.designsystem.components.TopBarStyle
import com.app.smishing.ui.designsystem.theme.SmishingPalette
import com.app.smishing.ui.designsystem.theme.Spacing

/**
 * Una conversación (#5): el remitente arriba y los mensajes recibidos (a la izquierda) y enviados (a la derecha), del más
 * antiguo al más reciente. Abre en el último mensaje. [messages] es `null` mientras se leen del buzón.
 *
 * Todavía no tiene la barra para escribir (#6), el menú ni el aviso de riesgo (#10).
 */
@Composable
fun ConversationScreen(
    address: String,
    messages: List<ConversationMessage>?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val count = messages?.size ?: 0
    // Abre en el mensaje más reciente, y sigue ahí cuando llega uno nuevo.
    LaunchedEffect(count) { if (count > 0) listState.scrollToItem(count - 1) }

    val received = stringResource(R.string.message_received)
    val sent = stringResource(R.string.message_sent)
    Column(modifier = modifier.fillMaxSize().background(SmishingPalette.Background)) {
        SmishingTopBar(
            title = address,
            subtitle = stringResource(R.string.conversation_subtitle),
            style = TopBarStyle.Plain,
            onBack = onBack,
            backDescription = stringResource(R.string.conversation_back),
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(Spacing.Md),
            verticalArrangement = Arrangement.spacedBy(Spacing.Sm),
        ) {
            items(messages.orEmpty(), key = { it.id }) { message ->
                val outgoing = message.direction == MessageDirection.Outgoing
                MessageBubble(
                    text = message.body,
                    outgoing = outgoing,
                    // El lector de pantalla dice si el mensaje es recibido o enviado, que el color no transmite.
                    modifier = Modifier.semantics(mergeDescendants = true) { stateDescription = if (outgoing) sent else received },
                )
            }
        }
    }
}
