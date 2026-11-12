package com.app.smishing.ui.onboarding

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.app.smishing.R
import com.app.smishing.domain.sms.SmsPermissionState
import com.app.smishing.ui.designsystem.components.OnboardingStepLayout
import com.app.smishing.ui.designsystem.theme.SmishingPalette
import com.app.smishing.ui.designsystem.theme.SmishingTypography

/**
 * Pantalla de recuperación (antes, el paso 3 del onboarding): pide el permiso para leer los mensajes, después del rol de SMS. [blocked] es verdadero cuando
 * Android ya no muestra la solicitud (se rechazó dos veces): entonces el botón lleva a los ajustes de la app.
 */
@Composable
fun SmsPermissionScreen(
    state: SmsPermissionState,
    blocked: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val granted = state == SmsPermissionState.Granted
    OnboardingStepLayout(
        // Sin "PASO N DE M": el rol concede este permiso solo, y esta pantalla aparece solo si la persona lo quitó después.
        stepIndex = null,
        stepCount = null,
        stepLabel = null,
        title = stringResource(R.string.sms_permission_title),
        modifier = modifier,
        primaryLabel = when {
            granted -> null
            blocked -> stringResource(R.string.sms_permission_open_settings)
            else -> stringResource(R.string.sms_permission_action)
        },
        onPrimary = when {
            granted -> null
            blocked -> onOpenSettings
            else -> onRequest
        },
        skipLabel = if (granted) null else stringResource(R.string.onboarding_skip_short),
        onSkip = if (granted) null else onSkip,
    ) {
        when {
            blocked -> Body(stringResource(R.string.sms_permission_blocked_message))
            else -> {
                Body(stringResource(R.string.sms_permission_explanation_read))
                Body(stringResource(R.string.sms_permission_explanation_privacy))
            }
        }
    }
}

@Composable
private fun Body(text: String) {
    Text(text, style = SmishingTypography.BodyLarge, color = SmishingPalette.TextPrimary)
}
