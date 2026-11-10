package com.app.smishing.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.app.smishing.R
import com.app.smishing.domain.messaging.Conversation
import com.app.smishing.domain.messaging.formatConversationDate
import com.app.smishing.ui.designsystem.components.ConversationRow
import com.app.smishing.ui.designsystem.components.SmishingExtendedFab
import com.app.smishing.ui.designsystem.components.SmishingTopBar
import com.app.smishing.ui.designsystem.components.TopBarStyle
import com.app.smishing.ui.designsystem.theme.SmishingPalette
import com.app.smishing.ui.designsystem.theme.SmishingTypography
import com.app.smishing.ui.designsystem.theme.Spacing
import java.time.ZoneId
import java.util.Locale

/** Los textos del diseño están en español: las fechas, también. */
private val SpanishLocale = Locale.forLanguageTag("es")

/**
 * La lista de mensajes (#5): una fila por conversación, con el remitente, la fecha y el inicio del último mensaje.
 * [conversations] es `null` mientras se leen del buzón.
 *
 * El botón "Escribir" abre una conversación nueva (#6). Las conversaciones con mensajes sin leer se distinguen con negrita y un
 * contador (#50). Todavía no tiene la búsqueda ni la advertencia de estafa en la lista.
 */
@Composable
fun ConversationListScreen(
    conversations: List<Conversation>?,
    nowMillis: Long,
    onOpen: (Conversation) -> Unit,
    onCompose: () -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val yesterday = stringResource(R.string.messages_yesterday)
    Box(modifier = modifier.fillMaxSize().background(SmishingPalette.Background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            SmishingTopBar(title = stringResource(R.string.messages_title), style = TopBarStyle.Plain)
            when {
                conversations == null -> Unit
                conversations.isEmpty() -> Box(Modifier.fillMaxSize().padding(Spacing.Md), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.messages_empty),
                        style = SmishingTypography.BodyMedium,
                        color = SmishingPalette.TextSecondary,
                    )
                }
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(conversations, key = { it.threadId }) { conversation ->
                        ConversationRow(
                            sender = conversation.address,
                            time = formatConversationDate(conversation.lastMessageAtMillis, nowMillis, zone, yesterday, SpanishLocale),
                            snippet = conversation.snippet,
                            onClick = { onOpen(conversation) },
                            unreadCount = conversation.unreadCount,
                            unreadDescription = stringResource(R.string.messages_unread_description, conversation.unreadCount),
                        )
                    }
                }
            }
        }
        SmishingExtendedFab(
            text = stringResource(R.string.compose_action),
            onClick = onCompose,
            modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.Md),
        )
    }
}
