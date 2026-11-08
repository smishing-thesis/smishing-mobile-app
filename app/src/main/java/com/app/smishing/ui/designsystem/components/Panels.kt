package com.app.smishing.ui.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.app.smishing.ui.designsystem.theme.SmishingPalette
import com.app.smishing.ui.designsystem.theme.SmishingShapes
import com.app.smishing.ui.designsystem.theme.SmishingTypography
import com.app.smishing.ui.designsystem.theme.Spacing

/** Cuadro con el texto original de un mensaje. */
@Composable
fun OriginalMessageBox(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = SmishingTypography.BodyMedium,
        color = SmishingPalette.TextPrimary,
        modifier = modifier
            .fillMaxWidth()
            .background(SmishingPalette.BackgroundSecondary, SmishingShapes.Lg)
            .border(1.dp, SmishingPalette.Border, SmishingShapes.Lg)
            .padding(Spacing.Md),
    )
}

/** Un motivo por el que un mensaje es riesgoso: icono en un círculo y texto. */
@Composable
fun RiskReasonRow(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(SmishingPalette.BackgroundSecondary, CircleShape)
                .border(1.dp, SmishingPalette.Border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = SmishingPalette.TextSecondary, modifier = Modifier.size(20.dp))
        }
        Text(text, style = SmishingTypography.BodyMedium, color = SmishingPalette.TextPrimary)
    }
}

/** Una acción que la app ya realizó, con visto verde. */
@Composable
fun ActionDoneRow(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
    ) {
        Icon(Icons.Filled.Check, contentDescription = null, tint = SmishingPalette.SafeOnBackground, modifier = Modifier.size(20.dp))
        Text(text, style = SmishingTypography.BodyMediumBold, color = SmishingPalette.TextPrimary)
    }
}

/** Panel gris con un título en mayúsculas y un texto, como "Recomendación". */
@Composable
fun InfoPanel(title: String, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SmishingPalette.BackgroundSecondary, SmishingShapes.Lg)
            .border(1.dp, SmishingPalette.Border, SmishingShapes.Lg)
            .padding(Spacing.Md),
        verticalArrangement = Arrangement.spacedBy(Spacing.X2s),
    ) {
        Text(title.uppercase(), style = SmishingTypography.NoteBold, color = SmishingPalette.TextSecondary)
        Text(text, style = SmishingTypography.Note, color = SmishingPalette.TextPrimary)
    }
}
