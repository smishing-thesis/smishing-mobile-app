package com.app.smishing.data.history

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.app.smishing.domain.classification.ClassificationResult
import com.app.smishing.domain.history.AnalysisRecord
import com.app.smishing.domain.mitigation.ActionNote
import com.app.smishing.domain.mitigation.ActionStatus
import com.app.smishing.domain.mitigation.ActionType
import com.app.smishing.domain.mitigation.latest
import com.app.smishing.domain.risk.RiskPolicy
import com.app.smishing.domain.signals.SignalAnalysis
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Las acciones de mitigación y la marca de "seguro" en la base local (#28, #16). Datos sintéticos, en una base en memoria. */
@RunWith(AndroidJUnit4::class)
class RoomMitigationLogInstrumentedTest {

    private lateinit var database: HistoryDatabase
    private lateinit var history: RoomAnalysisHistory
    private lateinit var log: RoomMitigationLog

    @Before
    fun setUp() {
        database = HistoryDatabase.inMemory(InstrumentationRegistry.getInstrumentation().targetContext)
        history = RoomAnalysisHistory(database)
        log = RoomMitigationLog(database)
    }

    @After
    fun tearDown() = database.close()

    private fun record(messageId: Long) = AnalysisRecord(
        0, messageId, "+10000000001", "Texto sintético $messageId", 100, 200,
        RiskPolicy.decide(ClassificationResult.Classified(0.97f, true, 0.9, "1.0.0", "1.1.0"), SignalAnalysis("1.1.0", emptyList())),
    )

    private fun saveOne(messageId: Long = 1) = history.save(record(messageId))

    @Test
    fun anActionIsRecordedAsRequestedThenUpdatedWithItsOutcomeAndCause() {
        val recordId = saveOne()
        val actionId = log.request(recordId, ActionType.BlockSender, 1_000)
        assertEquals(ActionStatus.Requested, log.actionsOf(recordId).single().status)

        log.update(actionId, ActionStatus.Failed, ActionNote.NotSupported, 2_000)

        val action = log.actionsOf(recordId).single()
        assertEquals(ActionStatus.Failed, action.status)
        assertEquals(ActionNote.NotSupported, action.note)
        assertEquals(ActionType.BlockSender, action.type)
        assertEquals(1_000L, action.requestedAtMillis)
        assertEquals(2_000L, action.updatedAtMillis)
    }

    @Test
    fun theLatestAttemptOfEachTypeWins() {
        val recordId = saveOne()
        val first = log.request(recordId, ActionType.BlockSender, 1)
        log.update(first, ActionStatus.Failed, ActionNote.Error, 2)
        val second = log.request(recordId, ActionType.BlockSender, 3)
        log.update(second, ActionStatus.Completed, null, 4)
        val removal = log.request(recordId, ActionType.RemoveMessage, 5)
        log.update(removal, ActionStatus.Completed, null, 6)

        assertEquals(3, log.actionsOf(recordId).size)
        val latest = log.actionsOf(recordId).latest()
        assertEquals(ActionStatus.Completed, latest.getValue(ActionType.BlockSender).status)
        assertEquals(second, latest.getValue(ActionType.BlockSender).id)
        assertEquals(ActionStatus.Completed, latest.getValue(ActionType.RemoveMessage).status)
    }

    @Test
    fun actionsAreGroupedByRecord() {
        val a = saveOne(1)
        val b = saveOne(2)
        log.request(a, ActionType.BlockSender, 1)
        log.request(b, ActionType.RemoveMessage, 2)
        log.request(b, ActionType.BlockSender, 3)
        val all = log.all()
        assertEquals(1, all.getValue(a).size)
        assertEquals(2, all.getValue(b).size)
    }

    @Test
    fun markingAsSafeAndRelinkingTheMessageAreKeptInTheRecord() {
        val id = saveOne(messageId = 5)
        assertNull(history.byId(id)?.markedSafeAtMillis)

        history.markSafe(id, 7_000)
        history.updateMessageId(id, 77)

        val saved = history.byId(id)!!
        assertEquals(7_000L, saved.markedSafeAtMillis)
        assertEquals(77L, saved.messageId)
        assertEquals("Se encuentra por su id nuevo en el buzón", id, history.byMessageId(77)?.id)
        assertNull(history.byMessageId(5))
    }

    @Test
    fun deletingTheAnalyzedMessageDeletesItsActions() {
        val id = saveOne()
        log.request(id, ActionType.BlockSender, 1)
        database.dao().delete(id)
        assertTrue(log.all().isEmpty())
    }

    @Test
    fun theLogAnnouncesChanges() {
        val id = saveOne()
        val heard = CountDownLatch(1)
        val subscription = log.onChange { heard.countDown() }
        log.request(id, ActionType.RemoveMessage, 1)
        assertTrue("No avisó del cambio", heard.await(5, TimeUnit.SECONDS))
        subscription.close()
    }
}
