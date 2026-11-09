package com.app.smishing.ui.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.app.smishing.R
import com.app.smishing.domain.risk.RiskAssessment
import com.app.smishing.domain.risk.RiskLevel
import com.app.smishing.domain.risk.RiskResult
import com.app.smishing.domain.signals.SignalType
import com.app.smishing.ui.designsystem.components.InfoPanel
import com.app.smishing.ui.designsystem.components.OriginalMessageBox
import com.app.smishing.ui.designsystem.components.RiskReasonRow
import com.app.smishing.ui.designsystem.components.SectionLabel
import com.app.smishing.ui.designsystem.components.SmishingTopBar
import com.app.smishing.ui.designsystem.components.StatusBanner
import com.app.smishing.ui.designsystem.components.TopBarStyle
import com.app.smishing.ui.designsystem.icons.SmishingIcons
import com.app.smishing.ui.designsystem.theme.SmishingPalette
import com.app.smishing.ui.designsystem.theme.SmishingTypography
import com.app.smishing.ui.designsystem.theme.Spacing
import com.app.smishing.ui.designsystem.theme.Status

/** Lo que muestra el detalle de un mensaje. */
sealed interface MessageDetailState {
    /** Leyendo el mensaje del buzón y evaluándolo. */
    data object Loading : MessageDetailState

    /** El mensaje ya no está en el buzón. */
    data object Missing : MessageDetailState

    data class Loaded(val body: String, val result: RiskResult) : MessageDetailState
}

/**
 * "Detalle del análisis" (#10): el nivel de riesgo, el mensaje original y por qué se avisó, con el diseño del detalle.
 *
 * **El mensaje se muestra como texto plano: ningún enlace de su texto se activa** (es lo que pide #10), y así debe seguir.
 *
 * El lenguaje es el de #11: palabras corrientes, cada señal con la parte del mensaje que la motivó, y sin presentar la sospecha
 * como certeza (se dice lo que se sabe y lo que no, y que el análisis puede equivocarse). **Nunca se muestra el puntaje del
 * modelo ni porcentajes.**
 *
 * Todavía no tiene las acciones automáticas ni "Este mensaje es seguro" (#13 a #16).
 */
@Composable
fun MessageDetailScreen(state: MessageDetailState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().background(SmishingPalette.Background)) {
        SmishingTopBar(
            title = stringResource(R.string.detail_title),
            style = TopBarStyle.Primary,
            onBack = onBack,
            backDescription = stringResource(R.string.conversation_back),
        )
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.Md),
            verticalArrangement = Arrangement.spacedBy(Spacing.Md),
        ) {
            when (state) {
                MessageDetailState.Loading -> Unit
                MessageDetailState.Missing -> Text(
                    stringResource(R.string.detail_missing),
                    style = SmishingTypography.BodyMedium,
                    color = SmishingPalette.TextSecondary,
                )
                is MessageDetailState.Loaded -> Loaded(state)
            }
        }
    }
}

@Composable
private fun Loaded(state: MessageDetailState.Loaded) {
    when (val result = state.result) {
        is RiskResult.NotAssessed -> {
            InfoPanel(stringResource(R.string.detail_not_assessed_title), stringResource(R.string.detail_not_assessed))
            Original(state.body)
        }
        is RiskResult.Assessed -> {
            val assessment = result.assessment
            StatusBanner(statusOf(assessment.level), stringResource(bannerText(assessment.level)))
            Text(
                stringResource(summaryText(assessment.level)),
                style = SmishingTypography.BodyMedium,
                color = SmishingPalette.TextPrimary,
            )
            Original(state.body)
            SectionLabel(stringResource(R.string.detail_why_label))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Sm)) { Reasons(assessment) }
            Text(
                stringResource(R.string.detail_disclaimer),
                style = SmishingTypography.Note,
                color = SmishingPalette.TextSecondary,
            )
            if (assessment.level != RiskLevel.Low) {
                InfoPanel(stringResource(R.string.detail_recommendation_title), stringResource(R.string.detail_recommendation))
            }
        }
    }
}

@Composable
private fun Original(body: String) {
    SectionLabel(stringResource(R.string.detail_original_label))
    // Texto plano: no se vuelve enlace ni se puede tocar para abrirlo.
    OriginalMessageBox(body)
}

@Composable
private fun Reasons(assessment: RiskAssessment) {
    val types = assessment.signals.types
    if (types.isEmpty()) {
        val text = if (assessment.model.isSmishing) R.string.detail_model_only else R.string.detail_no_signals
        Text(stringResource(text), style = SmishingTypography.BodyMedium, color = SmishingPalette.TextSecondary)
        return
    }
    for (type in types) {
        // Lo que dijo el mensaje y motivó la señal, entre comillas: así se ve de dónde sale el aviso.
        val fragment = assessment.signals.signals.first { it.type == type }.fragment
        RiskReasonRow(
            icon = iconOf(type),
            text = stringResource(reasonText(type)),
            detail = stringResource(R.string.detail_found_in_message, fragment),
        )
    }
}

private fun statusOf(level: RiskLevel) = when (level) {
    RiskLevel.High -> Status.Danger
    RiskLevel.Medium -> Status.Suspicious
    RiskLevel.Low -> Status.Safe
}

private fun bannerText(level: RiskLevel) = when (level) {
    RiskLevel.High -> R.string.detail_banner_high
    RiskLevel.Medium -> R.string.detail_banner_medium
    RiskLevel.Low -> R.string.detail_banner_low
}

private fun summaryText(level: RiskLevel) = when (level) {
    RiskLevel.High -> R.string.detail_summary_high
    RiskLevel.Medium -> R.string.detail_summary_medium
    RiskLevel.Low -> R.string.detail_summary_low
}

private fun reasonText(type: SignalType) = when (type) {
    SignalType.Urgency -> R.string.detail_reason_urgency
    SignalType.Impersonation -> R.string.detail_reason_impersonation
    SignalType.Link -> R.string.detail_reason_link
    SignalType.DataRequest -> R.string.detail_reason_data_request
}

private fun iconOf(type: SignalType): ImageVector = when (type) {
    SignalType.Urgency -> SmishingIcons.Schedule
    SignalType.Impersonation -> SmishingIcons.Institution
    SignalType.Link -> SmishingIcons.Link
    SignalType.DataRequest -> SmishingIcons.Key
}
