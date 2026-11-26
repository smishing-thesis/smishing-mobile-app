package com.app.smishing.ui.tutorial

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.app.smishing.R
import com.app.smishing.domain.classification.ClassificationResult
import com.app.smishing.domain.risk.RiskAssessment
import com.app.smishing.domain.risk.RiskLevel
import com.app.smishing.domain.risk.RiskResult
import com.app.smishing.domain.risk.RiskRule
import com.app.smishing.domain.signals.DetectedSignal
import com.app.smishing.domain.signals.SignalAnalysis
import com.app.smishing.domain.signals.SignalType
import com.app.smishing.domain.tutorial.TutorialPhase
import com.app.smishing.ui.alerts.MessageDetailState
import com.app.smishing.ui.designsystem.components.CoachmarkMessage
import com.app.smishing.ui.designsystem.components.CoachmarkOverlay
import com.app.smishing.ui.designsystem.components.coachmarkTarget
import com.app.smishing.ui.designsystem.theme.Elevation
import com.app.smishing.ui.designsystem.theme.SmishingPalette
import com.app.smishing.ui.designsystem.theme.SmishingShapes
import com.app.smishing.ui.designsystem.theme.SmishingTypography
import com.app.smishing.ui.designsystem.theme.Spacing

/** El recorrido de [controller] sobre la pantalla: la invitación, los pasos y el cierre. Atrás lo cierra sin decidir nada. */
@Composable
fun TutorialOverlay(controller: TutorialController, modifier: Modifier = Modifier) {
    BackHandler(enabled = controller.active, onBack = controller::pause)
    val message = when (val phase = controller.phase) {
        TutorialPhase.Hidden -> null
        TutorialPhase.Invite -> CoachmarkMessage(
            title = stringResource(R.string.tutorial_invite_title),
            body = stringResource(R.string.tutorial_invite_body),
            primaryLabel = stringResource(R.string.tutorial_invite_accept),
            secondaryLabel = stringResource(R.string.tutorial_invite_decline),
            note = stringResource(R.string.tutorial_invite_note),
        )
        is TutorialPhase.Step -> {
            val step = TutorialSteps[phase.index]
            CoachmarkMessage(
                title = stringResource(step.title),
                body = stringResource(step.body),
                primaryLabel = stringResource(if (phase.index == TutorialSteps.lastIndex) R.string.tutorial_finish else R.string.tutorial_next),
                target = step.target,
                counter = stringResource(R.string.tutorial_counter, phase.index + 1, TutorialSteps.size),
                secondaryLabel = stringResource(R.string.tutorial_skip),
            )
        }
        TutorialPhase.Closing -> CoachmarkMessage(
            title = stringResource(R.string.tutorial_closing_title),
            body = stringResource(R.string.tutorial_closing_body),
            primaryLabel = stringResource(R.string.tutorial_closing_button),
        )
    }
    CoachmarkOverlay(message, controller.registry, onPrimary = controller::next, onSecondary = controller::skip, modifier = modifier)
}

/**
 * El aviso de ejemplo del paso de la alerta: como la notificación real pero dentro de la app, con la marca "EJEMPLO". No es una
 * notificación del sistema ni se guarda nada.
 */
@Composable
fun SampleAlertCard(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = Spacing.Sm, vertical = Spacing.Xs)
            .fillMaxWidth()
            .coachmarkTarget(TutorialTarget.SampleAlert)
            .shadow(Elevation.High, SmishingShapes.X3l)
            .background(SmishingPalette.Background, SmishingShapes.X3l)
            .padding(Spacing.Md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
    ) {
        Image(
            painter = painterResource(R.drawable.smishy_active),
            contentDescription = null,
            modifier = Modifier.size(40.dp).background(SmishingPalette.PrimaryLight, CircleShape).padding(6.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.tutorial_sample_app), style = SmishingTypography.Micro, color = SmishingPalette.TextSecondary)
                Text(
                    stringResource(R.string.tutorial_sample_tag),
                    style = SmishingTypography.Micro,
                    color = SmishingPalette.TextSecondary,
                    modifier = Modifier.border(1.dp, SmishingPalette.Border, SmishingShapes.Sm).padding(horizontal = 6.dp),
                )
            }
            Text(stringResource(R.string.alert_title), style = SmishingTypography.BodyMediumBold, color = SmishingPalette.TextPrimary)
            Text(stringResource(R.string.tutorial_sample_alert_text), style = SmishingTypography.Note, color = SmishingPalette.TextSecondary)
        }
    }
}

/**
 * El detalle de un mensaje de ejemplo, para los pasos que explican cómo leerlo. Es la misma pantalla del detalle real, con un análisis
 * armado a mano: no pasa por el modelo, no se guarda y no tiene botones para actuar. Todo en él es inventado.
 */
@Composable
fun sampleDetailState(): MessageDetailState.Loaded {
    val body = stringResource(R.string.tutorial_sample_body)
    return MessageDetailState.Loaded(
        body = body,
        result = RiskResult.Assessed(
            RiskAssessment(
                level = RiskLevel.High,
                rule = RiskRule.ModelAndEvidence,
                model = ClassificationResult.Classified(score = 0.97f, isSmishing = true, threshold = 0.5, modelVersion = SAMPLE, preprocessingVersion = SAMPLE),
                signals = SignalAnalysis(
                    rulesVersion = SAMPLE,
                    signals = listOf(
                        DetectedSignal(SignalType.Urgency, SAMPLE, stringResource(R.string.tutorial_sample_urgency)),
                        DetectedSignal(SignalType.Link, SAMPLE, stringResource(R.string.tutorial_sample_link)),
                        DetectedSignal(SignalType.DataRequest, SAMPLE, stringResource(R.string.tutorial_sample_data)),
                    ),
                ),
                evidence = setOf(SignalType.Urgency, SignalType.DataRequest),
                policyVersion = SAMPLE,
            ),
        ),
    )
}

private const val SAMPLE = "tutorial"
