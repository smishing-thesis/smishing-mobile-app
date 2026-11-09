package com.app.smishing.ui.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
 * ([warning]) como "Cuidado: Posible estafa".
 */
@Composable
fun ConversationRow(
    sender: String,
    time: String,
    snippet: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    warning: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .heightIn(min = 72.dp)
                .padding(horizontal = Spacing.Md, vertical = Spacing.Sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.X2s),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(sender, style = SmishingTypography.BodyMediumBold, color = SmishingPalette.TextPrimary)
                Text(time, style = SmishingTypography.Note, color = SmishingPalette.TextSecondary)
            }
            Text(
                snippet,
                style = SmishingTypography.BodyMedium,
                color = SmishingPalette.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (warning != null) {
                val colors = SmishingPalette.statusColors(Status.Suspicious)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.X2s)) {
                    Icon(Icons.Outlined.Warning, contentDescription = null, tint = colors.onContainer, modifier = Modifier.size(16.dp))
                    Text(warning, style = SmishingTypography.NoteBold, color = colors.onContainer)
                }
            }
        }
        HorizontalDivider(color = SmishingPalette.Border)
    }
}

/** Burbuja de un mensaje: azul claro a la derecha si es enviado, gris a la izquierda si es recibido. */
@Composable
fun MessageBubble(text: String, outgoing: Boolean, modifier: Modifier = Modifier) {
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
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .heightIn(min = 72.dp)
                .padding(horizontal = Spacing.Md, vertical = Spacing.Sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.X2s),
        ) {
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
        }
        HorizontalDivider(color = SmishingPalette.Border)
    }
}
