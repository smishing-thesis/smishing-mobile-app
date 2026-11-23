package com.app.smishing.ui.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.app.smishing.R
import com.app.smishing.ui.designsystem.theme.SmishingPalette
import com.app.smishing.ui.designsystem.theme.SmishingTypography
import com.app.smishing.ui.designsystem.theme.Spacing

/**
 * Puntos del paso actual del onboarding. [stepIndex] empieza en 0. Los colores cambian solo sobre fondos de color.
 * Con [onSelect], cada punto se puede tocar para ir a ese paso (con un área de toque más grande que el punto) y [labels] lleva lo que el
 * lector de pantalla dice de cada uno.
 */
@Composable
fun StepIndicator(
    stepIndex: Int,
    stepCount: Int,
    modifier: Modifier = Modifier,
    activeColor: Color = SmishingPalette.Primary,
    inactiveColor: Color = SmishingPalette.Border,
    onSelect: ((Int) -> Unit)? = null,
    labels: List<String> = emptyList(),
    spaced: Boolean = false,
) {
    // Con [spaced] (o con [onSelect]) cada punto ocupa su área de toque, así que quedan igual de separados aunque no se toquen.
    val cells = spaced || onSelect != null
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(if (cells) 0.dp else Spacing.Xs)) {
        repeat(stepCount) { index ->
            val dot = Modifier
                .size(8.dp)
                .background(if (index == stepIndex) activeColor else inactiveColor, CircleShape)
            if (!cells) {
                Box(dot)
            } else if (onSelect == null) {
                Box(Modifier.size(width = 32.dp, height = 44.dp), contentAlignment = Alignment.Center) { Box(dot) }
            } else {
                Box(
                    modifier = Modifier
                        .size(width = 32.dp, height = 44.dp)
                        .semantics {
                            contentDescription = labels.getOrElse(index) { "" }
                            selected = index == stepIndex
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                        ) { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) { Box(dot) }
            }
        }
    }
}

/** Colores de una pantalla de onboarding con ilustración: el fondo, el texto, los puntos, el botón principal y el enlace. */
class OnboardingColors(
    val background: Color,
    val text: Color,
    val indicatorActive: Color,
    val indicatorInactive: Color,
    val buttonContainer: Color,
    val buttonContent: Color,
    val link: Color,
) {
    companion object {
        /** Sobre el azul de la marca (bienvenida): texto y botón blancos. */
        val OnBrand = OnboardingColors(
            background = SmishingPalette.Primary,
            text = Color.White,
            indicatorActive = Color.White,
            indicatorInactive = Color(0xFF7FB0F5),
            buttonContainer = Color.White,
            buttonContent = SmishingPalette.PrimaryDark,
            link = Color.White,
        )

        /** Sobre azul claro (paso del rol): texto oscuro y botón azul. */
        val OnLight = OnboardingColors(
            background = SmishingPalette.PrimaryLight,
            text = SmishingPalette.TextPrimary,
            indicatorActive = SmishingPalette.Primary,
            indicatorInactive = Color(0xFFB5C9EE),
            buttonContainer = SmishingPalette.Primary,
            buttonContent = Color.White,
            link = SmishingPalette.PrimaryDark,
        )
    }
}

/**
 * Pantalla de un paso del onboarding con una ilustración arriba ([art], un dibujo vectorial que se ajusta al ancho), los puntos del
 * paso encima (si [showSteps]; el paso 1 va sin ellos), el título y el texto abajo, el botón principal y, si hace falta, "Omitir". "PASO N DE M" ya no se ve, pero
 * [stepLabel] lo lee el lector de pantalla. El texto de los botones se parte en dos líneas si no cabe. Si el texto es más grande de lo
 * que cabe (letra grande en el teléfono), la pantalla se desplaza.
 */
@Composable
fun IllustratedOnboardingLayout(
    @DrawableRes art: Int,
    colors: OnboardingColors,
    stepIndex: Int,
    stepCount: Int,
    stepLabel: String,
    title: String,
    modifier: Modifier = Modifier,
    primaryLabel: String? = null,
    onPrimary: (() -> Unit)? = null,
    skipLabel: String? = null,
    onSkip: (() -> Unit)? = null,
    onStepSelected: ((Int) -> Unit)? = null,
    showSteps: Boolean = true,
    body: @Composable ColumnScope.(textColor: Color) -> Unit,
) {
    // Iconos oscuros si el fondo de este paso es claro (el del paso 2 y el 3); claros sobre el azul de la marca.
    StatusBarIcons(darkIcons = colors.background.luminance() > 0.5f)
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
    ) {
        Box(Modifier.fillMaxWidth()) {
            // Decorativa: el título y el texto dicen todo lo que importa.
            Image(
                painter = painterResource(art),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth,
            )
            if (showSteps) {
                // Con puntos que se tocan, cada uno lleva su descripción; si no, el grupo entero dice "PASO N DE M".
                val labels = (0 until stepCount).map { i ->
                    if (i == stepIndex) stepLabel else stringResource(R.string.onboarding_go_to_step, i + 1)
                }
                StepIndicator(
                    stepIndex = stepIndex,
                    stepCount = stepCount,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = Spacing.Xs)
                        .then(
                            if (onStepSelected == null) Modifier.semantics(mergeDescendants = true) { contentDescription = stepLabel }
                            else Modifier,
                        ),
                    activeColor = colors.indicatorActive,
                    inactiveColor = colors.indicatorInactive,
                    onSelect = onStepSelected,
                    labels = labels,
                    spaced = true,
                )
            }
        }
        Column(
            modifier = Modifier.padding(horizontal = Spacing.Xl).padding(top = Spacing.Sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.Sm),
        ) {
            Text(title, style = SmishingTypography.TitleMedium, color = colors.text)
            body(colors.text)
        }
        if ((primaryLabel != null && onPrimary != null) || (skipLabel != null && onSkip != null)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Xl).padding(top = Spacing.Md, bottom = Spacing.Md),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.Xs),
            ) {
                if (primaryLabel != null && onPrimary != null) {
                    PrimaryButton(
                        primaryLabel,
                        onPrimary,
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = colors.buttonContainer,
                        contentColor = colors.buttonContent,
                    )
                }
                if (skipLabel != null && onSkip != null) LinkButton(skipLabel, onSkip, color = colors.link)
            }
        }
    }
}

/**
 * Pantalla de un paso del onboarding: puntos y "PASO N DE M" arriba (sin ellos si no se dan: es la pantalla de recuperación que
 * aparece solo si falta el permiso de lectura), título, contenido propio, la acción principal
 * y, si hace falta, una acción para omitir. Los textos los da quien la usa.
 */
@Composable
fun OnboardingStepLayout(
    stepIndex: Int?,
    stepCount: Int?,
    stepLabel: String?,
    title: String,
    modifier: Modifier = Modifier,
    primaryLabel: String? = null,
    onPrimary: (() -> Unit)? = null,
    skipLabel: String? = null,
    onSkip: (() -> Unit)? = null,
    body: @Composable ColumnScope.() -> Unit,
) {
    // Fondo blanco: iconos oscuros, y el contenido deja lugar a las barras del sistema (#63).
    StatusBarIcons(darkIcons = true)
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SmishingPalette.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.Xl),
    ) {
        if (stepIndex != null && stepCount != null && stepLabel != null) {
            Column(
                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = stepLabel },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.Xs),
            ) {
                StepIndicator(stepIndex, stepCount)
                Text(stepLabel, style = SmishingTypography.Micro, color = SmishingPalette.TextSecondary, textAlign = TextAlign.Center)
            }
        }
        Column(
            modifier = Modifier.padding(top = Spacing.X2l),
            verticalArrangement = Arrangement.spacedBy(Spacing.Md),
        ) {
            Text(title, style = SmishingTypography.TitleMedium, color = SmishingPalette.TextPrimary)
            body()
        }
        val hasPrimary = primaryLabel != null && onPrimary != null
        val hasSkip = skipLabel != null && onSkip != null
        if (hasPrimary || hasSkip) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.X2l),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.Xs),
            ) {
                if (primaryLabel != null && onPrimary != null) {
                    PrimaryButton(primaryLabel, onPrimary, modifier = Modifier.fillMaxWidth())
                }
                if (skipLabel != null && onSkip != null) LinkButton(skipLabel, onSkip)
            }
        }
    }
}
