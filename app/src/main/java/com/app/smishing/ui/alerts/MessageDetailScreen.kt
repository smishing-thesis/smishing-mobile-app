package com.app.smishing.ui.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.app.smishing.R
import com.app.smishing.domain.mitigation.ActionNote
import com.app.smishing.domain.mitigation.ActionStatus
import com.app.smishing.domain.mitigation.ActionType
import com.app.smishing.domain.mitigation.MitigationAction
import com.app.smishing.domain.risk.Recommendation
import com.app.smishing.domain.risk.RiskAssessment
import com.app.smishing.domain.risk.RiskLevel
import com.app.smishing.domain.risk.RiskResult
import com.app.smishing.domain.signals.SignalType
import com.app.smishing.ui.designsystem.components.ActionDoneRow
import com.app.smishing.ui.designsystem.components.ConfirmDialog
import com.app.smishing.ui.designsystem.components.InfoPanel
import com.app.smishing.ui.designsystem.components.PrimaryButton
import com.app.smishing.ui.designsystem.components.SecondaryButton
import com.app.smishing.ui.designsystem.components.OriginalMessageBox
import com.app.smishing.ui.designsystem.components.RiskReasonRow
import com.app.smishing.ui.designsystem.components.SectionLabel
import com.app.smishing.ui.designsystem.components.SmishingTopBar
import com.app.smishing.ui.designsystem.components.StatusBanner
import com.app.smishing.ui.designsystem.components.coachmarkTarget
import com.app.smishing.ui.tutorial.TutorialTarget
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

    data class Loaded(val body: String, val result: RiskResult, val protection: Protection? = null) : MessageDetailState
}

/**
 * Lo que se sabe de las acciones sobre el mensaje (#14 a #16, #28): [actions] es la más reciente de cada tipo. Los botones de actuar
 * se ofrecen para cualquier mensaje con análisis, con cualquier modo: tocarlos es la decisión de la persona (#29).
 */
data class Protection(
    val markedSafe: Boolean,
    val actions: Map<ActionType, MitigationAction>,
    /** El modo de mitigación es el automático: tras revertir, el detalle ofrece pasar a "Preguntarme antes de actuar". */
    val automatic: Boolean = false,
)

/** Lo que la persona puede pedir desde el detalle. */
data class DetailActions(
    val onBlock: () -> Unit = {},
    val onRemove: () -> Unit = {},
    val onMarkSafe: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
)

/**
 * "Detalle del análisis" (#10): el nivel de riesgo, el mensaje original y por qué se avisó, con el diseño del detalle.
 *
 * **El mensaje se muestra como texto plano: ningún enlace de su texto se activa** (es lo que pide #10), y así debe seguir.
 *
 * El lenguaje es el de #11: palabras corrientes, cada señal con la parte del mensaje que la motivó, y sin presentar la sospecha
 * como certeza (se dice lo que se sabe y lo que no, y que el análisis puede equivocarse). **Nunca se muestra el puntaje del
 * modelo ni porcentajes.**
 *
 * Con [MessageDetailState.Loaded.protection] muestra las acciones tomadas con su estado (una acción fallida no figura como hecha),
 * los botones para actuar si la configuración lo permite y "Marcar como seguro", que las deshace (#14 a #16, #28). "Marcado como
 * seguro" es lo que **la persona** dice, no lo que la app afirma del mensaje.
 */
@Composable
fun MessageDetailScreen(
    state: MessageDetailState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: DetailActions = DetailActions(),
) {
    Column(modifier = modifier.fillMaxSize().background(SmishingPalette.Background).navigationBarsPadding()) {
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
                is MessageDetailState.Loaded -> Loaded(state, actions)
            }
        }
    }
}

@Composable
private fun Loaded(state: MessageDetailState.Loaded, actions: DetailActions) {
    when (val result = state.result) {
        is RiskResult.NotAssessed -> {
            InfoPanel(stringResource(R.string.detail_not_assessed_title), stringResource(R.string.detail_not_assessed))
            Original(state.body)
        }
        is RiskResult.Assessed -> if (state.protection?.markedSafe == true) {
            MarkedSafe(state, state.protection, actions)
        } else {
            val assessment = result.assessment
            var confirming by remember { mutableStateOf(false) }
            StatusBanner(
                statusOf(assessment.level),
                stringResource(bannerText(assessment.level)),
                modifier = Modifier.coachmarkTarget(TutorialTarget.DetailBanner),
            )
            Original(state.body)
            SectionLabel(stringResource(R.string.detail_why_label))
            Column(
                modifier = Modifier.coachmarkTarget(TutorialTarget.DetailReasons),
                verticalArrangement = Arrangement.spacedBy(Spacing.Sm),
            ) { Reasons(assessment) }
            Text(
                stringResource(R.string.detail_disclaimer),
                style = SmishingTypography.Note,
                color = SmishingPalette.TextSecondary,
            )
            state.protection?.let { ActionsSection(it, actions) }
            if (assessment.level != RiskLevel.Low) {
                InfoPanel(
                    stringResource(R.string.detail_recommendation_title),
                    stringResource(recommendationText(Recommendation.of(assessment.signals.types))),
                )
            }
            if (state.protection != null && assessment.level != RiskLevel.Low) {
                SecondaryButton(
                    stringResource(R.string.detail_button_mark_safe),
                    onClick = { confirming = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (confirming) {
                // "¿Está seguro?": marcar como seguro desbloquea al número y devuelve el mensaje, así que se confirma antes.
                ConfirmDialog(
                    title = stringResource(R.string.detail_confirm_title),
                    message = stringResource(R.string.detail_confirm_text),
                    confirmLabel = stringResource(R.string.detail_confirm_yes),
                    onConfirm = {
                        confirming = false
                        actions.onMarkSafe()
                    },
                    dismissLabel = stringResource(R.string.detail_confirm_cancel),
                    onDismiss = { confirming = false },
                )
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

private fun recommendationText(recommendation: Recommendation) = when (recommendation) {
    Recommendation.LinkAndData -> R.string.detail_recommendation_link_and_data
    Recommendation.Link -> R.string.detail_recommendation_link
    Recommendation.Data -> R.string.detail_recommendation_data
    Recommendation.General -> R.string.detail_recommendation_general
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

/** El mensaje que la persona marcó como seguro: el diseño de "acciones revertidas", con los botones para volver a actuar. */
@Composable
private fun MarkedSafe(state: MessageDetailState.Loaded, protection: Protection, actions: DetailActions) {
    StatusBanner(Status.Safe, stringResource(R.string.detail_marked_safe_title))
    Text(stringResource(R.string.detail_marked_safe_text), style = SmishingTypography.BodyMedium, color = SmishingPalette.TextPrimary)
    Original(state.body)
    val reverted = protection.actions.values.filter { it.status == ActionStatus.Reverted }
    val stuck = protection.actions.values.filter { it.note == ActionNote.RevertFailed }
    if (reverted.isNotEmpty() || stuck.isNotEmpty()) {
        SectionLabel(stringResource(R.string.detail_reverted_label))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.X2s)) {
            for (action in reverted) ActionDoneRow(stringResource(revertedText(action)))
            for (action in stuck) ActionIssueRow(stringResource(revertFailedText(action.type)), null)
        }
    }
    if (protection.automatic) {
        // El diseño de "acciones revertidas": se sugiere el modo en que siempre se pide consentimiento.
        InfoPanel(stringResource(R.string.detail_decide_title), stringResource(R.string.detail_decide_text), uppercaseTitle = false)
        PrimaryButton(stringResource(R.string.detail_go_settings), onClick = actions.onOpenSettings, modifier = Modifier.fillMaxWidth())
    }
    ActionButtons(protection, actions)
}

/** Las acciones tomadas y, debajo, los botones para tomar las que faltan (con el título "Acciones" si todavía no se tomó ninguna). */
@Composable
private fun ActionsSection(protection: Protection, actions: DetailActions) {
    if (protection.actions.isNotEmpty()) {
        SectionLabel(stringResource(R.string.detail_actions_label))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.X2s)) {
            for (action in protection.actions.values.sortedBy { it.type.ordinal }) {
                when {
                    action.note == ActionNote.RevertFailed -> {
                        // Se intentó deshacer y no se pudo: la acción sigue vigente.
                        ActionDoneRow(stringResource(doneText(action)))
                        ActionIssueRow(stringResource(revertFailedText(action.type)), null)
                    }
                    action.status == ActionStatus.Completed -> ActionDoneRow(stringResource(doneText(action)))
                    action.status == ActionStatus.Failed ->
                        ActionIssueRow(stringResource(failedText(action.type)), action.note?.let { stringResource(failureReason(it)) })
                    action.status == ActionStatus.Requested -> ActionIssueRow(stringResource(pendingText(action.type)), null)
                }
            }
        }
    }
    if (protection.actions.isEmpty()) SectionLabel(stringResource(R.string.detail_actions_manual_label))
    ActionButtons(protection, actions)
}

/** "Bloquear este número" y "Borrar mensaje", solo las que faltan. */
@Composable
private fun ActionButtons(protection: Protection, actions: DetailActions) {
    fun pending(type: ActionType) = protection.actions[type]?.status != ActionStatus.Completed
    if (pending(ActionType.BlockSender)) {
        // El mockup pone los dos con el mismo peso visual: el principal y el secundario.
        PrimaryButton(stringResource(R.string.detail_button_block), onClick = actions.onBlock, modifier = Modifier.fillMaxWidth())
    }
    if (pending(ActionType.RemoveMessage)) {
        SecondaryButton(stringResource(R.string.detail_button_remove), onClick = actions.onRemove, modifier = Modifier.fillMaxWidth())
    }
}

/** Una acción que no se hizo (o no se sabe si se hizo), con su motivo: nunca con el visto de las hechas. */
@Composable
private fun ActionIssueRow(text: String, reason: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 32.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
    ) {
        Icon(
            SmishingIcons.ErrorOutline,
            contentDescription = null,
            tint = SmishingPalette.SuspiciousOnBackground,
            modifier = Modifier.size(20.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.X2s)) {
            Text(text, style = SmishingTypography.BodyMediumBold, color = SmishingPalette.TextPrimary)
            if (reason != null) Text(reason, style = SmishingTypography.Note, color = SmishingPalette.TextSecondary)
        }
    }
}

private fun doneText(action: MitigationAction) = when (action.type) {
    ActionType.BlockSender ->
        if (action.note == ActionNote.AlreadyApplied) R.string.detail_action_block_already else R.string.detail_action_block_done
    ActionType.RemoveMessage -> R.string.detail_action_remove_done
}

private fun pendingText(type: ActionType) = when (type) {
    ActionType.BlockSender -> R.string.detail_action_block_pending
    ActionType.RemoveMessage -> R.string.detail_action_remove_pending
}

private fun failedText(type: ActionType) = when (type) {
    ActionType.BlockSender -> R.string.detail_action_block_failed
    ActionType.RemoveMessage -> R.string.detail_action_remove_failed
}

private fun revertFailedText(type: ActionType) = when (type) {
    ActionType.BlockSender -> R.string.detail_revert_failed_block
    ActionType.RemoveMessage -> R.string.detail_revert_failed_remove
}

private fun revertedText(action: MitigationAction) = when (action.type) {
    ActionType.BlockSender ->
        if (action.note == ActionNote.AlreadyApplied) R.string.detail_reverted_block_already else R.string.detail_reverted_block
    ActionType.RemoveMessage -> R.string.detail_reverted_remove
}

private fun failureReason(note: ActionNote) = when (note) {
    ActionNote.NotSupported -> R.string.detail_failure_not_supported
    ActionNote.NotAllowed -> R.string.detail_failure_not_allowed
    ActionNote.NoLocalCopy -> R.string.detail_failure_no_copy
    ActionNote.Error, ActionNote.AlreadyApplied, ActionNote.RevertFailed -> R.string.detail_failure_error
}
