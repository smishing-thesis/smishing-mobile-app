package com.app.smishing.domain.history

import com.app.smishing.domain.classification.ClassificationResult
import com.app.smishing.domain.risk.RiskPolicy
import com.app.smishing.domain.signals.DetectedSignal
import com.app.smishing.domain.signals.SignalAnalysis
import com.app.smishing.domain.signals.SignalType
import org.junit.Assert.assertEquals
import org.junit.Test

/** El resumen de la semana de Inicio (#17): mensajes revisados y posibles estafas en los últimos 7 días. */
class WeeklySummaryTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day

    private fun record(analyzedAt: Long, high: Boolean, markedSafeAt: Long? = null): AnalysisRecord {
        val signals = if (high) listOf(DetectedSignal(SignalType.Urgency, "urgency.test", "x")) else emptyList()
        return AnalysisRecord(
            id = analyzedAt, messageId = analyzedAt, sender = "+10000000001", body = "Texto sintético", receivedAtMillis = analyzedAt,
            analyzedAtMillis = analyzedAt,
            assessment = RiskPolicy.decide(ClassificationResult.Classified(if (high) 0.97f else 0.1f, high, 0.9, "1.0.0", "1.1.0"), SignalAnalysis("1.1.0", signals)),
            markedSafeAtMillis = markedSafeAt,
        )
    }

    @Test
    fun withNoRecordsEverythingIsZero() {
        assertEquals(WeeklySummary(0, 0), weeklySummary(emptyList(), now))
    }

    @Test
    fun itCountsEveryReviewedMessageAndOnlyTheHighRiskOnesAsPossibleScams() {
        val records = listOf(record(now - day, high = true), record(now - 2 * day, high = false), record(now - 3 * day, high = true))
        assertEquals(WeeklySummary(reviewed = 3, possibleScams = 2), weeklySummary(records, now))
    }

    @Test
    fun theWindowIsTheLastSevenDaysAndIncludesItsEdge() {
        val records = listOf(
            record(now - 7 * day, high = true), // justo en el borde: entra
            record(now - 7 * day - 1, high = true), // un instante antes: no entra
            record(now, high = false), // ahora: entra
        )
        assertEquals(WeeklySummary(reviewed = 2, possibleScams = 1), weeklySummary(records, now))
    }

    @Test
    fun aRecordFromTheFutureIsNotCounted() {
        assertEquals(WeeklySummary(0, 0), weeklySummary(listOf(record(now + 1, high = true)), now))
    }

    @Test
    fun aMessageMarkedAsSafeIsReviewedButNoLongerAPossibleScam() {
        val records = listOf(record(now - day, high = true, markedSafeAt = now), record(now - day, high = true))
        assertEquals(WeeklySummary(reviewed = 2, possibleScams = 1), weeklySummary(records, now))
    }

    @Test
    fun theWindowLengthCanBeChanged() {
        val records = listOf(record(now - 2 * day, high = true), record(now - 10 * day, high = true))
        assertEquals(WeeklySummary(1, 1), weeklySummary(records, now, days = 3))
        assertEquals(WeeklySummary(2, 2), weeklySummary(records, now, days = 30))
    }
}
