package com.app.smishing.ui.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.app.smishing.ui.designsystem.theme.SmishingPalette
import com.app.smishing.ui.designsystem.theme.SmishingTypography
import com.app.smishing.ui.designsystem.theme.Spacing

/** Puntos del paso actual del onboarding. [stepIndex] empieza en 0. */
@Composable
fun StepIndicator(stepIndex: Int, stepCount: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.Xs)) {
        repeat(stepCount) { index ->
            Box(
                Modifier
                    .size(8.dp)
                    .background(
                        if (index == stepIndex) SmishingPalette.Primary else SmishingPalette.Border,
                        CircleShape,
                    ),
            )
        }
    }
}

/**
 * Pantalla de un paso del onboarding: puntos y "PASO N DE M" arriba, título, contenido propio, la acción principal
 * y, si hace falta, una acción para omitir. Los textos los da quien la usa.
 */
@Composable
fun OnboardingStepLayout(
    stepIndex: Int,
    stepCount: Int,
    stepLabel: String,
    title: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    skipLabel: String? = null,
    onSkip: (() -> Unit)? = null,
    body: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SmishingPalette.Background)
            .verticalScroll(rememberScrollState())
            .padding(Spacing.Xl),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = stepLabel },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.Xs),
        ) {
            StepIndicator(stepIndex, stepCount)
            Text(stepLabel, style = SmishingTypography.Micro, color = SmishingPalette.TextSecondary, textAlign = TextAlign.Center)
        }
        Column(
            modifier = Modifier.padding(top = Spacing.X2l),
            verticalArrangement = Arrangement.spacedBy(Spacing.Md),
        ) {
            Text(title, style = SmishingTypography.TitleMedium, color = SmishingPalette.TextPrimary)
            body()
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.X2l),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.Xs),
        ) {
            PrimaryButton(primaryLabel, onPrimary, modifier = Modifier.fillMaxWidth())
            if (skipLabel != null && onSkip != null) LinkButton(skipLabel, onSkip)
        }
    }
}
