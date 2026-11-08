package com.app.smishing.ui.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Escala de espaciado del diseño. El mínimo recomendado entre elementos interactivos es [Sm]. */
object Spacing {
    val X2s = 4.dp
    val Xs = 8.dp
    val Sm = 12.dp
    val Md = 16.dp
    val Lg = 20.dp
    val Xl = 24.dp
    val X2l = 32.dp
    val X3l = 40.dp
    val X4l = 48.dp
}

/** Radios de esquina. Los más usados: [Lg] en tarjetas y [Xl] en botones y chips. */
object Radius {
    val None = 0.dp
    val Xs = 2.dp
    val Sm = 6.dp
    val Md = 8.dp
    val Lg = 12.dp
    val Xl = 16.dp
    val X2l = 20.dp
    val X3l = 24.dp
    val X4l = 28.dp
}

object SmishingShapes {
    val Sm = RoundedCornerShape(Radius.Sm)
    val Md = RoundedCornerShape(Radius.Md)
    val Lg = RoundedCornerShape(Radius.Lg)
    val Xl = RoundedCornerShape(Radius.Xl)
    val X2l = RoundedCornerShape(Radius.X2l)
    val X3l = RoundedCornerShape(Radius.X3l)
    val Full = RoundedCornerShape(percent = 50)
}

/**
 * Sombras: sutil (tarjetas), media (botón flotante y diálogos) y alta (modales). El diseño no da valores en dp:
 * estos son una elección razonable, y se usan con moderación porque la app prioriza un diseño plano para gama baja.
 */
object Elevation {
    val Subtle = 1.dp
    val Medium = 4.dp
    val High = 8.dp
}

/** Área táctil mínima de todo elemento interactivo. */
val MinTouchTarget = 48.dp
