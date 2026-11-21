package com.app.smishing.ui.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.app.smishing.R
import com.app.smishing.ui.designsystem.theme.SmishingPalette
import com.app.smishing.ui.designsystem.theme.SmishingShapes
import com.app.smishing.ui.designsystem.theme.SmishingTypography
import com.app.smishing.ui.designsystem.theme.Spacing

/** Las tarjetas del Inicio no llevan borde: solo una sombra suave pero visible sobre el fondo blanco, con las esquinas redondeadas del diseño. */
private fun Modifier.softShadow(): Modifier = shadow(
    elevation = 14.dp,
    shape = SmishingShapes.Lg,
    ambientColor = Color(0x59000000),
    spotColor = Color(0x80000000),
)

/**
 * Estado de la protección en la pantalla de inicio: una escena ilustrada arriba con Smishy, y debajo el título, el mensaje y, si falta
 * algo, una acción para encenderla ([actionLabel] y [onAction]).
 *
 * Activa: la escena cambia con la hora ([dayPart]: mañana, mediodía y tarde, atardecer o noche) y Smishy va contento. Apagada: siempre la
 * ciudad de noche en blanco y negro, con Smishy dormido y el título en rojo. La tarjeta es blanca y sin borde, con una sombra suave.
 */
@Composable
fun ProtectionCard(
    active: Boolean,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    dayPart: DayPart = DayPart.Midday,
) {
    val titleColor = if (active) SmishingPalette.SafeOnBackground else SmishingPalette.DangerOnBackground

    Column(
        modifier = modifier
            .fillMaxWidth()
            .softShadow()
            .background(SmishingPalette.Background),
    ) {
        // La escena y Smishy son decorativos: el estado ya lo dice el título, así que no llevan descripción para el lector de pantalla.
        Box(Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(if (active) dayPart.scene else R.drawable.scene_off),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth,
            )
            Image(
                painter = painterResource(if (active) R.drawable.smishy_active else R.drawable.smishy_off),
                contentDescription = null,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(0.27f).aspectRatio(120f / 146f),
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.Md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.Xs),
        ) {
            Text(title, style = SmishingTypography.TitleSmall, color = titleColor)
            Text(message, style = SmishingTypography.Note, color = SmishingPalette.TextPrimary, textAlign = TextAlign.Center)
            if (actionLabel != null && onAction != null) {
                PrimaryButton(actionLabel, onAction, modifier = Modifier.fillMaxWidth().padding(top = Spacing.Xs))
            }
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
            .softShadow()
            .background(SmishingPalette.Background)
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
