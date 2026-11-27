package com.app.smishing.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.app.smishing.R
import com.app.smishing.domain.history.MessageRisk
import com.app.smishing.domain.history.isMarkedSafe
import com.app.smishing.domain.history.riskNotice
import com.app.smishing.domain.messaging.ConversationMessage
import com.app.smishing.domain.risk.RiskLevel
import com.app.smishing.ui.designsystem.components.RiskNotice
import com.app.smishing.ui.designsystem.components.SelectAction
import com.app.smishing.ui.designsystem.components.SelectionTopBar
import com.app.smishing.domain.messaging.MessageDirection
import com.app.smishing.domain.messaging.SendOutcome
import com.app.smishing.ui.designsystem.components.LinkButton
import com.app.smishing.ui.designsystem.components.MessageBubble
import com.app.smishing.ui.designsystem.components.MessageInputBar
import com.app.smishing.ui.designsystem.components.SmishingTopBar
import com.app.smishing.ui.designsystem.components.TopBarStyle
import com.app.smishing.ui.designsystem.theme.SmishingPalette
import com.app.smishing.ui.designsystem.theme.SmishingTypography
import com.app.smishing.ui.designsystem.theme.Spacing
import com.app.smishing.ui.designsystem.theme.Status

/** Qué dice la fila de análisis de un mensaje recibido (#59). */
enum class StoredAnalysis {
    /** Nada: ya tiene análisis (y su aviso, si lo merece) o el modelo no lo evalúa. */
    None,

    /** "No analizado" con "Analizar". */
    CanAnalyze,

    /** "Analizando…". */
    Analyzing,

    /** "No se pudo analizar" con "Analizar" otra vez. */
    Failed,

    /** Analizado y sin señales (riesgo bajo): el aviso verde "Sin señales de riesgo" con "Ver análisis". */
    NoRisk,
}

/**
 * Una conversación (#5 y #6): el remitente arriba, los mensajes recibidos (a la izquierda) y enviados (a la derecha), del más
 * antiguo al más reciente, y abajo la barra para escribir y enviar. Abre en el último mensaje. [messages] es `null`
 * mientras se leen del buzón.
 *
 * Un mensaje enviado que no salió muestra "No se pudo enviar". [error] es el motivo por el que el último intento no se envió.
 * Un mensaje recibido de riesgo alto o medio lleva arriba un aviso con "Ver riesgo" (#60); uno sin análisis dice "No analizado" con
 * "Analizar" (#59). Todavía no tiene el menú.
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
    /** El riesgo guardado de cada mensaje (#60): un mensaje de riesgo alto o medio lleva un aviso con "Ver riesgo". */
    risks: Map<Long, MessageRisk> = emptyMap(),
    /** Abre el detalle del análisis del registro con ese id. */
    onSeeRisk: (recordId: Long) -> Unit = {},
    /** Qué dice la fila de análisis de un mensaje recibido (#59). */
    analysis: (ConversationMessage) -> StoredAnalysis = { StoredAnalysis.None },
    onAnalyze: (ConversationMessage) -> Unit = {},
    /** Si se están eligiendo mensajes para borrar (#51) y cuáles (por su id). Tocar una burbuja la marca o desmarca. */
    selecting: Boolean = false,
    selected: Set<Long> = emptySet(),
    onToggle: (ConversationMessage) -> Unit = {},
    /** Empezar a elegir: desde el botón "Seleccionar" (sin mensaje) o manteniendo presionado uno (ese mensaje). */
    onStartSelecting: (ConversationMessage?) -> Unit = {},
    onSelectAll: () -> Unit = {},
    onCancelSelection: () -> Unit = {},
    onDeleteSelected: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    val count = messages?.size ?: 0
    // Abre en el mensaje más reciente, y sigue ahí cuando llega uno nuevo.
    LaunchedEffect(count) { if (count > 0) listState.scrollToItem(count - 1) }

    val received = stringResource(R.string.message_received)
    val sent = stringResource(R.string.message_sent)
    Column(modifier = modifier.fillMaxSize().background(SmishingPalette.Background).navigationBarsPadding().imePadding()) {
        if (selecting) {
            SelectionTopBar(
                count = selected.size,
                allSelected = !messages.isNullOrEmpty() && selected.size >= messages.size,
                style = TopBarStyle.Primary,
                onCancel = onCancelSelection,
                onSelectAll = onSelectAll,
                onDelete = onDeleteSelected,
            )
        } else {
            SmishingTopBar(
                title = address,
                subtitle = stringResource(R.string.conversation_subtitle),
                style = TopBarStyle.Primary,
                onBack = onBack,
                backDescription = stringResource(R.string.conversation_back),
                actions = { if (count > 0) SelectAction(TopBarStyle.Primary) { onStartSelecting(null) } },
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(Spacing.Md),
            verticalArrangement = Arrangement.spacedBy(Spacing.Sm),
        ) {
            items(messages.orEmpty(), key = { it.id }) { message ->
                val outgoing = message.direction == MessageDirection.Outgoing
                // Al seleccionar, toda la fila (la casilla y el espacio de alrededor, no solo la burbuja) marca y desmarca el mensaje.
                Row(
                    modifier = if (selecting) Modifier.fillMaxWidth().clickable { onToggle(message) } else Modifier,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                if (selecting) {
                    Checkbox(
                        checked = message.id in selected,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(checkedColor = SmishingPalette.Primary),
                        modifier = Modifier.padding(end = Spacing.Sm),
                    )
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start) {
                    val level = if (outgoing) null else riskNotice(risks, message.id, message.body)
                    if (level != null) {
                        RiskNotice(
                            status = if (level == RiskLevel.High) Status.Danger else Status.Suspicious,
                            title = stringResource(if (level == RiskLevel.High) R.string.messages_notice_high else R.string.messages_notice_medium),
                            actionLabel = stringResource(R.string.messages_notice_action),
                            onAction = { risks[message.id]?.let { onSeeRisk(it.recordId) } },
                            modifier = Modifier.padding(bottom = Spacing.Xs),
                        )
                    }
                    if (!outgoing && isMarkedSafe(risks, message.id, message.body)) {
                        // El mismo aviso, en verde y con la marca de manual: es lo que dijo la persona, no un veredicto de la app (#102).
                        RiskNotice(
                            status = Status.Safe,
                            title = stringResource(R.string.messages_notice_safe),
                            actionLabel = stringResource(R.string.messages_notice_safe_action),
                            onAction = { risks[message.id]?.let { onSeeRisk(it.recordId) } },
                            modifier = Modifier.padding(bottom = Spacing.Xs),
                        )
                    }
                    val state = if (outgoing) StoredAnalysis.None else analysis(message)
                    val noRiskRecord = if (state == StoredAnalysis.NoRisk) risks[message.id] else null
                    if (noRiskRecord != null) {
                        // El mismo aviso que los de riesgo, en verde: no hay señales, que no es lo mismo que "es legítimo".
                        RiskNotice(
                            status = Status.Safe,
                            title = stringResource(R.string.messages_analyzed_no_risk),
                            actionLabel = stringResource(R.string.messages_see_analysis),
                            onAction = { onSeeRisk(noRiskRecord.recordId) },
                            modifier = Modifier.padding(bottom = Spacing.Xs),
                        )
                    }
                    MessageBubble(
                        text = message.body,
                        outgoing = outgoing,
                        onClick = if (selecting) ({ onToggle(message) }) else null,
                        onLongClick = if (selecting) null else ({ onStartSelecting(message) }),
                        onLongClickLabel = stringResource(R.string.history_select),
                        // El lector de pantalla dice si el mensaje es recibido o enviado, que el color no transmite.
                        stateText = if (outgoing) sent else received,
                    )
                    if (state == StoredAnalysis.CanAnalyze || state == StoredAnalysis.Analyzing || state == StoredAnalysis.Failed) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
                            modifier = Modifier.padding(top = Spacing.X2s),
                        ) {
                            val failed = state == StoredAnalysis.Failed
                            Text(
                                stringResource(
                                    when (state) {
                                        StoredAnalysis.CanAnalyze -> R.string.messages_not_analyzed
                                        StoredAnalysis.Failed -> R.string.messages_analysis_failed
                                        else -> R.string.messages_analyzing
                                    },
                                ),
                                style = SmishingTypography.Note,
                                color = if (failed) SmishingPalette.statusColors(Status.Danger).onContainer else SmishingPalette.TextSecondary,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            when (state) {
                                StoredAnalysis.CanAnalyze, StoredAnalysis.Failed ->
                                    LinkButton(stringResource(R.string.messages_analyze), onClick = { onAnalyze(message) })
                                else -> Unit
                            }
                        }
                    }
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
