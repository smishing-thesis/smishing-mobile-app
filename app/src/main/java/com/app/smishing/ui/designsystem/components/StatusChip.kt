package com.app.smishing.ui.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.app.smishing.ui.designsystem.icons.SmishingIcons
import com.app.smishing.ui.designsystem.theme.SmishingPalette
import com.app.smishing.ui.designsystem.theme.SmishingShapes
import com.app.smishing.ui.designsystem.theme.SmishingTypography
import com.app.smishing.ui.designsystem.theme.Spacing
import com.app.smishing.ui.designsystem.theme.Status

/** Icono que acompaña a cada estado: el color nunca es lo único que lo distingue. */
fun statusIcon(status: Status): ImageVector = when (status) {
    Status.Safe -> Icons.Filled.CheckCircle
    Status.Suspicious -> Icons.Outlined.Warning
    Status.Danger -> SmishingIcons.ErrorOutline
}

/** Etiqueta de estado de un mensaje (Peligro, Sospechoso, Seguro). El texto lo da quien la usa. */
@Composable
fun StatusChip(status: Status, label: String, modifier: Modifier = Modifier) {
    val colors = SmishingPalette.statusColors(status)
    Row(
        modifier = modifier
            .background(colors.container, SmishingShapes.Xl)
            .border(1.dp, colors.content, SmishingShapes.Xl)
            .padding(horizontal = Spacing.Sm, vertical = Spacing.X2s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.X2s),
    ) {
        Icon(statusIcon(status), contentDescription = null, tint = colors.onContainer, modifier = Modifier.size(16.dp))
        Text(label, style = SmishingTypography.LabelSmall, color = colors.onContainer)
    }
}
