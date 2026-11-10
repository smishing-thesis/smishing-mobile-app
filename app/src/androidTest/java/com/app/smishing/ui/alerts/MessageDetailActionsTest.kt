package com.app.smishing.ui.alerts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.app.smishing.PlainLanguage
import com.app.smishing.R
import com.app.smishing.domain.classification.ClassificationResult
import com.app.smishing.domain.mitigation.ActionNote
import com.app.smishing.domain.mitigation.ActionStatus
import com.app.smishing.domain.mitigation.ActionType
import com.app.smishing.domain.mitigation.MitigationAction
import com.app.smishing.domain.risk.RiskPolicy
import com.app.smishing.domain.risk.RiskResult
import com.app.smishing.domain.signals.DetectedSignal
import com.app.smishing.domain.signals.SignalAnalysis
import com.app.smishing.domain.signals.SignalType
import com.app.smishing.ui.designsystem.theme.SmishingTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Las acciones en el detalle (#14 a #16, #28): lo hecho, lo fallido, los botones y "Marcar como seguro". */
@RunWith(AndroidJUnit4::class)
class MessageDetailActionsTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)
    private fun count(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().size

    private val body = "Mensaje sintético con http://ejemplo.invalid"
    private fun result(flagged: Boolean = true) = RiskResult.Assessed(
        RiskPolicy.decide(
            ClassificationResult.Classified(if (flagged) 0.97f else 0.1f, flagged, 0.9, "1.0.0", "1.1.0"),
            SignalAnalysis("1.1.0", listOf(DetectedSignal(SignalType.Urgency, "urgency.test", "fragmento"))),
        ),
    )

    private var nextId = 0L
    private fun action(type: ActionType, status: ActionStatus, note: ActionNote? = null) =
        MitigationAction(++nextId, 1, type, status, note, 1, 1)

    private fun protection(vararg actions: MitigationAction, markedSafe: Boolean = false, canAct: Boolean = true) =
        Protection(markedSafe, actions.associateBy { it.type }, canAct)

    private var state by mutableStateOf<MessageDetailState>(MessageDetailState.Loading)
    private val calls = mutableListOf<String>()

    private fun show(protection: Protection?, risk: RiskResult = result()) {
        state = MessageDetailState.Loaded(body, risk, protection)
        compose.setContent {
            SmishingTheme {
                MessageDetailScreen(
                    state = state,
                    onBack = {},
                    actions = DetailActions({ calls += "block" }, { calls += "remove" }, { calls += "safe" }),
                )
            }
        }
    }

    @Test
    fun withoutProtectionDataItShowsNoActionsAtAll() {
        show(null)
        assertEquals(0, count(text(R.string.detail_button_block)))
        assertEquals(0, count(text(R.string.detail_button_mark_safe)))
        assertEquals(0, count(text(R.string.detail_actions_label).uppercase()))
    }

    @Test
    fun completedActionsAreShownAsDoneAndOnlyMarkAsSafeIsOffered() {
        show(protection(action(ActionType.BlockSender, ActionStatus.Completed), action(ActionType.RemoveMessage, ActionStatus.Completed)))
        compose.onNodeWithText(text(R.string.detail_action_block_done)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.detail_action_remove_done)).performScrollTo().assertIsDisplayed()
        assertEquals(0, count(text(R.string.detail_button_block)))
        assertEquals(0, count(text(R.string.detail_button_remove)))
        compose.onNodeWithText(text(R.string.detail_button_mark_safe)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aFailedActionIsNeverShownAsDoneAndItsCauseIsExplained() {
        show(protection(action(ActionType.BlockSender, ActionStatus.Failed, ActionNote.NotSupported)))
        compose.onNodeWithText(text(R.string.detail_action_block_failed)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.detail_failure_not_supported)).performScrollTo().assertIsDisplayed()
        assertEquals("No debe figurar como hecha", 0, count(text(R.string.detail_action_block_done)))
        // Se puede reintentar.
        compose.onNodeWithText(text(R.string.detail_button_block)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun eachFailureCauseHasItsOwnExplanation() {
        val causes = mapOf(
            ActionNote.NotSupported to R.string.detail_failure_not_supported,
            ActionNote.NotAllowed to R.string.detail_failure_not_allowed,
            ActionNote.NoLocalCopy to R.string.detail_failure_no_copy,
            ActionNote.Error to R.string.detail_failure_error,
        )
        assertEquals(causes.size, causes.values.map { text(it) }.toSet().size)
        show(protection(action(ActionType.RemoveMessage, ActionStatus.Failed, ActionNote.NoLocalCopy)))
        compose.onNodeWithText(text(R.string.detail_action_remove_failed)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.detail_failure_no_copy)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aRequestedActionIsShownAsInProgressNotAsDone() {
        show(protection(action(ActionType.BlockSender, ActionStatus.Requested)))
        compose.onNodeWithText(text(R.string.detail_action_block_pending)).performScrollTo().assertIsDisplayed()
        assertEquals(0, count(text(R.string.detail_action_block_done)))
    }

    @Test
    fun anActionThatCouldNotBeRevertedIsStillShownAsInEffectWithTheWarning() {
        show(protection(action(ActionType.BlockSender, ActionStatus.Completed, ActionNote.RevertFailed)))
        compose.onNodeWithText(text(R.string.detail_action_block_done)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.detail_revert_failed_block)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun inAskModeTheMissingActionsAreOfferedAndTappingReportsThem() {
        show(protection())
        compose.onNodeWithText(text(R.string.detail_button_block)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.detail_button_remove)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.detail_button_mark_safe)).performScrollTo().performClick()
        assertEquals(listOf("block", "remove", "safe"), calls)
    }

    @Test
    fun whenTheModeDoesNotAllowActingNoActionButtonIsOffered() {
        show(protection(canAct = false))
        assertEquals(0, count(text(R.string.detail_button_block)))
        assertEquals(0, count(text(R.string.detail_button_remove)))
    }

    @Test
    fun aMediumRiskMessageOffersMarkAsSafeButNeverTheActionButtons() {
        show(protection(canAct = false), result(flagged = true).let { RiskResult.Assessed(RiskPolicy.decide(it.assessment.model, SignalAnalysis("1.1.0", emptyList()))) })
        compose.onNodeWithText(text(R.string.detail_button_mark_safe)).performScrollTo().assertIsDisplayed()
        assertEquals(0, count(text(R.string.detail_button_block)))
    }

    @Test
    fun aMessageMarkedAsSafeShowsTheDecisionAsTheirsAndWhatWasReverted() {
        show(
            protection(
                action(ActionType.BlockSender, ActionStatus.Reverted),
                action(ActionType.RemoveMessage, ActionStatus.Reverted),
                markedSafe = true,
            ),
        )
        compose.onNodeWithText(text(R.string.detail_marked_safe_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.detail_marked_safe_text)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.detail_reverted_block)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.detail_reverted_remove)).performScrollTo().assertIsDisplayed()
        // Ya no hay aviso de riesgo ni "Marcar como seguro", pero sí se puede volver a actuar.
        assertEquals(0, count(text(R.string.detail_banner_high)))
        assertEquals(0, count(text(R.string.detail_button_mark_safe)))
        compose.onNodeWithText(text(R.string.detail_button_block)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun everyTextOnTheScreenKeepsTheLanguageRules() {
        show(
            protection(
                action(ActionType.BlockSender, ActionStatus.Failed, ActionNote.NotSupported),
                action(ActionType.RemoveMessage, ActionStatus.Completed, ActionNote.RevertFailed),
            ),
        )
        val strings = listOf(
            R.string.detail_actions_label, R.string.detail_action_block_done, R.string.detail_action_block_already, R.string.detail_action_remove_done,
            R.string.detail_action_block_pending, R.string.detail_action_remove_pending, R.string.detail_action_block_failed,
            R.string.detail_action_remove_failed, R.string.detail_failure_not_supported, R.string.detail_failure_not_allowed,
            R.string.detail_failure_no_copy, R.string.detail_failure_error, R.string.detail_revert_failed_block,
            R.string.detail_revert_failed_remove, R.string.detail_button_block, R.string.detail_button_remove, R.string.detail_button_mark_safe,
            R.string.detail_marked_safe_title, R.string.detail_marked_safe_text, R.string.detail_reverted_label, R.string.detail_reverted_block,
            R.string.detail_reverted_block_already, R.string.detail_reverted_remove,
        )
        for (id in strings) assertEquals(text(id), emptyList<String>(), PlainLanguage.violations(text(id)))
    }
}
