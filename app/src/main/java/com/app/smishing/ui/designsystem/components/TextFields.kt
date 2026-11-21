package com.app.smishing.ui.designsystem.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import com.app.smishing.ui.designsystem.theme.MinTouchTarget
import com.app.smishing.ui.designsystem.theme.SmishingPalette
import com.app.smishing.ui.designsystem.theme.SmishingShapes
import com.app.smishing.ui.designsystem.theme.SmishingTypography

/** Campo de texto con borde. [minLines] mayor que 1 lo vuelve un área para redactar. */
@Composable
fun SmishingTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    shape: Shape = SmishingShapes.Lg,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.heightIn(min = MinTouchTarget),
        textStyle = SmishingTypography.BodyMedium,
        placeholder = { Text(placeholder, style = SmishingTypography.BodyMedium, color = SmishingPalette.TextSecondary) },
        minLines = minLines,
        keyboardOptions = keyboardOptions,
        shape = shape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = SmishingPalette.Primary,
            unfocusedBorderColor = SmishingPalette.Border,
            focusedTextColor = SmishingPalette.TextPrimary,
            unfocusedTextColor = SmishingPalette.TextPrimary,
            focusedContainerColor = SmishingPalette.Background,
            unfocusedContainerColor = SmishingPalette.Background,
            cursorColor = SmishingPalette.Primary,
        ),
    )
}

/** Campo de búsqueda de una sola línea, con lupa, para ponerlo bajo el encabezado: esquinas muy redondas. */
@Composable
fun SmishingSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.heightIn(min = MinTouchTarget),
        textStyle = SmishingTypography.BodyMedium,
        placeholder = { Text(placeholder, style = SmishingTypography.BodyMedium, color = SmishingPalette.TextSecondary) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = SmishingPalette.TextSecondary) },
        singleLine = true,
        shape = SmishingShapes.Xl,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = SmishingPalette.Primary,
            unfocusedBorderColor = SmishingPalette.Border,
            focusedTextColor = SmishingPalette.TextPrimary,
            unfocusedTextColor = SmishingPalette.TextPrimary,
            focusedContainerColor = SmishingPalette.Background,
            unfocusedContainerColor = SmishingPalette.Background,
            cursorColor = SmishingPalette.Primary,
        ),
    )
}
