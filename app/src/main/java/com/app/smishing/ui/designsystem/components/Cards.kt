package com.app.smishing.ui.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.app.smishing.R
import com.app.smishing.ui.designsystem.theme.SmishingPalette
import com.app.smishing.ui.designsystem.theme.SmishingShapes
import com.app.smishing.ui.designsystem.theme.SmishingTypography
import com.app.smishing.ui.designsystem.theme.Spacing

/**
 * Estado de la protección en la pantalla de inicio. Activa: tarjeta tranquila. Apagada: fondo blanco con borde y título rojos (para
 * que el Smishy gris se vea bien, que no se ve sobre un fondo rojo) y una acción para encenderla ([actionLabel] y [onAction]).
 */
@Composable
fun ProtectionCard(
    active: Boolean,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val container = if (active) SmishingPalette.BackgroundSecondary else SmishingPalette.Background
    val border = if (active) SmishingPalette.Border else SmishingPalette.Danger
    val textColor = if (active) SmishingPalette.SafeOnBackground else SmishingPalette.DangerOnBackground
    // El mensaje de la tarjeta apagada va en texto oscuro: en rojo y pequeño cansa la lectura; el título y el borde ya avisan.
    val messageColor = if (active) textColor else SmishingPalette.TextPrimary

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(container, SmishingShapes.Lg)
            .border(1.dp, border, SmishingShapes.Lg)
            .padding(Spacing.Md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.Xs),
    ) {
        // Smishy contento si la protección está activa, dormido si está apagada. Es decorativo: el estado ya lo dice el título,
        // así que no lleva descripción para el lector de pantalla.
        Image(
            painter = painterResource(if (active) R.drawable.smishy_active else R.drawable.smishy_off),
            contentDescription = null,
            modifier = Modifier.size(width = 105.dp, height = 128.dp),
        )
        Text(title, style = SmishingTypography.TitleSmall, color = textColor)
        Text(message, style = SmishingTypography.Note, color = messageColor, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            PrimaryButton(actionLabel, onAction, modifier = Modifier.fillMaxWidth().padding(top = Spacing.Xs))
        }
    }
}

/** Tarjeta de resumen con título y filas. [dimmed] la atenúa cuando no hay datos, como con la protección apagada. */
@Composable
fun SummaryCard(
    title: String,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (dimmed) 0.6f else 1f)
            .background(SmishingPalette.BackgroundSecondary, SmishingShapes.Lg)
            .border(1.dp, SmishingPalette.Border, SmishingShapes.Lg)
            .padding(Spacing.Md),
        verticalArrangement = Arrangement.spacedBy(Spacing.Sm),
    ) {
        Text(title, style = SmishingTypography.BodyLargeBold, color = SmishingPalette.TextPrimary)
        content()
    }
}

/** Una línea de [SummaryCard]: icono, descripción y valor en negrita. */
@Composable
fun SummaryRow(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
    ) {
        Icon(icon, contentDescription = null, tint = SmishingPalette.TextSecondary, modifier = Modifier.size(24.dp))
        Text(
            // El valor va en negrita, como en el diseño.
            text = buildAnnotatedString {
                append("$label ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(value) }
            },
            style = SmishingTypography.BodyMedium,
            color = SmishingPalette.TextPrimary,
        )
    }
}
