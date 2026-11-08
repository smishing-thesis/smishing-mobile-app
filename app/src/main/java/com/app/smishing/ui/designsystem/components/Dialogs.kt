package com.app.smishing.ui.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.app.smishing.ui.designsystem.theme.MinTouchTarget
import com.app.smishing.ui.designsystem.theme.SmishingPalette
import com.app.smishing.ui.designsystem.theme.SmishingShapes
import com.app.smishing.ui.designsystem.theme.SmishingTypography
import com.app.smishing.ui.designsystem.theme.Spacing

/**
 * Diálogo con un título, contenido propio y dos acciones: la de confirmar (relleno azul) y la de descartar
 * (texto azul). Para un mensaje simple usa [ConfirmDialog].
 */
@Composable
fun SmishingDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = SmishingShapes.X3l,
        containerColor = SmishingPalette.Background,
        tonalElevation = 0.dp, // con elevación, Material tiñe el fondo de azul; el diseño pide diálogos blancos y planos
        title = { Text(title, style = SmishingTypography.TitleSmall, color = SmishingPalette.TextPrimary) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(Spacing.Xs), content = content) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.heightIn(min = MinTouchTarget),
                shape = SmishingShapes.Xl,
                colors = ButtonDefaults.buttonColors(containerColor = SmishingPalette.Primary, contentColor = Color.White),
            ) {
                Text(confirmLabel, style = SmishingTypography.LabelMedium)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = MinTouchTarget),
                colors = ButtonDefaults.textButtonColors(contentColor = SmishingPalette.Primary),
            ) {
                Text(dismissLabel, style = SmishingTypography.LabelMedium)
            }
        },
    )
}

/** Diálogo de confirmación con un mensaje, como "¿Está seguro?". */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SmishingDialog(
        title = title,
        confirmLabel = confirmLabel,
        onConfirm = onConfirm,
        dismissLabel = dismissLabel,
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        Text(message, style = SmishingTypography.BodyMedium, color = SmishingPalette.TextSecondary)
    }
}
