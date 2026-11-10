package com.app.smishing.domain.mitigation

import com.app.smishing.domain.classification.ClassificationResult
import com.app.smishing.domain.history.AnalysisRecord
import com.app.smishing.domain.risk.RiskPolicy
import com.app.smishing.domain.signals.SignalAnalysis
import org.junit.Assert.assertEquals
import org.junit.Test

/** Lo que el historial dice de las acciones sobre cada mensaje (#28). */
class HistoryMarksTest {

    private val record = AnalysisRecord(
        id = 1, messageId = 1, sender = "+10000000001", body = "Texto sintético", receivedAtMillis = 1, analyzedAtMillis = 1,
        assessment = RiskPolicy.decide(ClassificationResult.Classified(0.97f, true, 0.9, "1.0.0", "1.1.0"), SignalAnalysis("1.1.0", emptyList())),
    )

    private var nextId = 0L
    private fun action(type: ActionType, status: ActionStatus, note: ActionNote? = null) =
        MitigationAction(++nextId, 1, type, status, note, 1, 1)

    @Test
    fun aMessageWithoutActionsHasNoMarks() {
        assertEquals(emptyList<HistoryMark>(), historyMarks(record, emptyList()))
    }

    @Test
    fun completedActionsAreShownAsDone() {
        val marks = historyMarks(
            record,
            listOf(action(ActionType.BlockSender, ActionStatus.Completed), action(ActionType.RemoveMessage, ActionStatus.Completed)),
        )
        assertEquals(listOf(HistoryMark.Blocked, HistoryMark.Removed), marks)
    }

    @Test
    fun aFailedActionIsShownAsFailedAndNeverAsDone() {
        val marks = historyMarks(
            record,
            listOf(action(ActionType.BlockSender, ActionStatus.Failed, ActionNote.NotSupported), action(ActionType.RemoveMessage, ActionStatus.Completed)),
        )
        assertEquals(listOf(HistoryMark.Removed, HistoryMark.ActionFailed), marks)
    }

    @Test
    fun aRequestedActionIsShownAsPending() {
        assertEquals(listOf(HistoryMark.ActionPending), historyMarks(record, listOf(action(ActionType.BlockSender, ActionStatus.Requested))))
    }

    @Test
    fun onlyTheLatestAttemptOfEachTypeCounts() {
        val marks = historyMarks(
            record,
            listOf(action(ActionType.BlockSender, ActionStatus.Failed, ActionNote.Error), action(ActionType.BlockSender, ActionStatus.Completed)),
        )
        assertEquals(listOf(HistoryMark.Blocked), marks)
    }

    @Test
    fun anActionThatCouldNotBeRevertedIsShownAsFailedWhileItIsStillInEffect() {
        val marks = historyMarks(record, listOf(action(ActionType.BlockSender, ActionStatus.Completed, ActionNote.RevertFailed)))
        assertEquals(listOf(HistoryMark.Blocked, HistoryMark.ActionFailed), marks)
    }

    @Test
    fun aMessageMarkedAsSafeShowsOnlyThat() {
        val safe = record.copy(markedSafeAtMillis = 5)
        val marks = historyMarks(safe, listOf(action(ActionType.BlockSender, ActionStatus.Reverted)))
        assertEquals(listOf(HistoryMark.MarkedSafe), marks)
    }

    @Test
    fun aRevertedActionLeavesNoMark() {
        assertEquals(emptyList<HistoryMark>(), historyMarks(record, listOf(action(ActionType.BlockSender, ActionStatus.Reverted))))
    }

    @Test
    fun theStoredIdsAreStableAndUnique() {
        assertEquals(listOf("block", "remove"), ActionType.entries.map { it.id })
        assertEquals(listOf("requested", "completed", "failed", "reverted"), ActionStatus.entries.map { it.id })
        for (e in ActionNote.entries) assertEquals(e, ActionNote.fromId(e.id))
        assertEquals(ActionNote.entries.size, ActionNote.entries.map { it.id }.toSet().size)
    }
}
