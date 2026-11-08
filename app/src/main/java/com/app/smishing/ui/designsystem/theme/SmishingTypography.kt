package com.app.smishing.ui.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Los 13 estilos de texto del diseño. Roboto (la fuente del sistema en Android), en sp para que sigan el tamaño
 * de letra que la persona elige. El diseño los nombra en "px"; en Android son sp.
 */
object SmishingTypography {
    private val family = FontFamily.Default

    private fun style(weight: FontWeight, size: TextUnit, lineHeight: TextUnit) =
        TextStyle(fontFamily = family, fontWeight = weight, fontSize = size, lineHeight = lineHeight)

    val TitleLarge = style(FontWeight.Bold, 28.sp, 36.sp)
    val TitleMedium = style(FontWeight.Bold, 24.sp, 32.sp)
    val TitleSmall = style(FontWeight.Bold, 22.sp, 28.sp)

    val BodyLarge = style(FontWeight.Normal, 18.sp, 28.sp)
    val BodyLargeBold = style(FontWeight.Bold, 18.sp, 28.sp)
    val BodyMedium = style(FontWeight.Normal, 16.sp, 24.sp)
    val BodyMediumBold = style(FontWeight.Bold, 16.sp, 24.sp)

    val LabelLarge = style(FontWeight.Medium, 18.sp, 24.sp)
    val LabelMedium = style(FontWeight.Medium, 16.sp, 20.sp)
    val LabelSmall = style(FontWeight.Medium, 14.sp, 20.sp)

    val Note = style(FontWeight.Normal, 14.sp, 20.sp)
    val NoteBold = style(FontWeight.Bold, 14.sp, 20.sp)
    val Micro = style(FontWeight.Normal, 12.sp, 16.sp)

    /** Los estilos de Material 3 que usan sus componentes (diálogos, campos) toman los equivalentes del diseño. */
    fun toMaterial() = Typography(
        titleLarge = TitleLarge,
        titleMedium = TitleMedium,
        titleSmall = TitleSmall,
        bodyLarge = BodyLarge,
        bodyMedium = BodyMedium,
        bodySmall = Note,
        labelLarge = LabelLarge,
        labelMedium = LabelMedium,
        labelSmall = LabelSmall,
    )
}
