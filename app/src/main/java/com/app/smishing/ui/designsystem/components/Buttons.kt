package com.app.smishing.ui.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.unit.dp
import com.app.smishing.ui.designsystem.theme.MinTouchTarget
import com.app.smishing.ui.designsystem.theme.SmishingPalette
import com.app.smishing.ui.designsystem.theme.SmishingShapes
import com.app.smishing.ui.designsystem.theme.SmishingTypography
import com.app.smishing.ui.designsystem.theme.Spacing

private val ButtonPadding = PaddingValues(horizontal = Spacing.Xl, vertical = Spacing.Sm)

/** Acción principal de una pantalla: relleno azul. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = MinTouchTarget),
        enabled = enabled,
        shape = SmishingShapes.Xl,
        colors = ButtonDefaults.buttonColors(
            containerColor = SmishingPalette.Primary,
            contentColor = Color.White,
            disabledContainerColor = SmishingPalette.Border,
            disabledContentColor = SmishingPalette.TextSecondary,
        ),
        contentPadding = ButtonPadding,
    ) {
        Text(text, style = SmishingTypography.LabelLarge, textAlign = TextAlign.Center)
    }
}

/** Acción secundaria: borde azul y fondo transparente. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = MinTouchTarget),
        enabled = enabled,
        shape = SmishingShapes.Xl,
        border = BorderStroke(1.dp, if (enabled) SmishingPalette.Primary else SmishingPalette.Border),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = SmishingPalette.Primary,
            disabledContentColor = SmishingPalette.TextSecondary,
        ),
        contentPadding = ButtonPadding,
    ) {
        Text(text, style = SmishingTypography.LabelMedium, textAlign = TextAlign.Center)
    }
}

/** Acción de baja prioridad, como "Omitir por ahora": texto azul subrayado. */
@Composable
fun LinkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = MinTouchTarget),
        colors = ButtonDefaults.textButtonColors(contentColor = SmishingPalette.Primary),
    ) {
        Text(text, style = SmishingTypography.LabelMedium.copy(textDecoration = TextDecoration.Underline))
    }
}
