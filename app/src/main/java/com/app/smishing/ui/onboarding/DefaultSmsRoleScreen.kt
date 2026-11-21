package com.app.smishing.ui.onboarding

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.app.smishing.R
import com.app.smishing.domain.sms.SmsRoleState
import com.app.smishing.ui.designsystem.components.IllustratedOnboardingLayout
import com.app.smishing.ui.designsystem.components.OnboardingColors
import com.app.smishing.ui.designsystem.theme.SmishingTypography

/**
 * Paso 2 del onboarding: explica por qué la app necesita ser la de mensajería predeterminada antes de pedirlo, y
 * muestra el resultado. [onRequest] abre el diálogo del sistema; [onSkip] sigue sin el rol; [onBack] vuelve al paso 1. Va sobre azul claro, con la app de
 * mensajes actual y Smishy arriba.
 */
@Composable
fun DefaultSmsRoleScreen(
    state: SmsRoleState,
    onRequest: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    IllustratedOnboardingLayout(
        art = R.drawable.onboarding_role_art,
        colors = OnboardingColors.OnLight,
        stepIndex = 1,
        stepCount = 2,
        stepLabel = stringResource(R.string.onboarding_step_two_of_two),
        title = stringResource(R.string.default_sms_title),
        modifier = modifier,
        primaryLabel = if (state == SmsRoleState.NotHeld) stringResource(R.string.default_sms_action) else null,
        onPrimary = if (state == SmsRoleState.NotHeld) onRequest else null,
        skipLabel = if (state == SmsRoleState.Held) null else stringResource(R.string.onboarding_skip),
        onSkip = if (state == SmsRoleState.Held) null else onSkip,
        // Tocar el primer punto vuelve al paso 1.
        onStepSelected = { if (it == 0) onBack() },
    ) { textColor ->
        when (state) {
            SmsRoleState.NotHeld, SmsRoleState.Held -> {
                Body(stringResource(R.string.default_sms_explanation_replace), textColor)
                Body(stringResource(R.string.default_sms_explanation_safe), textColor)
            }
            SmsRoleState.Unavailable -> Body(stringResource(R.string.default_sms_unavailable), textColor)
        }
    }
}

@Composable
private fun Body(text: String, color: Color) {
    Text(text, style = SmishingTypography.BodyMedium, color = color)
}
