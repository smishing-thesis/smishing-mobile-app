package com.app.smishing.ui.designsystem.theme

import androidx.compose.ui.graphics.Color

/** Riesgo que un componente representa. El nivel real lo asigna el dominio (#9); aquí solo es presentación. */
enum class Status { Safe, Suspicious, Danger }

/**
 * Colores de un estado.
 * [content] es el color del diseño, para iconos y bordes. [container] es el fondo del chip.
 * [onContainer] es el texto sobre el fondo: una variante más oscura que sí llega a 4,5:1 de contraste.
 */
data class StatusColors(val content: Color, val container: Color, val onContainer: Color)

/** Colores del sistema de diseño (Material You, septiembre 2026). Única fuente de estos valores. */
object SmishingPalette {
    // Marca
    val Primary = Color(0xFF1A73E8)
    val PrimaryDark = Color(0xFF1557B0)
    val PrimaryLight = Color(0xFFD2E3FC)
    val PrimarySurface = Color(0xFFE8F0FE)

    // Neutros
    val Background = Color(0xFFFFFFFF)
    val BackgroundSecondary = Color(0xFFF8F9FA)
    val TextPrimary = Color(0xFF202124)
    val TextSecondary = Color(0xFF5F6368)
    val Border = Color(0xFFDADCE0)

    // Estados, tal como están en el diseño
    val Safe = Color(0xFF1E8E3E)
    val SafeBackground = Color(0xFFE6F4EA)
    val Suspicious = Color(0xFFE37400)
    val SuspiciousBackground = Color(0xFFFEF7E0)
    val Danger = Color(0xFFD93025)
    val DangerBackground = Color(0xFFFCE8E6)

    // Texto de estado sobre su fondo. No están en el diseño: los colores de arriba dan 3,70, 2,90 y 4,05 de
    // contraste y para texto pequeño hace falta 4,5. Estas variantes dan 5,24, 5,50 y 4,92.
    val SafeOnBackground = Color(0xFF137333)
    val SuspiciousOnBackground = Color(0xFF995200)
    val DangerOnBackground = Color(0xFFC5221F)

    fun statusColors(status: Status): StatusColors = when (status) {
        Status.Safe -> StatusColors(Safe, SafeBackground, SafeOnBackground)
        Status.Suspicious -> StatusColors(Suspicious, SuspiciousBackground, SuspiciousOnBackground)
        Status.Danger -> StatusColors(Danger, DangerBackground, DangerOnBackground)
    }
}
