package com.app.smishing.ui.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.app.smishing.ui.designsystem.theme.Elevation
import com.app.smishing.ui.designsystem.theme.MinTouchTarget
import com.app.smishing.ui.designsystem.theme.SmishingPalette
import com.app.smishing.ui.designsystem.theme.SmishingShapes
import com.app.smishing.ui.designsystem.theme.SmishingTypography
import com.app.smishing.ui.designsystem.theme.Spacing

enum class TopBarStyle {
    /** Barra azul oscura, con texto blanco: Nuevo mensaje, Historial, Detalle, Ajustes. */
    Primary,

    /** Barra blanca con texto oscuro: Mensajes y la conversación. */
    Plain,
}

/**
 * Barra superior. Con [onBack] muestra la flecha de volver ([backDescription] es lo que lee el lector de
 * pantalla). [actions] va a la derecha.
 */
@Composable
fun SmishingTopBar(
    title: String,
    modifier: Modifier = Modifier,
    style: TopBarStyle = TopBarStyle.Primary,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    backDescription: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val container = if (style == TopBarStyle.Primary) SmishingPalette.PrimaryDark else SmishingPalette.Background
    val content = if (style == TopBarStyle.Primary) Color.White else SmishingPalette.TextPrimary
    val secondary = if (style == TopBarStyle.Primary) Color.White else SmishingPalette.TextSecondary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(container)
            .heightIn(min = 56.dp)
            .padding(horizontal = Spacing.X2s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.heightIn(min = MinTouchTarget)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backDescription, tint = content)
            }
        } else {
            Spacer(Modifier.width(Spacing.Xs))
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = Spacing.Xs)) {
            val titleStyle = if (subtitle != null) SmishingTypography.BodyLargeBold else SmishingTypography.TitleSmall
            Text(title, style = titleStyle, color = content)
            if (subtitle != null) Text(subtitle, style = SmishingTypography.Micro, color = secondary)
        }
        actions()
    }
}

/** Botón de búsqueda para la barra superior. */
@Composable
fun SearchAction(description: String, onClick: () -> Unit, tint: Color = SmishingPalette.TextPrimary) {
    IconButton(onClick = onClick, modifier = Modifier.heightIn(min = MinTouchTarget)) {
        Icon(Icons.Outlined.Search, contentDescription = description, tint = tint)
    }
}

/** Una pestaña de la navegación inferior. */
data class NavItem(val label: String, val icon: ImageVector)

/** Navegación inferior: Inicio, Mensajes, Historial y Ajustes. */
@Composable
fun SmishingBottomBar(
    items: List<NavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier,
        containerColor = SmishingPalette.Background,
        tonalElevation = 0.dp,
    ) {
        items.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(item.label, style = SmishingTypography.LabelSmall) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = SmishingPalette.Primary,
                    selectedTextColor = SmishingPalette.Primary,
                    indicatorColor = SmishingPalette.PrimaryLight,
                    unselectedIconColor = SmishingPalette.TextSecondary,
                    unselectedTextColor = SmishingPalette.TextSecondary,
                ),
            )
        }
    }
}

/** Botón flotante extendido, como "Escribir" en la lista de mensajes. */
@Composable
fun SmishingExtendedFab(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Edit,
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = MinTouchTarget),
        shape = SmishingShapes.Xl,
        containerColor = SmishingPalette.Primary,
        contentColor = Color.White,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = Elevation.Medium),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.Xs)) {
            Icon(icon, contentDescription = null)
            Text(text, style = SmishingTypography.LabelMedium)
        }
    }
}
