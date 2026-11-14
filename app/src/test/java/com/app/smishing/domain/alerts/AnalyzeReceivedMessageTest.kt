package com.app.smishing.domain.alerts

import com.app.smishing.domain.classification.ClassificationResult
import com.app.smishing.domain.classification.MessageClassifier
import com.app.smishing.domain.history.AnalysisHistory
import com.app.smishing.domain.history.AnalysisRecord
import com.app.smishing.domain.mitigation.MitigationDecision
import com.app.smishing.domain.mitigation.MitigationMode
import com.app.smishing.domain.mitigation.MitigationSettings
import com.app.smishing.domain.mitigation.Mitigator
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
        override fun delete(ids: Collection<Long>) = 0
        override fun deleteAll() = 0
        override fun deleteByMessage(messageId: Long, body: String) = 0
        override fun markSafe(id: Long, nowMillis: Long) = Unit
        override fun updateMessageId(id: Long, messageId: Long) = Unit
    }

    private val flagged = ClassificationResult.Classified(0.97f, true, 0.9, "1.0.0", "1.1.0")
    private val notFlagged = ClassificationResult.Classified(0.1f, false, 0.9, "1.0.0", "1.1.0")
    private val message = IncomingSms("+10000000001", "Texto del mensaje con http://ejemplo.invalid", 1_000L)

    private class FakeSettings(var current: MitigationMode = MitigationMode.Ask, private val failWith: Exception? = null) : MitigationSettings {
        var reads = 0
        override fun mode(): MitigationMode {
            reads++
            failWith?.let { throw it }
            return current
        }
        override fun setMode(mode: MitigationMode) { current = mode }
    }

    private val alerts = mutableListOf<RiskAlert>()
    private val messageNotices = mutableListOf<Pair<Long, IncomingSms>>()
    private val history = FakeHistory()
    private val settings = FakeSettings()
    private val applied = mutableListOf<Pair<MitigationDecision, AnalysisRecord>>()
    private var mitigatorFails: Exception? = null
    private val mitigator = Mitigator { decision, record ->
        applied += decision to record
        mitigatorFails?.let { throw it }
    }

    private fun analyze(
        classification: ClassificationResult,
        vararg types: SignalType,
        history: AnalysisHistory = this.history,
        errors: MutableList<Throwable> = mutableListOf(),
        settings: MitigationSettings = this.settings,
        mitigationErrors: MutableList<Throwable> = mutableListOf(),
    ) = AnalyzeReceivedMessage(
        assessor = RiskAssessor(FakeClassifier(classification), FakeDetector(*types)),
        history = history,
        notifier = { alerts += it },
        messageNotifier = { id, sms -> messageNotices += id to sms },
        settings = settings,
        mitigator = mitigator,
        clock = { 5_000L },
        onHistoryError = { errors += it },
        onMitigationError = { mitigationErrors += it },
    ).onReceived(messageId = 42, message = message)

    // --- La alerta (#10) ---

    @Test
    fun aHighRiskMessageAlertsWithItsIdSenderTextAndReason() {
        assertEquals(AnalysisOutcome.Alerted, analyze(flagged, SignalType.Urgency, SignalType.Link))
        assertEquals(listOf(RiskAlert(42, "+10000000001", message.body, AlertReason.Urgency)), alerts)
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
            messageNotifier = { _, _ -> order += "aviso de mensaje" },
            settings = settings,
            mitigator = mitigator,
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

    // --- El modo de mitigación (#13) ---

    @Test
    fun inAskModeAHighRiskMessageAsksAndCarriesTheSavedRecord() {
        settings.current = MitigationMode.Ask
        analyze(flagged, SignalType.Urgency)
        val (decision, record) = applied.single()
        assertEquals(MitigationDecision.AskUser, decision)
        assertEquals(history.saved.single().copy(id = 1), record)
    }

    @Test
    fun inAutomaticModeAHighRiskMessageIsActedOn() {
        settings.current = MitigationMode.Automatic
        analyze(flagged, SignalType.Urgency)
        assertEquals(listOf(MitigationDecision.Act), applied.map { it.first })
    }

    @Test
    fun inDisabledModeNothingIsDoneToTheMessageButTheAlertStillShows() {
        settings.current = MitigationMode.Disabled
        assertEquals(AnalysisOutcome.Alerted, analyze(flagged, SignalType.Urgency))
        assertTrue(applied.isEmpty())
        assertEquals(1, alerts.size)
    }

    @Test
    fun mediumAndLowRiskNeverActWhateverTheModeAndTheModeIsNotEvenRead() {
        for (mode in MitigationMode.entries) {
            settings.current = mode
            analyze(flagged, SignalType.Link) // medio
            analyze(notFlagged) // bajo
        }
        assertTrue(applied.isEmpty())
        assertEquals(0, settings.reads)
    }

    @Test
    fun theModeIsReadAfterTheRecordIsSavedAndBeforeTheMitigatorOrTheAlert() {
        val order = mutableListOf<String>()
        val reading = object : MitigationSettings {
            override fun mode(): MitigationMode { order += "leer modo"; return MitigationMode.Automatic }
            override fun setMode(mode: MitigationMode) = Unit
        }
        val recording = object : AnalysisHistory by history {
            override fun save(record: AnalysisRecord): Long { order += "guardar"; return history.save(record) }
        }
        AnalyzeReceivedMessage(
            assessor = RiskAssessor(FakeClassifier(flagged), FakeDetector(SignalType.Urgency)),
            history = recording,
            notifier = { order += "avisar" },
            messageNotifier = { _, _ -> order += "aviso de mensaje" },
            settings = reading,
            mitigator = { _, _ -> order += "actuar" },
        ).onReceived(1, message)
        assertEquals(listOf("guardar", "leer modo", "actuar", "avisar"), order)
    }

    @Test
    fun ifTheModeCannotBeReadNothingIsDoneButTheAlertStillShows() {
        val errors = mutableListOf<Throwable>()
        val broken = FakeSettings(failWith = IllegalStateException("preferencias ilegibles"))

        assertEquals(AnalysisOutcome.Alerted, analyze(flagged, SignalType.Urgency, settings = broken, mitigationErrors = errors))

        assertTrue("Sin modo legible no se debe actuar", applied.isEmpty())
        assertEquals(1, errors.size)
        assertEquals(1, alerts.size)
    }

    @Test
    fun ifTheActionFailsTheAlertStillShowsAndTheErrorIsReported() {
        mitigatorFails = IllegalStateException("no se pudo")
        val errors = mutableListOf<Throwable>()

        assertEquals(AnalysisOutcome.Alerted, analyze(flagged, SignalType.Urgency, mitigationErrors = errors))

        assertEquals(1, errors.size)
        assertEquals(1, alerts.size)
    }

    @Test
    fun aMessageTheModelDidNotEvaluateIsNotActedOnInAnyMode() {
        for (mode in MitigationMode.entries) {
            settings.current = mode
            analyze(ClassificationResult.NotClassified(ClassificationResult.NotClassified.Reason.entries.first()), SignalType.Urgency)
        }
        assertTrue(applied.isEmpty())
    }

    // --- El aviso de cada mensaje (#49) ---

    @Test
    fun aHighRiskMessageShowsOnlyItsRiskAlertAndNoSecondNotification() {
        assertEquals(AnalysisOutcome.Alerted, analyze(flagged, SignalType.Urgency))
        assertEquals(1, alerts.size)
        assertTrue("El de riesgo alto no debe sumar un aviso de mensaje", messageNotices.isEmpty())
    }

    @Test
    fun aMessageThatIsNotHighRiskIsNotifiedAsAnyOtherMessageAndRaisesNoAlert() {
        analyze(flagged, SignalType.Link) // medio
        analyze(notFlagged) // bajo
        assertTrue("Medio y bajo no deben dar alerta", alerts.isEmpty())
        assertEquals(listOf(42L to message, 42L to message), messageNotices)
    }

    @Test
    fun aMessageTheModelDidNotEvaluateIsStillNotified() {
        for (reason in ClassificationResult.NotClassified.Reason.entries) {
            assertEquals(AnalysisOutcome.NotAssessed, analyze(ClassificationResult.NotClassified(reason), SignalType.Urgency))
        }
        assertEquals("Un mensaje corto o con el análisis suspendido igual avisa", ClassificationResult.NotClassified.Reason.entries.size, messageNotices.size)
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun ifTheAnalysisFailsTheMessageIsStillNotifiedAndTheFailureIsNotHidden() {
        val failing = object : MessageClassifier {
            override fun classify(message: String): ClassificationResult = throw IllegalStateException("la biblioteca nativa falló")
        }
        val useCase = AnalyzeReceivedMessage(
            assessor = RiskAssessor(failing, FakeDetector()),
            history = history,
            notifier = { alerts += it },
            messageNotifier = { id, sms -> messageNotices += id to sms },
            settings = settings,
            mitigator = mitigator,
        )
        var thrown: Throwable? = null
        try {
            useCase.onReceived(42, message)
        } catch (e: IllegalStateException) {
            thrown = e
        }
        assertTrue("El fallo debe seguir llegando a quien llamó", thrown != null)
        assertEquals("Aun así hay aviso del mensaje", listOf(42L to message), messageNotices)
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun ifTheMessageNotificationFailsTheOutcomeIsNotAffected() {
        val useCase = AnalyzeReceivedMessage(
            assessor = RiskAssessor(FakeClassifier(notFlagged), FakeDetector()),
            history = history,
            notifier = { alerts += it },
            messageNotifier = { _, _ -> throw IllegalStateException("sin canal") },
            settings = settings,
            mitigator = mitigator,
        )
        assertEquals(AnalysisOutcome.Recorded, useCase.onReceived(42, message))
        assertEquals(1, history.saved.size)
    }

    @Test
    fun theMessageNotificationComesAfterTheRecordIsSaved() {
        val order = mutableListOf<String>()
        val recording = object : AnalysisHistory by history {
            override fun save(record: AnalysisRecord): Long { order += "guardar"; return history.save(record) }
        }
        AnalyzeReceivedMessage(
            assessor = RiskAssessor(FakeClassifier(notFlagged), FakeDetector()),
            history = recording,
            notifier = { order += "alerta" },
            messageNotifier = { _, _ -> order += "aviso de mensaje" },
            settings = settings,
            mitigator = mitigator,
        ).onReceived(1, message)
        assertEquals(listOf("guardar", "aviso de mensaje"), order)
    }
}
