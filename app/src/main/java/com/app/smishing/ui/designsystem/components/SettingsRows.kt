package com.app.smishing.ui.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.app.smishing.ui.designsystem.theme.MinTouchTarget
import com.app.smishing.ui.designsystem.theme.SmishingPalette
import com.app.smishing.ui.designsystem.theme.SmishingTypography
import com.app.smishing.ui.designsystem.theme.Spacing

/** Título de una sección de ajustes, en mayúsculas pequeñas. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = SmishingTypography.NoteBold,
        color = SmishingPalette.TextSecondary,
        modifier = modifier,
    )
}

/** Una opción de un grupo exclusivo, con título y explicación. Toda la fila es el área táctil. */
@Composable
fun RadioOption(
    title: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = Spacing.Xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = SmishingPalette.Primary,
                unselectedColor = SmishingPalette.TextSecondary,
            ),
        )
        Column {
            Text(title, style = SmishingTypography.BodyMediumBold, color = SmishingPalette.TextPrimary)
            Text(description, style = SmishingTypography.Note, color = SmishingPalette.TextSecondary)
        }
    }
}

/** Una preferencia que se activa o desactiva. Toda la fila es el área táctil. */
@Composable
fun SwitchRow(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = Spacing.Xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = SmishingTypography.BodyMediumBold, color = SmishingPalette.TextPrimary)
            if (description != null) Text(description, style = SmishingTypography.Note, color = SmishingPalette.TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = SmishingPalette.Primary,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = SmishingPalette.Border,
                uncheckedThumbColor = Color.White,
                uncheckedBorderColor = SmishingPalette.Border,
            ),
        )
    }
}

/** Una fila con título, explicación y un botón, para una acción que no cambia una preferencia (como "Ver recorrido"). */
@Composable
fun ActionRow(title: String, description: String, buttonLabel: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.X2s)) {
        Text(title, style = SmishingTypography.BodyMediumBold, color = SmishingPalette.TextPrimary)
        Text(description, style = SmishingTypography.Note, color = SmishingPalette.TextSecondary)
        SecondaryButton(buttonLabel, onClick = onClick, modifier = Modifier.padding(top = Spacing.X2s))
    }
}

/** Estado de un permiso: visto verde si está concedido, aviso si no. */
@Composable
fun PermissionRow(label: String, granted: Boolean, modifier: Modifier = Modifier) {
    val tint = if (granted) SmishingPalette.SafeOnBackground else SmishingPalette.SuspiciousOnBackground
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = MinTouchTarget),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Outlined.Warning,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
        Text(label, style = SmishingTypography.BodyMediumBold, color = SmishingPalette.TextPrimary)
    }
}
