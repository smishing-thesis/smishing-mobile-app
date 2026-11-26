package com.app.smishing.ui.designsystem.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
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
 * Los iconos de la barra de estado del sistema: oscuros ([darkIcons] en `true`) sobre fondo claro, claros sobre el azul. Con la app
 * dibujada bajo las barras (#63), cada pantalla dice de qué color es lo que queda detrás.
 */
@Composable
internal fun StatusBarIcons(darkIcons: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkIcons
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Barra superior. Con [onBack] muestra la flecha de volver ([backDescription] es lo que lee el lector de
 * pantalla). [actions] va a la derecha. El color de la barra llega hasta arriba, bajo la barra de estado del sistema (#63), y su
 * contenido empieza debajo.
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

    StatusBarIcons(darkIcons = style == TopBarStyle.Plain)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(container)
            .windowInsetsPadding(WindowInsets.statusBars)
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

/** Una pestaña de la navegación inferior. [target] es la clave con que un coachmark puede señalarla (#88); sin ella no se señala. */
data class NavItem(val label: String, val icon: ImageVector, val target: Any? = null)

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
                modifier = Modifier.coachmarkTarget(item.target),
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
