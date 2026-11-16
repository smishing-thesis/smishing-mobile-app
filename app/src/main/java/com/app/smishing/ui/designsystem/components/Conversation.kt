package com.app.smishing.ui.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.app.smishing.ui.designsystem.theme.MinTouchTarget
import com.app.smishing.ui.designsystem.theme.SmishingPalette
import com.app.smishing.ui.designsystem.theme.SmishingShapes
import com.app.smishing.ui.designsystem.theme.SmishingTypography
import com.app.smishing.ui.designsystem.theme.Spacing
import com.app.smishing.ui.designsystem.theme.Status

/**
 * Fila de la lista de conversaciones: remitente, hora, inicio del mensaje y, si hace falta, una advertencia
 * ([warning]) como "Cuidado: Posible estafa". El remitente va siempre en negrita y el inicio del
 * mensaje siempre en texto normal. Con [unreadCount] mayor que cero (#50) aparece un contador azul, que es lo único que marca la
 * conversación como no leída; [unreadDescription] es lo que lee un lector de pantalla del contador ("2 sin leer").
 */
@Composable
fun ConversationRow(
    sender: String,
    time: String,
    snippet: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    warning: String? = null,
    /** El estado de la advertencia, que fija su color: rojo si es peligroso, ámbar si es sospechoso. */
    warningStatus: Status = Status.Suspicious,
    unreadCount: Int = 0,
    unreadDescription: String = "",
    /** Mantener presionada la fila (#51): empezar a seleccionar. [onLongClickLabel] es lo que lee un lector de pantalla. */
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    /** En modo de selección (#51) la fila lleva una casilla, marcada si está [selected]. */
    selectionMode: Boolean = false,
    selected: Boolean = false,
    /** Con [showAvatar] la fila lleva la foto del contacto ([photoUri]) o, si no hay, una imagen por defecto. */
    showAvatar: Boolean = false,
    photoUri: String? = null,
) {
    val unread = unreadCount > 0
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = onLongClickLabel)
                .heightIn(min = 72.dp)
                .padding(horizontal = Spacing.Md, vertical = Spacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = SmishingPalette.Primary))
                Spacer(Modifier.width(Spacing.Sm))
            }
            if (showAvatar) {
                Avatar(photoUri)
                Spacer(Modifier.width(Spacing.Sm))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.X2s)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(sender, style = SmishingTypography.BodyMediumBold, color = SmishingPalette.TextPrimary)
                Text(time, style = SmishingTypography.Note, color = SmishingPalette.TextSecondary)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
                Text(
                    snippet,
                    style = SmishingTypography.BodyMedium,
                    color = SmishingPalette.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (unread) {
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 24.dp, minHeight = 24.dp)
                            .background(SmishingPalette.Primary, CircleShape)
                            .clearAndSetSemantics { contentDescription = unreadDescription }, // el contador se lee como "2 sin leer", no como "2"
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(unreadCount.toString(), style = SmishingTypography.NoteBold, color = Color.White, modifier = Modifier.padding(horizontal = 6.dp))
                    }
                }
            }
            if (warning != null) {
                val colors = SmishingPalette.statusColors(warningStatus)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.X2s)) {
                    Icon(statusIcon(warningStatus), contentDescription = null, tint = colors.onContainer, modifier = Modifier.size(16.dp))
                    Text(warning, style = SmishingTypography.NoteBold, color = colors.onContainer)
                }
            }
            }
        }
        HorizontalDivider(color = SmishingPalette.Border)
    }
}

/** Burbuja de un mensaje: azul claro a la derecha si es enviado, gris a la izquierda si es recibido. */
@Composable
fun MessageBubble(
    text: String,
    outgoing: Boolean,
    modifier: Modifier = Modifier,
    /** Tocar la burbuja (en modo de selección, marcarla) y mantenerla presionada (#51: empezar a seleccionar). */
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    /**
     * Lo que lee el lector de pantalla para decir si el mensaje es recibido o enviado, que el color no transmite. Va en el **mismo nodo que
     * el texto y el toque**: puesto en un nodo de afuera, el toque separa a la burbuja de él y el lector deja de decirlo.
     */
    stateText: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start,
    ) {
        Text(
            text = text,
            style = SmishingTypography.BodyMedium,
            color = SmishingPalette.TextPrimary,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(
                    if (outgoing) SmishingPalette.PrimaryLight else SmishingPalette.BackgroundSecondary,
                    SmishingShapes.Xl,
                )
                .then(if (outgoing) Modifier else Modifier.border(1.dp, SmishingPalette.Border, SmishingShapes.Xl))
                .then(
                    if (onClick == null && onLongClick == null) Modifier
                    else Modifier.combinedClickable(onClick = onClick ?: {}, onLongClick = onLongClick, onLongClickLabel = onLongClickLabel),
                )
                .then(if (stateText == null) Modifier else Modifier.semantics { stateDescription = stateText })
                .padding(horizontal = Spacing.Md, vertical = Spacing.Sm),
        )
    }
}

/** Barra para escribir y enviar en una conversación. [sendDescription] es lo que lee el lector de pantalla. */
@Composable
fun MessageInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    placeholder: String,
    sendDescription: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(Spacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Xs),
    ) {
        SmishingTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            modifier = Modifier.weight(1f),
            shape = SmishingShapes.Full,
        )
        FilledIconButton(
            onClick = onSend,
            modifier = Modifier.size(MinTouchTarget),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = SmishingPalette.Primary,
                contentColor = Color.White,
            ),
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = sendDescription)
        }
    }
}

/**
 * Fila de la lista del historial (#12): remitente y fecha arriba; abajo, el inicio del mensaje y la marca de estado ([chip],
 * por ejemplo un `StatusChip`).
 */
@Composable
fun HistoryRow(
    sender: String,
    date: String,
    snippet: String,
    onClick: () -> Unit,
    chip: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    /** Lo que se hizo con el mensaje (por ejemplo, "Número bloqueado"), o `null` si no se hizo nada (#28). */
    actions: String? = null,
    /** En modo de selección (#52) la fila lleva una casilla, marcada si está [selected]. */
    selectionMode: Boolean = false,
    selected: Boolean = false,
    /** Mantener presionada la fila (#52): empezar a seleccionar. [onLongClickLabel] es lo que lee un lector de pantalla. */
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = onLongClickLabel)
                .heightIn(min = 72.dp)
                .padding(horizontal = Spacing.Md, vertical = Spacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // La fila entera es el control: la casilla solo muestra el estado.
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = SmishingPalette.Primary))
                Spacer(Modifier.width(Spacing.Sm))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.X2s)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(sender, style = SmishingTypography.BodyMediumBold, color = SmishingPalette.TextPrimary)
                Text(date, style = SmishingTypography.Note, color = SmishingPalette.TextSecondary)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
            ) {
                Text(
                    snippet,
                    style = SmishingTypography.BodyMedium,
                    color = SmishingPalette.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                chip()
            }
            if (actions != null) Text(actions, style = SmishingTypography.Note, color = SmishingPalette.TextSecondary)
            }
        }
        HorizontalDivider(color = SmishingPalette.Border)
    }
}
