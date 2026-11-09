package com.app.smishing.ui.alerts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.app.smishing.PlainLanguage
import com.app.smishing.R
import com.app.smishing.domain.classification.ClassificationResult
import com.app.smishing.domain.classification.ClassificationResult.NotClassified.Reason
import com.app.smishing.domain.risk.RiskPolicy
import com.app.smishing.domain.risk.RiskResult
import com.app.smishing.domain.signals.DetectedSignal
import com.app.smishing.domain.signals.SignalAnalysis
import com.app.smishing.domain.signals.SignalType
import com.app.smishing.ui.designsystem.theme.SmishingTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** "Detalle del análisis" (#10): el nivel, el mensaje original como texto plano y por qué se avisó. */
@RunWith(AndroidJUnit4::class)
class MessageDetailScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)
    private fun count(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().size

    private val body = "Estimado cliente, su tarjeta ha sido suspendida. Ingrese a ejemplo.invalid para reactivarla."

    /** El trozo del mensaje que motivó cada señal, distinto para cada tipo. */
    private fun fragmentOf(type: SignalType) = "trozo-${type.name.lowercase()}"

    private fun assessed(flagged: Boolean, vararg types: SignalType, score: Float = if (flagged) 0.97f else 0.1f) = RiskResult.Assessed(
        RiskPolicy.decide(
            ClassificationResult.Classified(score, flagged, 0.9, "1.0.0", "1.1.0"),
            SignalAnalysis("1.1.0", types.map { DetectedSignal(it, "test.${it.name.lowercase()}", fragmentOf(it)) }),
        ),
    )

    private fun show(state: MessageDetailState, onBack: () -> Unit = {}) {
        compose.setContent { SmishingTheme { MessageDetailScreen(state, onBack) } }
    }

    @Test
    fun aHighRiskMessageShowsThePossibleScamBannerTheMessageTheReasonsAndTheRecommendation() {
        show(MessageDetailState.Loaded(body, assessed(true, SignalType.Urgency, SignalType.Impersonation, SignalType.Link, SignalType.DataRequest)))

        compose.onNodeWithText(text(R.string.detail_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.detail_banner_high)).assertIsDisplayed()
        compose.onNodeWithText(body).assertIsDisplayed()
        for (id in listOf(R.string.detail_reason_urgency, R.string.detail_reason_impersonation, R.string.detail_reason_link, R.string.detail_reason_data_request)) {
            compose.onNodeWithText(text(id)).assertIsDisplayed()
        }
        compose.onNodeWithText(text(R.string.detail_recommendation)).assertIsDisplayed()
    }

    @Test
    fun everyDetectedSignalIsNamedAndShowsThePartOfTheMessageThatCausedIt() {
        val all = arrayOf(SignalType.Urgency, SignalType.Impersonation, SignalType.Link, SignalType.DataRequest)
        show(MessageDetailState.Loaded(body, assessed(true, *all)))

        for (type in all) {
            compose.onNodeWithText(context.getString(R.string.detail_found_in_message, fragmentOf(type))).assertIsDisplayed()
        }
        compose.onNodeWithText(text(R.string.detail_why_label).uppercase()).assertIsDisplayed()
    }

    @Test
    fun onlyTheSignalsThatWereFoundAreNamed() {
        show(MessageDetailState.Loaded(body, assessed(true, SignalType.Urgency)))

        compose.onNodeWithText(text(R.string.detail_reason_urgency)).assertIsDisplayed()
        for (id in listOf(R.string.detail_reason_impersonation, R.string.detail_reason_link, R.string.detail_reason_data_request)) {
            assertEquals(0, count(text(id)))
        }
    }

    @Test
    fun theSummaryTellsWhatIsKnownAndWhatIsNotAtEveryLevel() {
        show(MessageDetailState.Loaded(body, assessed(true, SignalType.Urgency)))
        compose.onNodeWithText(text(R.string.detail_summary_high)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.detail_disclaimer)).assertIsDisplayed()
    }

    @Test
    fun theMediumAndLowSummariesAdmitDoubtToo() {
        show(MessageDetailState.Loaded(body, assessed(true)))
        compose.onNodeWithText(text(R.string.detail_summary_medium)).assertIsDisplayed()
    }

    @Test
    fun aLowRiskSummaryDoesNotPromiseTheMessageIsSafe() {
        show(MessageDetailState.Loaded(body, assessed(false)))
        compose.onNodeWithText(text(R.string.detail_summary_low)).assertIsDisplayed()
        assertTrue(text(R.string.detail_summary_low).contains("no garantiza"))
    }

    @Test
    fun noScoreOrPercentageIsEverShownAndTheTextsFollowThePlainLanguageRules() {
        val levels = listOf(
            assessed(true, SignalType.Urgency, SignalType.Impersonation, SignalType.Link, SignalType.DataRequest, score = 0.9731f),
            assessed(true, score = 0.9412f),
            assessed(false, score = 0.0387f),
        )
        var current by mutableStateOf<RiskResult>(levels.first())
        compose.setContent { SmishingTheme { MessageDetailScreen(MessageDetailState.Loaded(body, current), onBack = {}) } }

        for (result in levels) {
            current = result
            compose.waitForIdle()
            val visible = visibleTexts().filter { it != body }
            assertTrue("Debía haber textos", visible.isNotEmpty())
            for (shown in visible) {
                assertEquals(shown, emptyList<String>(), PlainLanguage.violations(shown))
                for (score in listOf("0,97", "0.97", "97", "0,94", "94", "0,03", "0.03", "03")) {
                    assertTrue("Se mostró el puntaje en «$shown»", score !in shown)
                }
            }
        }
    }

    private fun visibleTexts(): List<String> {
        fun walk(node: androidx.compose.ui.semantics.SemanticsNode): List<String> =
            (node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text).orEmpty().map { it.text }) + node.children.flatMap(::walk)
        return walk(compose.onRoot(useUnmergedTree = true).fetchSemanticsNode())
    }

    @Test
    fun theBannerDoesNotPresentTheResultAsACertainty() {
        show(MessageDetailState.Loaded(body, assessed(true, SignalType.Urgency)))
        // #11 pide no presentar la probabilidad como certeza: "posible", no "peligroso".
        assertEquals(0, count("Mensaje Peligroso"))
        compose.onNodeWithText(text(R.string.detail_banner_high)).assertIsDisplayed()
    }

    @Test
    fun theOriginalMessageIsPlainTextAndNoLinkInItCanBeActivated() {
        show(MessageDetailState.Loaded(body, assessed(true, SignalType.Urgency, SignalType.Link)))

        compose.onNodeWithText(body).assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnLongClick))
        // Tocar el texto no hace nada: el detalle sigue ahí.
        compose.onNodeWithText(body).performClick()
        compose.onNodeWithText(text(R.string.detail_title)).assertIsDisplayed()
    }

    @Test
    fun aMediumMessageWithOnlyTheModelSaysSoInsteadOfInventingASignal() {
        show(MessageDetailState.Loaded(body, assessed(true)))

        compose.onNodeWithText(text(R.string.detail_banner_medium)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.detail_model_only)).assertIsDisplayed()
    }

    @Test
    fun aLowRiskMessageHasNoRecommendationToAvoidIt() {
        show(MessageDetailState.Loaded(body, assessed(false)))

        compose.onNodeWithText(text(R.string.detail_banner_low)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.detail_no_signals)).assertIsDisplayed()
        assertEquals(0, count(text(R.string.detail_recommendation)))
    }

    @Test
    fun aMessageTheModelDidNotEvaluateSaysSoAndStillShowsTheMessage() {
        show(MessageDetailState.Loaded("Hola", RiskResult.NotAssessed(Reason.TOO_SHORT)))

        compose.onNodeWithText(text(R.string.detail_not_assessed)).assertIsDisplayed()
        compose.onNodeWithText("Hola").assertIsDisplayed()
    }

    @Test
    fun aMessageThatIsNoLongerInTheInboxSaysSo() {
        show(MessageDetailState.Missing)
        compose.onNodeWithText(text(R.string.detail_missing)).assertIsDisplayed()
    }

    @Test
    fun theBackArrowGoesBack() {
        var back = 0
        show(MessageDetailState.Loading, onBack = { back++ })
        compose.onNodeWithContentDescription(text(R.string.conversation_back)).performClick()
        assertEquals(1, back)
    }
}
