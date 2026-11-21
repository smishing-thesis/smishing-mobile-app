package com.app.smishing.domain.mitigation

import com.app.smishing.domain.classification.ClassificationResult
import com.app.smishing.domain.history.AnalysisHistory
import com.app.smishing.domain.history.AnalysisRecord
import com.app.smishing.domain.risk.RiskPolicy
import com.app.smishing.domain.signals.DetectedSignal
import com.app.smishing.domain.signals.SignalAnalysis
import com.app.smishing.domain.signals.SignalType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hacer, anotar y deshacer las acciones sobre un mensaje (#14, #15, #16, #28), con piezas falsas en memoria. */
class MitigationExecutorTest {

    private class FakeLog : MitigationLog {
        val rows = mutableListOf<MitigationAction>()
        override fun request(recordId: Long, type: ActionType, nowMillis: Long): Long {
            val id = rows.size + 1L
            rows += MitigationAction(id, recordId, type, ActionStatus.Requested, null, nowMillis, nowMillis)
            return id
        }
        override fun update(actionId: Long, status: ActionStatus, note: ActionNote?, nowMillis: Long) {
            val i = rows.indexOfFirst { it.id == actionId }
            rows[i] = rows[i].copy(status = status, note = note, updatedAtMillis = nowMillis)
        }
        override fun actionsOf(recordId: Long) = rows.filter { it.recordId == recordId }
        override fun all() = rows.groupBy { it.recordId }
    }

    private class FakeHistory : AnalysisHistory {
        val records = mutableMapOf<Long, AnalysisRecord>()
        override fun save(record: AnalysisRecord): Long {
            val id = records.size + 1L
            records[id] = record.copy(id = id)
            return id
        }
        override fun all() = records.values.toList()
        override fun byId(id: Long) = records[id]
        override fun byMessageId(messageId: Long) = records.values.lastOrNull { it.messageId == messageId }
        override fun delete(ids: Collection<Long>): Int = ids.count { records.remove(it) != null }
        override fun deleteAll(): Int = records.size.also { records.clear() }
        override fun deleteByMessage(messageId: Long, body: String): Int {
            val gone = records.filterValues { it.messageId == messageId && it.body == body }.keys
            gone.forEach(records::remove)
            return gone.size
        }
        override fun markSafe(id: Long, nowMillis: Long) {
            records[id] = records.getValue(id).copy(markedSafeAtMillis = nowMillis)
        }
        override fun updateMessageId(id: Long, messageId: Long) {
            records[id] = records.getValue(id).copy(messageId = messageId)
        }
    }

    private class FakeSettings(var current: MitigationMode = MitigationMode.Ask, var failing: Boolean = false) : MitigationSettings {
        override fun mode(): MitigationMode {
            if (failing) throw IllegalStateException("preferencias ilegibles")
            return current
        }
        override fun setMode(mode: MitigationMode) { current = mode }
    }

    private class FakeBlocker(var available: Boolean = true, val blocked: MutableSet<String> = mutableSetOf()) : SenderBlocker {
        var blockResult = true
        var unblockResult = true
        var throwOnBlock: Exception? = null
        var blockCalls = 0
        var unblockCalls = 0
        var seenDuringBlock: (() -> Unit)? = null
        override fun isAvailable() = available
        override fun isBlocked(sender: String) = sender in blocked
        override fun block(sender: String): Boolean {
            blockCalls++
            seenDuringBlock?.invoke()
            throwOnBlock?.let { throw it }
            if (blockResult) blocked += sender
            return blockResult
        }
        override fun unblock(sender: String): Boolean {
            unblockCalls++
            if (unblockResult) blocked -= sender
            return unblockResult
        }
    }

    private class FakeInbox(val messages: MutableSet<Long> = mutableSetOf(42L)) : InboxMessages {
        var removeResult = true
        var restoreResult: Long? = 99L
        var removeCalls = 0
        val restored = mutableListOf<Triple<String, String, Long>>()
        override fun remove(messageId: Long): Boolean {
            removeCalls++
            return removeResult && messages.remove(messageId)
        }
        override fun restore(sender: String, body: String, receivedAtMillis: Long): Long? {
            restored += Triple(sender, body, receivedAtMillis)
            return restoreResult
        }
    }

    private val log = FakeLog()
    private val history = FakeHistory()
    private val settings = FakeSettings()
    private val blocker = FakeBlocker()
    private val inbox = FakeInbox()
    private var now = 1_000L
    private val executor = MitigationExecutor(log, history, settings, blocker, inbox) { ++now }

    private val unsaved = AnalysisRecord(
        id = 0, messageId = 42, sender = "+10000000001", body = "Texto sintético con http://ejemplo.invalid",
        receivedAtMillis = 500L, analyzedAtMillis = 600L,
        assessment = RiskPolicy.decide(
            ClassificationResult.Classified(0.97f, true, 0.9, "1.0.0", "1.1.0"),
            SignalAnalysis("1.1.0", listOf(DetectedSignal(SignalType.Urgency, "urgency.test", "fragmento"))),
        ),
    )

    private fun saved(): AnalysisRecord = unsaved.copy(id = history.save(unsaved))

    private fun latest(record: AnalysisRecord, type: ActionType) = log.actionsOf(record.id).latest().getValue(type)

    // --- Solo si la configuración lo permite (#14) ---

    @Test
    fun ifTheModeCannotBeReadNothingIsDone() {
        settings.failing = true
        val record = saved()
        assertNull(executor.perform(record, ActionType.BlockSender))
        assertTrue(log.rows.isEmpty())
        assertEquals(0, blocker.blockCalls)
    }

    @Test
    fun withoutALocalRecordNothingIsDone() {
        assertNull(executor.perform(unsaved, ActionType.BlockSender))
        assertNull(executor.perform(unsaved, ActionType.RemoveMessage))
        assertTrue(log.rows.isEmpty())
        assertEquals(0, inbox.removeCalls)
    }

    // --- Lo que la persona pide a mano (#29) ---

    @Test
    fun whatThePersonAsksForIsDoneWithAnyMode() {
        settings.current = MitigationMode.Ask
        val record = saved()
        assertEquals(ActionStatus.Completed, executor.performManual(record, ActionType.BlockSender))
        assertEquals(ActionStatus.Completed, executor.performManual(record, ActionType.RemoveMessage))
        assertTrue("+10000000001" in blocker.blocked)
        assertEquals(1, inbox.removeCalls)
        // Queda anotado, para que el historial lo refleje.
        assertEquals(ActionStatus.Completed, latest(record, ActionType.BlockSender).status)
        assertEquals(ActionStatus.Completed, latest(record, ActionType.RemoveMessage).status)
    }

    @Test
    fun aManualBlockThatCannotBeDoneIsRecordedAsFailedWithItsCause() {
        settings.current = MitigationMode.Ask
        blocker.available = false
        val record = saved()
        assertEquals(ActionStatus.Failed, executor.performManual(record, ActionType.BlockSender))
        assertEquals(ActionNote.NotSupported, latest(record, ActionType.BlockSender).note)
        assertTrue(blocker.blocked.isEmpty())
    }

    @Test
    fun aManualActionWithoutALocalRecordDoesNothingAndIsNotRecorded() {
        assertNull(executor.performManual(unsaved, ActionType.BlockSender))
        assertTrue(log.rows.isEmpty())
        assertEquals(0, blocker.blockCalls)
    }

    // --- Bloquear (#14) ---

    @Test
    fun blockingBlocksTheSenderAndRecordsItAsCompleted() {
        val record = saved()
        assertEquals(ActionStatus.Completed, executor.perform(record, ActionType.BlockSender))
        assertTrue("+10000000001" in blocker.blocked)
        val action = latest(record, ActionType.BlockSender)
        assertEquals(ActionStatus.Completed, action.status)
        assertNull(action.note)
    }

    @Test
    fun ifThePlatformCannotBlockItFailsWithItsCauseAndNeverLooksCompleted() {
        blocker.available = false
        val record = saved()
        assertEquals(ActionStatus.Failed, executor.perform(record, ActionType.BlockSender))
        assertEquals(0, blocker.blockCalls)
        val action = latest(record, ActionType.BlockSender)
        assertEquals(ActionStatus.Failed, action.status)
        assertEquals(ActionNote.NotSupported, action.note)
        assertTrue(blocker.blocked.isEmpty())
    }

    @Test
    fun aRefusedOrFailedBlockIsRecordedAsFailedWithTheRightCause() {
        blocker.blockResult = false
        var record = saved()
        executor.perform(record, ActionType.BlockSender)
        assertEquals(ActionNote.Error, latest(record, ActionType.BlockSender).note)

        blocker.throwOnBlock = SecurityException("no es la app predeterminada")
        record = saved()
        executor.perform(record, ActionType.BlockSender)
        assertEquals(ActionStatus.Failed, latest(record, ActionType.BlockSender).status)
        assertEquals(ActionNote.NotAllowed, latest(record, ActionType.BlockSender).note)

        blocker.throwOnBlock = IllegalStateException("fallo")
        record = saved()
        executor.perform(record, ActionType.BlockSender)
        assertEquals(ActionNote.Error, latest(record, ActionType.BlockSender).note)
    }

    @Test
    fun theActionIsRecordedAsRequestedBeforeItIsExecuted() {
        val record = saved()
        var seen: ActionStatus? = null
        blocker.seenDuringBlock = { seen = latest(record, ActionType.BlockSender).status }
        executor.perform(record, ActionType.BlockSender)
        assertEquals(ActionStatus.Requested, seen)
    }

    @Test
    fun aNumberThatWasAlreadyBlockedIsLeftAloneAndNeverUnblockedByTheApp() {
        blocker.blocked += "+10000000001"
        val record = saved()
        executor.perform(record, ActionType.BlockSender)
        assertEquals(0, blocker.blockCalls)
        assertEquals(ActionNote.AlreadyApplied, latest(record, ActionType.BlockSender).note)

        executor.markSafe(record)
        assertEquals("No lo bloqueó la app: no debe desbloquearlo", 0, blocker.unblockCalls)
        assertTrue("+10000000001" in blocker.blocked)
        assertEquals(ActionStatus.Reverted, latest(record, ActionType.BlockSender).status)
    }

    @Test
    fun anActionAlreadyCompletedIsNotRepeated() {
        val record = saved()
        executor.perform(record, ActionType.BlockSender)
        assertEquals(ActionStatus.Completed, executor.perform(record, ActionType.BlockSender))
        assertEquals(1, blocker.blockCalls)
        assertEquals(1, log.rows.size)
    }

    @Test
    fun retryingAfterAFailureKeepsBothAttemptsAndTheLatestOneCounts() {
        blocker.available = false
        val record = saved()
        executor.perform(record, ActionType.BlockSender)
        blocker.available = true
        executor.perform(record, ActionType.BlockSender)
        assertEquals(listOf(ActionStatus.Failed, ActionStatus.Completed), log.actionsOf(record.id).map { it.status })
        assertEquals(ActionStatus.Completed, latest(record, ActionType.BlockSender).status)
    }

    // --- Retirar (#15) ---

    @Test
    fun removingDeletesTheMessageFromTheInboxOnlyWhenTheLocalCopyIsComplete() {
        val record = saved()
        assertEquals(ActionStatus.Completed, executor.perform(record, ActionType.RemoveMessage))
        assertFalse(42L in inbox.messages)
        assertEquals(ActionStatus.Completed, latest(record, ActionType.RemoveMessage).status)
        // La copia local sigue ahí: es la referencia para explicar y revertir.
        assertEquals(unsaved.body, history.byId(record.id)?.body)
    }

    @Test
    fun withoutAMatchingLocalCopyTheMessageIsNotRemoved() {
        val record = unsaved.copy(id = 7) // el historial no tiene el registro 7
        assertEquals(ActionStatus.Failed, executor.perform(record, ActionType.RemoveMessage))
        assertEquals(0, inbox.removeCalls)
        assertTrue(42L in inbox.messages)
        assertEquals(ActionNote.NoLocalCopy, latest(record, ActionType.RemoveMessage).note)
    }

    @Test
    fun aFailedRemovalIsNeverRecordedAsCompleted() {
        inbox.removeResult = false
        val record = saved()
        assertEquals(ActionStatus.Failed, executor.perform(record, ActionType.RemoveMessage))
        assertEquals(ActionNote.Error, latest(record, ActionType.RemoveMessage).note)
    }

    @Test
    fun theAutomaticActionBlocksAndRemovesAndOneFailureDoesNotStopTheOther() {
        blocker.available = false
        val record = saved()
        executor.act(record)
        assertEquals(ActionStatus.Failed, latest(record, ActionType.BlockSender).status)
        assertEquals(ActionStatus.Completed, latest(record, ActionType.RemoveMessage).status)
    }

    // --- Marcar como seguro y revertir (#16) ---

    @Test
    fun markingAsSafeUnblocksRestoresTheMessageAndRemembersTheDecision() {
        val record = saved()
        executor.act(record)
        assertTrue(42L !in inbox.messages)

        val result = executor.markSafe(record)

        assertTrue(result.complete)
        assertTrue("+10000000001" !in blocker.blocked)
        assertEquals(listOf(Triple(unsaved.sender, unsaved.body, unsaved.receivedAtMillis)), inbox.restored)
        assertEquals("El registro sigue al mensaje restaurado, que tiene un id nuevo", 99L, history.byId(record.id)?.messageId)
        assertTrue(history.byId(record.id)?.markedSafeAtMillis != null)
        assertEquals(ActionStatus.Reverted, latest(record, ActionType.BlockSender).status)
        assertEquals(ActionStatus.Reverted, latest(record, ActionType.RemoveMessage).status)
    }

    @Test
    fun whatCannotBeRevertedStaysInEffectAndIsReported() {
        val record = saved()
        executor.act(record)
        blocker.unblockResult = false
        inbox.restoreResult = null

        val result = executor.markSafe(record)

        assertEquals(setOf(ActionType.BlockSender, ActionType.RemoveMessage), result.notReverted)
        for (type in ActionType.entries) {
            val action = latest(record, type)
            assertEquals("Sigue vigente", ActionStatus.Completed, action.status)
            assertEquals(ActionNote.RevertFailed, action.note)
        }
        assertTrue("La decisión de la persona se guarda igual", history.byId(record.id)?.markedSafeAtMillis != null)
    }

    @Test
    fun anExceptionWhileRevertingIsNotCrashAndIsReportedAsNotReverted() {
        val record = saved()
        executor.perform(record, ActionType.RemoveMessage)
        val throwing = object : InboxMessages {
            override fun remove(messageId: Long) = true
            override fun restore(sender: String, body: String, receivedAtMillis: Long): Long? = throw SecurityException("no")
        }
        val result = MitigationExecutor(log, history, settings, blocker, throwing) { ++now }.markSafe(record)
        assertEquals(setOf(ActionType.RemoveMessage), result.notReverted)
    }

    @Test
    fun markingAMessageWithNoActionsJustRemembersTheDecision() {
        val record = saved()
        val result = executor.markSafe(record)
        assertTrue(result.complete)
        assertTrue(log.rows.isEmpty())
        assertEquals(0, blocker.unblockCalls)
        assertEquals(0, inbox.restored.size)
        assertTrue(history.byId(record.id)?.markedSafeAtMillis != null)
    }

    @Test
    fun aFailedActionIsNotRevertedBecauseNothingHappened() {
        blocker.available = false
        val record = saved()
        executor.perform(record, ActionType.BlockSender)
        executor.markSafe(record)
        assertEquals(0, blocker.unblockCalls)
        assertEquals(ActionStatus.Failed, latest(record, ActionType.BlockSender).status)
    }

    @Test
    fun afterMarkingAsSafeThePersonCanActAgainAndItIsRecordedAsANewAttempt() {
        val record = saved()
        executor.perform(record, ActionType.BlockSender)
        executor.markSafe(record)
        assertEquals(ActionStatus.Completed, executor.perform(record, ActionType.BlockSender))
        assertEquals(
            listOf(ActionStatus.Reverted, ActionStatus.Completed),
            log.actionsOf(record.id).map { it.status },
        )
    }

    // --- El ejecutor del modo automático (#13) ---

    @Test
    fun onlyTheActDecisionTriggersTheActions() {
        val mitigator = ApplyMitigation(executor)
        val record = saved()
        mitigator.apply(MitigationDecision.AskUser, record)
        mitigator.apply(MitigationDecision.DoNothing, record)
        assertTrue(log.rows.isEmpty())

        mitigator.apply(MitigationDecision.Act, record)
        assertEquals(2, log.rows.size)
    }

    // --- Lo que no se registra ---

    @Test
    fun theActionDataNeverCarriesTheMessageTextOrTheSender() {
        val record = saved()
        executor.act(record)
        val text = log.rows.toString()
        assertFalse(unsaved.body in text)
        assertFalse(unsaved.sender in text)
    }
}
