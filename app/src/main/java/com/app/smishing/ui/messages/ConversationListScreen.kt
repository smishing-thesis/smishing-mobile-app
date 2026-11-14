package com.app.smishing.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.app.smishing.R
import com.app.smishing.domain.history.MessageRisk
import com.app.smishing.domain.history.riskNotice
import com.app.smishing.domain.messaging.Conversation
import com.app.smishing.domain.risk.RiskLevel
import com.app.smishing.ui.designsystem.theme.Status
import com.app.smishing.domain.messaging.formatConversationDate
import com.app.smishing.ui.designsystem.components.ConversationRow
import com.app.smishing.ui.designsystem.components.SmishingExtendedFab
import com.app.smishing.ui.designsystem.components.SelectAction
import com.app.smishing.ui.designsystem.components.SelectionTopBar
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
    /** El riesgo guardado de cada mensaje (#60): la fila avisa si su último mensaje es de riesgo alto o medio. */
    risks: Map<Long, MessageRisk> = emptyMap(),
    /** El desplazamiento de la lista. Lo guarda quien la usa, para volver al mismo lugar al regresar de una conversación. */
    listState: LazyListState = rememberLazyListState(),
    /** Si se están eligiendo conversaciones para borrar (#51) y cuáles (por su hilo). Tocar una fila la marca o desmarca. */
    selecting: Boolean = false,
    selected: Set<Long> = emptySet(),
    onToggle: (Conversation) -> Unit = {},
    /** Empezar a elegir: desde el botón "Seleccionar" (sin conversación) o manteniendo presionada una (esa conversación). */
    onStartSelecting: (Conversation?) -> Unit = {},
    onSelectAll: () -> Unit = {},
    onCancelSelection: () -> Unit = {},
    onDeleteSelected: () -> Unit = {},
    /** Cómo se muestra un remitente: el nombre del contacto o, si no hay, el número (#53). */
    displayName: (String) -> String = { it },
) {
    val yesterday = stringResource(R.string.messages_yesterday)
    Box(modifier = modifier.fillMaxSize().background(SmishingPalette.Background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (selecting) {
                SelectionTopBar(
                    count = selected.size,
                    style = TopBarStyle.Plain,
                    onCancel = onCancelSelection,
                    onSelectAll = onSelectAll,
                    onDelete = onDeleteSelected,
                )
            } else {
                SmishingTopBar(
                    title = stringResource(R.string.messages_title),
                    style = TopBarStyle.Plain,
                    actions = { if (!conversations.isNullOrEmpty()) SelectAction(TopBarStyle.Plain) { onStartSelecting(null) } },
                )
            }
            when {
                conversations == null -> Unit
                conversations.isEmpty() -> Box(Modifier.fillMaxSize().padding(Spacing.Md), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.messages_empty),
                        style = SmishingTypography.BodyMedium,
                        color = SmishingPalette.TextSecondary,
                    )
                }
                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(conversations, key = { it.threadId }) { conversation ->
                        val notice = riskNotice(risks, conversation.lastMessageId, conversation.snippet)
                        ConversationRow(
                            sender = displayName(conversation.address),
                            time = formatConversationDate(conversation.lastMessageAtMillis, nowMillis, zone, yesterday, SpanishLocale),
                            snippet = conversation.snippet,
                            onClick = { if (selecting) onToggle(conversation) else onOpen(conversation) },
                            onLongClick = { if (!selecting) onStartSelecting(conversation) },
                            onLongClickLabel = stringResource(R.string.history_select),
                            selectionMode = selecting,
                            selected = conversation.threadId in selected,
                            warning = when (notice) {
                                RiskLevel.High -> stringResource(R.string.messages_warning_high)
                                RiskLevel.Medium -> stringResource(R.string.messages_warning_medium)
                                else -> null
                            },
                            warningStatus = if (notice == RiskLevel.High) Status.Danger else Status.Suspicious,
                            unreadCount = conversation.unreadCount,
                            unreadDescription = stringResource(R.string.messages_unread_description, conversation.unreadCount),
                        )
                    }
                }
            }
        }
        if (!selecting) {
            SmishingExtendedFab(
                text = stringResource(R.string.compose_action),
                onClick = onCompose,
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.Md),
            )
        }
    }
}
