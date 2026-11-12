package com.app.smishing.ui.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.app.smishing.ui.designsystem.theme.MinTouchTarget
import com.app.smishing.ui.designsystem.theme.SmishingPalette
import com.app.smishing.ui.designsystem.theme.SmishingShapes
import com.app.smishing.ui.designsystem.theme.SmishingTypography
import com.app.smishing.ui.designsystem.theme.Spacing
import com.app.smishing.ui.designsystem.theme.Status

/** Aviso rojo sobre un mensaje sospechoso, con una acción como "Ver riesgo". */
@Composable
fun AlertBanner(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SmishingPalette.statusColors(Status.Danger)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.container, SmishingShapes.Lg)
            .border(1.dp, colors.content, SmishingShapes.Lg)
            .padding(Spacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
    ) {
        Icon(statusIcon(Status.Danger), contentDescription = null, tint = colors.onContainer, modifier = Modifier.size(24.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = SmishingTypography.NoteBold, color = colors.onContainer)
            Text(message, style = SmishingTypography.Note, color = colors.onContainer)
        }
        Button(
            onClick = onAction,
            modifier = Modifier.heightIn(min = MinTouchTarget),
            shape = SmishingShapes.Xl,
            colors = ButtonDefaults.buttonColors(containerColor = SmishingPalette.Primary, contentColor = Color.White),
            contentPadding = PaddingValues(horizontal = Spacing.Sm),
        ) {
            Text(actionLabel, style = SmishingTypography.LabelSmall)
        }
    }
}

/**
 * Aviso de una sola línea sobre un mensaje (#60): el estado con su color (rojo si es peligroso, ámbar si es sospechoso) y una acción como
 * "Ver riesgo". Más chico que [AlertBanner] para no molestar dentro de una conversación.
 */
@Composable
fun RiskNotice(
    status: Status,
    title: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SmishingPalette.statusColors(status)
    Row(
        modifier = modifier
            .background(colors.container, SmishingShapes.Lg)
            .border(1.dp, colors.content, SmishingShapes.Lg)
            .padding(start = Spacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Xs),
    ) {
        Icon(statusIcon(status), contentDescription = null, tint = colors.onContainer, modifier = Modifier.size(18.dp))
        Text(title, style = SmishingTypography.NoteBold, color = colors.onContainer)
        LinkButton(actionLabel, onClick = onAction)
    }
}

/** Encabezado grande con el estado de un análisis, por ejemplo "Mensaje Peligroso". */
@Composable
fun StatusBanner(status: Status, text: String, modifier: Modifier = Modifier) {
    val colors = SmishingPalette.statusColors(status)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.container, SmishingShapes.Lg)
            .padding(horizontal = Spacing.Md, vertical = Spacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
    ) {
        Icon(statusIcon(status), contentDescription = null, tint = colors.onContainer, modifier = Modifier.size(24.dp))
        Text(text, style = SmishingTypography.TitleSmall, color = colors.onContainer)
    }
}
