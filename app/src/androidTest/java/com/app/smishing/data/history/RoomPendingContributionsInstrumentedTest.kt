package com.app.smishing.data.history

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.app.smishing.domain.classification.ClassificationResult
import com.app.smishing.domain.history.AnalysisRecord
import com.app.smishing.domain.risk.RiskLevel
import com.app.smishing.domain.risk.RiskPolicy
import com.app.smishing.domain.sharing.ContributionLabel
import com.app.smishing.domain.signals.SignalAnalysis
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** La cola de aportes pendientes (#71) en la base real: orden, límite, quitar, vaciar y que borrar el historial la limpie. */
@RunWith(AndroidJUnit4::class)
class RoomPendingContributionsInstrumentedTest {

    private lateinit var database: HistoryDatabase
    private lateinit var history: RoomAnalysisHistory
    private lateinit var queue: RoomPendingContributions

    @Before
    fun setUp() {
        database = HistoryDatabase.inMemory(InstrumentationRegistry.getInstrumentation().targetContext)
        history = RoomAnalysisHistory(database)
        queue = RoomPendingContributions(database)
    }

    @After
    fun tearDown() = database.close()

    // Texto sintético: los mensajes de las pruebas nunca son reales.
    private fun save(n: Int): Long {
        val assessment = RiskPolicy.decide(
            ClassificationResult.Classified(0.97f, true, 0.9034745693206787, "1.0.0", "1.1.0"),
            SignalAnalysis("1.1.0", emptyList()),
        )
        return history.save(AnalysisRecord(0, n.toLong(), "+10000000001", "Mensaje sintético $n", 1_000L, 1_050L, assessment))
    }

    @Test
    fun theOldestComeFirstTheLimitAppliesAndAddingTwiceKeepsOne() {
        val ids = (1..4).map { save(it) }
        queue.add(ids[2], 30)
        queue.add(ids[0], 10)
        queue.add(ids[1], 20)
        queue.add(ids[0], 99)
        assertEquals(3, queue.count())
        assertEquals(listOf(ids[0], ids[1]), queue.next(2).map { it.analyzedMessageId })
        assertEquals("Mensaje sintético 1", queue.next(1).single().body)
        // Con la etiqueta y la versión del modelo, que el backend pide junto al texto.
        assertEquals(ContributionLabel.Smishing, queue.next(1).single().label)
        assertEquals("1.0.0", queue.next(1).single().modelVersion)
    }

    @Test
    fun aSuspiciousMessageOfMediumRiskAlsoTravelsAsSmishing() {
        // El modelo lo marca pero ninguna señal lo respalda: nivel medio (risk.model_only).
        val assessment = RiskPolicy.decide(
            ClassificationResult.Classified(0.97f, true, 0.9034745693206787, "1.0.0", "1.1.0"),
            SignalAnalysis("1.1.0", emptyList()),
        )
        assertEquals(RiskLevel.Medium, assessment.level)
        val id = history.save(AnalysisRecord(0, 9L, "+10000000001", "Mensaje sospechoso sintético", 1_000L, 1_050L, assessment))
        queue.add(id, 1)
        assertEquals(ContributionLabel.Smishing, queue.next(1).single().label)
    }

    @Test
    fun removingAndClearingLeaveTheRightOnes() {
        val ids = (1..3).map { save(it) }
        ids.forEachIndexed { i, id -> queue.add(id, i.toLong()) }
        queue.remove(listOf(ids[0], ids[2]))
        assertEquals(listOf(ids[1]), queue.next(10).map { it.analyzedMessageId })
        queue.clear()
        assertEquals(0, queue.count())
    }

    @Test
    fun deletingAMessageFromTheHistoryTakesItOutOfTheQueue() {
        val ids = (1..3).map { save(it) }
        ids.forEachIndexed { i, id -> queue.add(id, i.toLong()) }
        history.delete(listOf(ids[1]))
        assertEquals(listOf(ids[0], ids[2]), queue.next(10).map { it.analyzedMessageId })
        history.deleteAll()
        assertEquals(0, queue.count())
    }
}
