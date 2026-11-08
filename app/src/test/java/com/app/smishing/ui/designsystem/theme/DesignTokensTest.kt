package com.app.smishing.ui.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Los tokens son la única fuente de colores, tipografía y espaciado: aquí se comprueban contra el diseño. */
class DesignTokensTest {

    private fun hex(color: Color) = "#%06X".format(color.toArgb() and 0xFFFFFF)

    // Luminancia y contraste según WCAG 2.1.
    private fun luminance(color: Color): Double {
        fun channel(c: Float): Double = if (c <= 0.03928f) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    private fun contrast(a: Color, b: Color): Double {
        val (light, dark) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    @Test
    fun paletteMatchesTheDesign() {
        val expected = mapOf(
            SmishingPalette.Primary to "#1A73E8",
            SmishingPalette.PrimaryDark to "#1557B0",
            SmishingPalette.PrimaryLight to "#D2E3FC",
            SmishingPalette.PrimarySurface to "#E8F0FE",
            SmishingPalette.Background to "#FFFFFF",
            SmishingPalette.BackgroundSecondary to "#F8F9FA",
            SmishingPalette.TextPrimary to "#202124",
            SmishingPalette.TextSecondary to "#5F6368",
            SmishingPalette.Border to "#DADCE0",
            SmishingPalette.Safe to "#1E8E3E",
            SmishingPalette.SafeBackground to "#E6F4EA",
            SmishingPalette.Suspicious to "#E37400",
            SmishingPalette.SuspiciousBackground to "#FEF7E0",
            SmishingPalette.Danger to "#D93025",
            SmishingPalette.DangerBackground to "#FCE8E6",
        )
        expected.forEach { (color, code) -> assertEquals(code, hex(color)) }
    }

    @Test
    fun spacingScaleMatchesTheDesign() {
        val scale = listOf(Spacing.X2s, Spacing.Xs, Spacing.Sm, Spacing.Md, Spacing.Lg, Spacing.Xl, Spacing.X2l, Spacing.X3l, Spacing.X4l)
        assertEquals(listOf(4.dp, 8.dp, 12.dp, 16.dp, 20.dp, 24.dp, 32.dp, 40.dp, 48.dp), scale)
    }

    @Test
    fun radiusScaleMatchesTheDesign() {
        val scale = listOf(Radius.None, Radius.Xs, Radius.Sm, Radius.Md, Radius.Lg, Radius.Xl, Radius.X2l, Radius.X3l, Radius.X4l)
        assertEquals(listOf(0.dp, 2.dp, 6.dp, 8.dp, 12.dp, 16.dp, 20.dp, 24.dp, 28.dp), scale)
    }

    @Test
    fun touchTargetIs48dp() {
        assertEquals(48.dp, MinTouchTarget)
    }

    @Test
    fun typographyMatchesTheDesign() {
        fun check(style: androidx.compose.ui.text.TextStyle, size: Int, line: Int) {
            assertEquals(size.sp, style.fontSize)
            assertEquals(line.sp, style.lineHeight)
        }
        check(SmishingTypography.TitleLarge, 28, 36)
        check(SmishingTypography.TitleMedium, 24, 32)
        check(SmishingTypography.TitleSmall, 22, 28)
        check(SmishingTypography.BodyLarge, 18, 28)
        check(SmishingTypography.BodyLargeBold, 18, 28)
        check(SmishingTypography.BodyMedium, 16, 24)
        check(SmishingTypography.BodyMediumBold, 16, 24)
        check(SmishingTypography.LabelLarge, 18, 24)
        check(SmishingTypography.LabelMedium, 16, 20)
        check(SmishingTypography.LabelSmall, 14, 20)
        check(SmishingTypography.Note, 14, 20)
        check(SmishingTypography.NoteBold, 14, 20)
        check(SmishingTypography.Micro, 12, 16)
    }

    @Test
    fun designStateColorsAreTooLightForSmallTextOnTheirBackground() {
        // Es la razón de las variantes onContainer: con los colores del diseño el texto del chip no llega a 4,5.
        Status.values().forEach { status ->
            val colors = SmishingPalette.statusColors(status)
            assertTrue("$status: ${contrast(colors.content, colors.container)}", contrast(colors.content, colors.container) < 4.5)
        }
    }

    @Test
    fun statusChipTextMeetsWcagAaOnItsBackground() {
        Status.values().forEach { status ->
            val colors = SmishingPalette.statusColors(status)
            val ratio = contrast(colors.onContainer, colors.container)
            assertTrue("$status tiene contraste $ratio, se pide 4.5", ratio >= 4.5)
        }
    }

    @Test
    fun statusTextAlsoMeetsAaOnWhiteAndOnTheSecondaryBackground() {
        Status.values().forEach { status ->
            val onContainer = SmishingPalette.statusColors(status).onContainer
            listOf(SmishingPalette.Background, SmishingPalette.BackgroundSecondary).forEach { background ->
                val ratio = contrast(onContainer, background)
                assertTrue("$status sobre ${hex(background)}: $ratio", ratio >= 4.5)
            }
        }
    }

    @Test
    fun mainTextAndButtonsMeetWcagAa() {
        val pairs = mapOf(
            "texto principal" to (SmishingPalette.TextPrimary to SmishingPalette.Background),
            "texto secundario" to (SmishingPalette.TextSecondary to SmishingPalette.Background),
            "texto secundario sobre gris" to (SmishingPalette.TextSecondary to SmishingPalette.BackgroundSecondary),
            "botón primario" to (Color.White to SmishingPalette.Primary),
            "barra superior" to (Color.White to SmishingPalette.PrimaryDark),
            "enlace y texto de la pestaña activa" to (SmishingPalette.Primary to SmishingPalette.Background),
        )
        pairs.forEach { (name, pair) ->
            val ratio = contrast(pair.first, pair.second)
            assertTrue("$name tiene contraste $ratio, se pide 4.5", ratio >= 4.5)
        }
    }

    @Test
    fun activeTabIconMeetsTheNonTextContrastOfThree() {
        // El icono va sobre el indicador azul claro; es un elemento gráfico, que pide 3:1.
        val ratio = contrast(SmishingPalette.Primary, SmishingPalette.PrimaryLight)
        assertTrue("icono activo: $ratio, se pide 3.0", ratio >= 3.0)
    }
}
