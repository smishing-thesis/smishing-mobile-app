package com.app.smishing.domain.sharing

import com.app.smishing.domain.classification.ClassificationResult
import com.app.smishing.domain.history.AnalysisHistory
import com.app.smishing.domain.history.AnalysisRecord
import com.app.smishing.domain.risk.RiskLevel
import com.app.smishing.domain.risk.RiskPolicy
import com.app.smishing.domain.signals.DetectedSignal
import com.app.smishing.domain.signals.SignalAnalysis
import com.app.smishing.domain.signals.SignalType
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueingAnalysisHistoryTest {

    private class FakeHistory : AnalysisHistory {
        val saved = mutableListOf<AnalysisRecord>()
        override fun save(record: AnalysisRecord): Long { saved += record; return saved.size.toLong() }
        override fun all() = saved.reversed()
        override fun byId(id: Long) = saved.getOrNull(id.toInt() - 1)
        override fun byMessageId(messageId: Long) = saved.lastOrNull { it.messageId == messageId }
        override fun delete(ids: Collection<Long>) = 0
        override fun deleteAll() = 0
        override fun deleteByMessage(messageId: Long, body: String) = 0
        override fun markSafe(id: Long, nowMillis: Long) = Unit
        override fun updateMessageId(id: Long, messageId: Long) = Unit
    }

    private val model = ClassificationResult.Classified(0.97f, true, 0.9034745693206787, "1.0.0", "1.1.0")

    // Alto: el modelo lo marca y una señal lo respalda. Medio: el modelo lo marca sin respaldo.
    private fun record(level: RiskLevel): AnalysisRecord {
        val signals = if (level == RiskLevel.High) listOf(DetectedSignal(SignalType.Urgency, "urgency.test", "fragmento")) else emptyList()
        val assessment = RiskPolicy.decide(model, SignalAnalysis("1.1.0", signals))
        assertEquals(level, assessment.level)
        return AnalysisRecord(0, 7, "+10000000001", "Mensaje sintético", 1_000L, 1_050L, assessment)
    }

    private val queue = FakeQueue()
    private var queued = 0

    private fun history(consent: Boolean, queue: PendingContributions = this.queue) =
        QueueingAnalysisHistory(FakeHistory(), FakeConsent(consent), queue, clock = { 5L }, onQueued = { queued++ })

    @Test
    fun aDangerousMessageWithTheContributionOnIsQueuedAndTheSendingIsScheduled() {
        val id = history(consent = true).save(record(RiskLevel.High))
        assertEquals(1L, id)
        assertEquals(listOf(1L), queue.rows.keys.toList())
        assertEquals(1, queued)
    }

    @Test
    fun aSuspiciousMessageOrOneWithTheContributionOffIsNeverQueued() {
        history(consent = true).save(record(RiskLevel.Medium))
        history(consent = false).save(record(RiskLevel.High))
        assertEquals(0, queue.count())
        assertEquals(0, queued)
    }

    @Test
    fun aFailureToQueueDoesNotBreakTheAnalysisThatWasAlreadySaved() {
        val broken = object : PendingContributions by FakeQueue() {
            override fun add(analyzedMessageId: Long, nowMillis: Long) = error("sin espacio")
        }
        assertEquals(1L, history(consent = true, queue = broken).save(record(RiskLevel.High)))
        assertEquals(0, queued)
    }
}
