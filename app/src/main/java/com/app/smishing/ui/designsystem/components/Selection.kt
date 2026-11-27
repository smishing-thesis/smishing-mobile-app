package com.app.smishing.ui.designsystem.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.app.smishing.R
import com.app.smishing.ui.designsystem.theme.SmishingPalette
import com.app.smishing.ui.designsystem.theme.SmishingTypography

private fun contentColor(style: TopBarStyle) = if (style == TopBarStyle.Primary) Color.White else SmishingPalette.Primary

/** El botón "Seleccionar" que va en la barra de una lista (#51, #52): empieza a elegir varios elementos para borrarlos. */
@Composable
fun RowScope.SelectAction(style: TopBarStyle, onClick: () -> Unit) {
    TextButton(onClick = onClick, colors = ButtonDefaults.textButtonColors(contentColor = contentColor(style))) {
        Text(stringResource(R.string.select_action), style = SmishingTypography.LabelMedium)
    }
}

/**
 * La barra mientras se eligen elementos (#51, #52): "N seleccionados", la flecha para cancelar, "Todos" y "Borrar". "Borrar" no se puede
 * tocar mientras no haya ninguno elegido. Con todos ya elegidos ([allSelected]), "Todos" pasa a "Ninguno" y quita la selección.
 */
@Composable
fun SelectionTopBar(
    count: Int,
    allSelected: Boolean,
    style: TopBarStyle,
    onCancel: () -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SmishingTopBar(
        title = pluralStringResource(R.plurals.history_selected, count, count),
        modifier = modifier,
        style = style,
        onBack = onCancel,
        backDescription = stringResource(R.string.history_cancel_selection),
        actions = {
            val color = contentColor(style)
            TextButton(onClick = onSelectAll, colors = ButtonDefaults.textButtonColors(contentColor = color)) {
                Text(
                    stringResource(if (allSelected) R.string.history_select_none else R.string.history_select_all),
                    style = SmishingTypography.LabelMedium,
                )
            }
            TextButton(
                onClick = onDelete,
                enabled = count > 0,
                colors = ButtonDefaults.textButtonColors(contentColor = color, disabledContentColor = color.copy(alpha = 0.5f)),
            ) {
                Text(stringResource(R.string.delete_confirm), style = SmishingTypography.LabelMedium)
            }
        },
    )
}
