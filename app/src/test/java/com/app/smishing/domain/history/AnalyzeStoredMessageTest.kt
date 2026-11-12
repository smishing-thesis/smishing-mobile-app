package com.app.smishing.domain.history

import com.app.smishing.domain.alerts.AnalysisOutcome
import com.app.smishing.domain.classification.ClassificationResult
import com.app.smishing.domain.classification.MessageClassifier
import com.app.smishing.domain.risk.RiskAssessor
import com.app.smishing.domain.risk.RiskLevel
import com.app.smishing.domain.signals.SignalAnalysis
import com.app.smishing.domain.signals.SignalDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyzeStoredMessageTest {

    // Texto sintético: los mensajes de las pruebas nunca son reales.
    private fun risk(id: Long, body: String) = MessageRisk(id, id, RiskLevel.Low, false, body)

    @Test
    fun aMessageIsAnalyzedOnlyWhenTheRecordIsOfThatMessage() {
        val risks = mapOf(1L to risk(1, "texto guardado"))
        assertTrue(isAnalyzed(risks, 1, "texto guardado"))
        // Si el almacenamiento de SMS del sistema se borra, los ids empiezan de nuevo: otro texto con el mismo id no cuenta.
        assertFalse(isAnalyzed(risks, 1, "otro texto"))
        assertFalse(isAnalyzed(risks, 2, "texto guardado"))
    }

    private class FakeClassifier(private val accepted: Boolean) : MessageClassifier {
        override fun accepts(message: String) = accepted
        override fun classify(message: String) =
            if (accepted) ClassificationResult.Classified(0.97f, true, 0.9, "1.0.0", "1.1.0")
            else ClassificationResult.NotClassified(ClassificationResult.NotClassified.Reason.TOO_SHORT)
    }

    private class FakeHistory : AnalysisHistory {
        val saved = mutableListOf<AnalysisRecord>()
        override fun save(record: AnalysisRecord): Long { saved += record; return saved.size.toLong() }
        override fun all() = saved.reversed()
        override fun byId(id: Long) = saved.getOrNull(id.toInt() - 1)
        override fun byMessageId(messageId: Long) = saved.lastOrNull { it.messageId == messageId }
        override fun markSafe(id: Long, nowMillis: Long) = Unit
        override fun updateMessageId(id: Long, messageId: Long) = Unit
    }

    private val history = FakeHistory()
    private val detector = object : SignalDetector {
        override fun detect(message: String) = SignalAnalysis("1.1.0", emptyList())
    }

    private fun analyzer(accepted: Boolean) = AnalyzeStoredMessage(RiskAssessor(FakeClassifier(accepted), detector), history) { 9_000L }

    @Test
    fun analyzingSavesTheRecordWithTheReceivedTimeOfTheMessage() {
        assertEquals(AnalysisOutcome.Recorded, analyzer(true).analyze(3, "+10000000001", "mensaje de prueba", 3_000L))
        val record = history.saved.single()
        assertEquals(3L, record.messageId)
        assertEquals(3_000L, record.receivedAtMillis)
        assertEquals(9_000L, record.analyzedAtMillis)
    }

    @Test
    fun aMessageTheModelDoesNotEvaluateSavesNothing() {
        assertEquals(AnalysisOutcome.NotAssessed, analyzer(false).analyze(1, "+10000000001", "ok", 1_000L))
        assertTrue(history.saved.isEmpty())
    }
}
