package com.app.smishing.ui.onboarding

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.app.smishing.R
import com.app.smishing.ui.designsystem.components.OnboardingStepLayout
import com.app.smishing.ui.designsystem.theme.SmishingPalette
import com.app.smishing.ui.designsystem.theme.SmishingTypography

/**
 * Paso 1 del onboarding (#17): qué hace la app, antes de pedir nada. Dice que revisa los mensajes y avisa de las señales de estafa,
 * y que el análisis se hace en el teléfono, sin internet; no promete protección total.
 */
@Composable
fun WelcomeScreen(onNext: () -> Unit, modifier: Modifier = Modifier) {
    OnboardingStepLayout(
        stepIndex = 0,
        stepCount = 2,
        stepLabel = stringResource(R.string.onboarding_step_one_of_two),
        title = stringResource(R.string.welcome_title),
        modifier = modifier,
        primaryLabel = stringResource(R.string.welcome_next),
        onPrimary = onNext,
    ) {
        Text(stringResource(R.string.welcome_body_one), style = SmishingTypography.BodyLarge, color = SmishingPalette.TextPrimary)
        Text(stringResource(R.string.welcome_body_two), style = SmishingTypography.BodyLarge, color = SmishingPalette.TextPrimary)
    }
}
