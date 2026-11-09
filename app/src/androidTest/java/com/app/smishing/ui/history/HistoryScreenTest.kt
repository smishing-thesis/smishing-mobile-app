package com.app.smishing.ui.history

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.app.smishing.R
import com.app.smishing.domain.classification.ClassificationResult
import com.app.smishing.domain.history.AnalysisRecord
import com.app.smishing.domain.risk.RiskPolicy
import com.app.smishing.domain.signals.DetectedSignal
import com.app.smishing.domain.signals.SignalAnalysis
import com.app.smishing.domain.signals.SignalType
import com.app.smishing.ui.designsystem.theme.SmishingTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.time.ZoneId

/** "Historial" (#12): los mensajes analizados con remitente, fecha, inicio del mensaje y marca de estado. */
@RunWith(AndroidJUnit4::class)
class HistoryScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)
    private val zone = ZoneId.of("America/Lima")
    private fun at(month: Int, day: Int) = LocalDateTime.of(2026, month, day, 12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun record(id: Long, sender: String, body: String, at: Long, flagged: Boolean, vararg types: SignalType) = AnalysisRecord(
        id, id, sender, body, at, at,
        RiskPolicy.decide(
            ClassificationResult.Classified(if (flagged) 0.97f else 0.1f, flagged, 0.9, "1.0.0", "1.1.0"),
            SignalAnalysis("1.1.0", types.map { DetectedSignal(it, "test.${it.name.lowercase()}", "x") }),
        ),
    )

    private val high = record(1, "987 654 321", "Estimado cliente, su tarjeta ha sido suspendida.", at(9, 28), true, SignalType.Urgency)
    private val low = record(2, "Mamá", "Hola hijo, trae fruta para la cena", at(9, 27), false)
    private val medium = record(3, "912 345 678", "Confirma tu código de verificación", at(9, 26), true)

    private fun count(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun showsSenderDateAndSnippetOfEachRecordWithItsStatusMark() {
        compose.setContent { SmishingTheme { HistoryScreen(listOf(high, low, medium), onOpen = {}, zone = zone) } }

        compose.onNodeWithText(text(R.string.history_title)).assertIsDisplayed()
        for (record in listOf(high, low, medium)) {
            compose.onNodeWithText(record.sender).assertIsDisplayed()
            compose.onNodeWithText(record.body).assertIsDisplayed()
        }
        compose.onNodeWithText("28 sep 2026").assertIsDisplayed()
        compose.onNodeWithText("27 sep 2026").assertIsDisplayed()
        compose.onNodeWithText("26 sep 2026").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.history_chip_high)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.history_chip_medium)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.history_chip_low)).assertIsDisplayed()
    }

    @Test
    fun theMarksDoNotClaimCertaintyAsTheDesignWordsDid() {
        compose.setContent { SmishingTheme { HistoryScreen(listOf(high, low), onOpen = {}, zone = zone) } }
        assertEquals(0, count("Peligroso"))
        assertEquals(0, count("Seguro"))
    }

    @Test
    fun tappingARecordOpensItAndTheRowsMeetTheTouchTarget() {
        var opened: AnalysisRecord? = null
        compose.setContent { SmishingTheme { HistoryScreen(listOf(high, low), onOpen = { opened = it }, zone = zone) } }

        compose.onNodeWithText(low.sender).assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText(low.sender).performClick()
        assertEquals(low, opened)
    }

    @Test
    fun anEmptyHistorySaysSoAndLoadingShowsNothingYet() {
        compose.setContent { SmishingTheme { HistoryScreen(emptyList(), onOpen = {}, zone = zone) } }
        compose.onNodeWithText(text(R.string.history_empty)).assertIsDisplayed()
    }

    @Test
    fun whileLoadingNeitherTheListNorTheEmptyMessageIsShown() {
        compose.setContent { SmishingTheme { HistoryScreen(null, onOpen = {}, zone = zone) } }
        assertEquals(0, count(text(R.string.history_empty)))
        compose.onNodeWithText(text(R.string.history_title)).assertIsDisplayed()
    }
}
