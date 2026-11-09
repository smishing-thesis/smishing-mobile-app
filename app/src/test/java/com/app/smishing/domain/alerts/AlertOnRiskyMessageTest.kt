package com.app.smishing.domain.alerts

import com.app.smishing.domain.classification.ClassificationResult
import com.app.smishing.domain.classification.MessageClassifier
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

class AlertOnRiskyMessageTest {

    private class FakeClassifier(private val result: ClassificationResult) : MessageClassifier {
        override fun classify(message: String) = result
    }

    private class FakeDetector(private vararg val types: SignalType) : SignalDetector {
        override fun detect(message: String) =
            SignalAnalysis("1.1.0", types.map { DetectedSignal(it, "test.${it.name.lowercase()}", "fragmento") })
    }

    private val flagged = ClassificationResult.Classified(0.97f, true, 0.9, "1.0.0", "1.1.0")
    private val notFlagged = ClassificationResult.Classified(0.1f, false, 0.9, "1.0.0", "1.1.0")
    private val message = IncomingSms("+10000000001", "Texto del mensaje con http://ejemplo.invalid", 1_000L)

    private val alerts = mutableListOf<RiskAlert>()
    private fun onReceived(classification: ClassificationResult, vararg types: SignalType) =
        AlertOnRiskyMessage(RiskAssessor(FakeClassifier(classification), FakeDetector(*types)), { alerts += it })
            .onReceived(messageId = 42, message = message)

    @Test
    fun aHighRiskMessageAlertsWithItsIdSenderAndReason() {
        assertEquals(AlertOutcome.Alerted, onReceived(flagged, SignalType.Urgency, SignalType.Link))
        assertEquals(listOf(RiskAlert(42, "+10000000001", AlertReason.Urgency)), alerts)
    }

    @Test
    fun onlyHighAlerts() {
        assertEquals(setOf(RiskLevel.High), AlertPolicy.ALERT_LEVELS)
        // Medio: el modelo lo marca sin respaldo.
        assertEquals(AlertOutcome.NotRisky, onReceived(flagged, SignalType.Link))
        // Medio: solo señales.
        assertEquals(AlertOutcome.NotRisky, onReceived(notFlagged, SignalType.Impersonation))
        // Bajo.
        assertEquals(AlertOutcome.NotRisky, onReceived(notFlagged))
        assertTrue("Medio y bajo no deben avisar", alerts.isEmpty())
    }

    @Test
    fun aMessageTheModelDidNotEvaluateGetsNoAlert() {
        for (reason in ClassificationResult.NotClassified.Reason.entries) {
            assertEquals(AlertOutcome.NotAssessed, onReceived(ClassificationResult.NotClassified(reason), SignalType.Urgency))
        }
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun theReasonIsTheFirstBackingSignalInTheOrderOfTheDesign() {
        onReceived(flagged, SignalType.DataRequest, SignalType.Impersonation, SignalType.Link)
        onReceived(flagged, SignalType.DataRequest, SignalType.Urgency, SignalType.Impersonation)
        onReceived(flagged, SignalType.DataRequest)
        assertEquals(listOf(AlertReason.Impersonation, AlertReason.Urgency, AlertReason.DataRequest), alerts.map { it.reason })
    }

    @Test
    fun aLinkAloneIsNeverTheReason() {
        // Con el modelo y solo un enlace el nivel es medio: no hay alerta, así que el enlace nunca es un motivo.
        assertEquals(AlertOutcome.NotRisky, onReceived(flagged, SignalType.Link))
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun theAlertNeverCarriesTheMessageTextAndHidesTheSenderInItsStringForm() {
        onReceived(flagged, SignalType.Urgency)
        val alert = alerts.single()
        assertFalse("El texto no debe estar en la alerta", alert.toString().contains("ejemplo.invalid"))
        assertFalse(alert.toString().contains("+10000000001"))
    }
}
