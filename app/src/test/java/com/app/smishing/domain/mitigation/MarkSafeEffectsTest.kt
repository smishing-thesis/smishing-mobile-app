package com.app.smishing.domain.mitigation

import org.junit.Assert.assertEquals
import org.junit.Test

/** Lo que "Este mensaje es seguro" va a deshacer de verdad, para que el aviso no prometa lo que no ocurrió (#102). */
class MarkSafeEffectsTest {

    private var nextId = 0L
    private fun action(type: ActionType, status: ActionStatus, note: ActionNote? = null) =
        MitigationAction(++nextId, 1, type, status, note, 1, 1)

    private fun undone(vararg actions: MitigationAction) = undoneOnMarkSafe(actions.associateBy { it.type })

    @Test
    fun withoutActionsThereIsNothingToUndo() {
        // Es lo que pasa con un mensaje de riesgo medio o con un modo que solo avisa.
        assertEquals(emptySet<ActionType>(), undone())
    }

    @Test
    fun completedActionsAreUndone() {
        assertEquals(setOf(ActionType.BlockSender), undone(action(ActionType.BlockSender, ActionStatus.Completed)))
        assertEquals(setOf(ActionType.RemoveMessage), undone(action(ActionType.RemoveMessage, ActionStatus.Completed)))
        assertEquals(
            setOf(ActionType.BlockSender, ActionType.RemoveMessage),
            undone(action(ActionType.BlockSender, ActionStatus.Completed), action(ActionType.RemoveMessage, ActionStatus.Completed)),
        )
    }

    @Test
    fun failedRequestedAndAlreadyRevertedActionsHaveNothingToUndo() {
        assertEquals(
            emptySet<ActionType>(),
            undone(
                action(ActionType.BlockSender, ActionStatus.Failed, ActionNote.Error),
                action(ActionType.RemoveMessage, ActionStatus.Reverted),
            ),
        )
        assertEquals(emptySet<ActionType>(), undone(action(ActionType.BlockSender, ActionStatus.Requested)))
    }

    @Test
    fun aBlockThatAlreadyExistedBeforeTheAppIsNotUndone() {
        assertEquals(emptySet<ActionType>(), undone(action(ActionType.BlockSender, ActionStatus.Completed, ActionNote.AlreadyApplied)))
    }

    @Test
    fun anActionWhoseUndoFailedBeforeIsTriedAgain() {
        assertEquals(
            setOf(ActionType.RemoveMessage),
            undone(action(ActionType.RemoveMessage, ActionStatus.Completed, ActionNote.RevertFailed)),
        )
    }
}
