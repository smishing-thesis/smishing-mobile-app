package com.app.smishing.domain.alerts

import com.app.smishing.domain.classification.ClassificationResult
import com.app.smishing.domain.classification.MessageClassifier
import com.app.smishing.domain.history.AnalysisHistory
import com.app.smishing.domain.history.AnalysisRecord
import com.app.smishing.domain.risk.RiskAssessor
import com.app.smishing.domain.risk.RiskLevel
import com.app.smishing.domain.signals.DetectedSignal
import com.app.smishing.domain.signals.SignalAnalysis
import com.app.smishing.domain.signals.SignalDetector
import com.app.smishing.domain.signals.SignalType
import com.app.smishing.domain.sms.IncomingSms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyzeReceivedMessageTest {

    private class FakeClassifier(private val result: ClassificationResult) : MessageClassifier {
        override fun classify(message: String) = result
    }

    private class FakeDetector(private vararg val types: SignalType) : SignalDetector {
        override fun detect(message: String) =
            SignalAnalysis("1.1.0", types.map { DetectedSignal(it, "test.${it.name.lowercase()}", "fragmento") })
    }

    private class FakeHistory(private val failWith: Exception? = null) : AnalysisHistory {
        val saved = mutableListOf<AnalysisRecord>()
        override fun save(record: AnalysisRecord): Long {
            failWith?.let { throw it }
            saved += record
            return saved.size.toLong()
        }
        override fun all() = saved.reversed()
        override fun byId(id: Long) = saved.getOrNull(id.toInt() - 1)
        override fun byMessageId(messageId: Long) = saved.lastOrNull { it.messageId == messageId }
    }

    private val flagged = ClassificationResult.Classified(0.97f, true, 0.9, "1.0.0", "1.1.0")
    private val notFlagged = ClassificationResult.Classified(0.1f, false, 0.9, "1.0.0", "1.1.0")
    private val message = IncomingSms("+10000000001", "Texto del mensaje con http://ejemplo.invalid", 1_000L)

    private val alerts = mutableListOf<RiskAlert>()
    private val history = FakeHistory()
    private fun analyze(classification: ClassificationResult, vararg types: SignalType, history: AnalysisHistory = this.history, errors: MutableList<Throwable> = mutableListOf()) =
        AnalyzeReceivedMessage(
            assessor = RiskAssessor(FakeClassifier(classification), FakeDetector(*types)),
            history = history,
            notifier = { alerts += it },
            clock = { 5_000L },
            onHistoryError = { errors += it },
        ).onReceived(messageId = 42, message = message)

    // --- La alerta (#10) ---

    @Test
    fun aHighRiskMessageAlertsWithItsIdSenderAndReason() {
        assertEquals(AnalysisOutcome.Alerted, analyze(flagged, SignalType.Urgency, SignalType.Link))
        assertEquals(listOf(RiskAlert(42, "+10000000001", AlertReason.Urgency)), alerts)
    }

    @Test
    fun onlyHighAlerts() {
        assertEquals(setOf(RiskLevel.High), AlertPolicy.ALERT_LEVELS)
        assertEquals(AnalysisOutcome.Recorded, analyze(flagged, SignalType.Link)) // medio: el modelo sin respaldo
        assertEquals(AnalysisOutcome.Recorded, analyze(notFlagged, SignalType.Impersonation)) // medio: solo señales
        assertEquals(AnalysisOutcome.Recorded, analyze(notFlagged)) // bajo
        assertTrue("Medio y bajo no deben avisar", alerts.isEmpty())
    }

    @Test
    fun theReasonIsTheFirstBackingSignalInTheOrderOfTheDesign() {
        analyze(flagged, SignalType.DataRequest, SignalType.Impersonation, SignalType.Link)
        analyze(flagged, SignalType.DataRequest, SignalType.Urgency, SignalType.Impersonation)
        analyze(flagged, SignalType.DataRequest)
        assertEquals(listOf(AlertReason.Impersonation, AlertReason.Urgency, AlertReason.DataRequest), alerts.map { it.reason })
    }

    @Test
    fun theAlertNeverCarriesTheMessageTextAndHidesTheSenderInItsStringForm() {
        analyze(flagged, SignalType.Urgency)
        val alert = alerts.single()
        assertFalse("El texto no debe estar en la alerta", alert.toString().contains("ejemplo.invalid"))
        assertFalse(alert.toString().contains("+10000000001"))
    }

    // --- El historial (#12) ---

    @Test
    fun everyEvaluatedMessageIsRecordedWhetherOrNotItAlerts() {
        analyze(flagged, SignalType.Urgency) // alto
        analyze(flagged) // medio
        analyze(notFlagged) // bajo
        assertEquals(listOf(RiskLevel.High, RiskLevel.Medium, RiskLevel.Low), history.saved.map { it.assessment.level })
    }

    @Test
    fun theRecordKeepsTheMessageTheResultAndTheExplanation() {
        analyze(flagged, SignalType.Urgency, SignalType.Link)
        val record = history.saved.single()

        assertEquals(42L, record.messageId)
        assertEquals("+10000000001", record.sender)
        assertEquals(message.body, record.body)
        assertEquals(5_000L, record.receivedAtMillis)
        assertEquals(5_000L, record.analyzedAtMillis)
        assertEquals(RiskLevel.High, record.assessment.level)
        assertEquals(flagged, record.assessment.model)
        assertEquals("1.0.0", record.assessment.model.modelVersion)
        assertEquals(listOf(SignalType.Urgency, SignalType.Link), record.assessment.signals.signals.map { it.type })
    }

    @Test
    fun theRecordIsSavedBeforeTheAlertSoTheDetailFindsIt() {
        val order = mutableListOf<String>()
        val recording = object : AnalysisHistory by history {
            override fun save(record: AnalysisRecord): Long {
                order += "guardar"
                return history.save(record)
            }
        }
        AnalyzeReceivedMessage(
            assessor = RiskAssessor(FakeClassifier(flagged), FakeDetector(SignalType.Urgency)),
            history = recording,
            notifier = { order += "avisar" },
        ).onReceived(1, message)
        assertEquals(listOf("guardar", "avisar"), order)
    }

    @Test
    fun aMessageTheModelDidNotEvaluateIsNeitherRecordedNorAlerted() {
        for (reason in ClassificationResult.NotClassified.Reason.entries) {
            assertEquals(AnalysisOutcome.NotAssessed, analyze(ClassificationResult.NotClassified(reason), SignalType.Urgency))
        }
        assertTrue(history.saved.isEmpty())
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun ifSavingFailsTheAlertIsStillShownAndTheErrorIsReported() {
        val errors = mutableListOf<Throwable>()
        val failing = FakeHistory(failWith = IllegalStateException("disco lleno"))

        assertEquals(AnalysisOutcome.Alerted, analyze(flagged, SignalType.Urgency, history = failing, errors = errors))

        assertEquals(1, alerts.size)
        assertEquals(1, errors.size)
    }

    @Test
    fun theRecordNeverShowsTheMessageTextInItsStringForm() {
        analyze(flagged, SignalType.Urgency)
        val text = history.saved.single().toString()
        assertFalse("ejemplo.invalid" in text)
        assertFalse("+10000000001" in text)
    }
}
