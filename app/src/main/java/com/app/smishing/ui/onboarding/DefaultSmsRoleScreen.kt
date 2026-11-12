package com.app.smishing.ui.onboarding

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.app.smishing.R
import com.app.smishing.domain.sms.SmsRoleState
import com.app.smishing.ui.designsystem.components.OnboardingStepLayout
import com.app.smishing.ui.designsystem.theme.SmishingPalette
import com.app.smishing.ui.designsystem.theme.SmishingTypography

/**
 * Paso 2 del onboarding: explica por qué la app necesita ser la de mensajería predeterminada antes de pedirlo, y
 * muestra el resultado. [onRequest] abre el diálogo del sistema; [onSkip] sigue sin el rol.
 */
@Composable
fun DefaultSmsRoleScreen(
    state: SmsRoleState,
    onRequest: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingStepLayout(
        stepIndex = 1,
        stepCount = 3,
        stepLabel = stringResource(R.string.onboarding_step_two_of_three),
        title = stringResource(R.string.default_sms_title),
        modifier = modifier,
        primaryLabel = if (state == SmsRoleState.NotHeld) stringResource(R.string.default_sms_action) else null,
        onPrimary = if (state == SmsRoleState.NotHeld) onRequest else null,
        skipLabel = if (state == SmsRoleState.Held) null else stringResource(R.string.onboarding_skip),
        onSkip = if (state == SmsRoleState.Held) null else onSkip,
    ) {
        when (state) {
            SmsRoleState.NotHeld, SmsRoleState.Held -> {
                Body(stringResource(R.string.default_sms_explanation_replace))
                Body(stringResource(R.string.default_sms_explanation_safe))
            }
            SmsRoleState.Unavailable -> Body(stringResource(R.string.default_sms_unavailable))
        }
    }
}

@Composable
private fun Body(text: String) {
    Text(text, style = SmishingTypography.BodyLarge, color = SmishingPalette.TextPrimary)
}
