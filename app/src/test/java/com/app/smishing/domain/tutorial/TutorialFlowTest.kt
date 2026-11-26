package com.app.smishing.domain.tutorial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Cuándo avanza, termina o se omite el recorrido guiado (#88), y cuándo se guarda la decisión. */
class TutorialFlowTest {

    private val flow = TutorialFlow(stepCount = 3)

    @Test
    fun acceptingTheInviteStartsTheFirstStepAndSavesNothing() {
        val t = flow.next(TutorialPhase.Invite)
        assertEquals(TutorialPhase.Step(0), t.phase)
        assertNull("Aceptar a mitad no debe fijar la decisión", t.save)
    }

    @Test
    fun eachStepLeadsToTheNextAndTheLastOneToTheClosing() {
        assertEquals(TutorialPhase.Step(1), flow.next(TutorialPhase.Step(0)).phase)
        assertEquals(TutorialPhase.Step(2), flow.next(TutorialPhase.Step(1)).phase)
        val last = flow.next(TutorialPhase.Step(2))
        assertEquals(TutorialPhase.Closing, last.phase)
        assertNull(last.save)
    }

    @Test
    fun finishingTheClosingSavesDone() {
        val t = flow.next(TutorialPhase.Closing)
        assertEquals(TutorialPhase.Hidden, t.phase)
        assertEquals(TutorialState.Done, t.save)
    }

    @Test
    fun skippingFromTheInviteOrAnyStepSavesSkipped() {
        for (phase in listOf(TutorialPhase.Invite, TutorialPhase.Step(0), TutorialPhase.Step(2), TutorialPhase.Closing)) {
            val t = flow.skip(phase)
            assertEquals(TutorialPhase.Hidden, t.phase)
            assertEquals("Omitir desde $phase", TutorialState.Skipped, t.save)
        }
    }

    @Test
    fun nothingHappensWhileHidden() {
        assertEquals(TutorialTransition(TutorialPhase.Hidden), flow.next(TutorialPhase.Hidden))
        assertEquals(TutorialTransition(TutorialPhase.Hidden), flow.skip(TutorialPhase.Hidden))
    }

    @Test
    fun restartingGoesBackToPendingAndStartsAtOnce() {
        val t = flow.restart()
        assertEquals(TutorialPhase.Step(0), t.phase)
        assertEquals(TutorialState.Pending, t.save)
    }

    @Test
    fun aSavedIdComesBackAsTheSameStateAndAnythingElseIsPending() {
        for (state in TutorialState.entries) assertEquals(state, TutorialState.fromId(state.id))
        assertEquals(TutorialState.Pending, TutorialState.fromId(null))
        assertEquals(TutorialState.Pending, TutorialState.fromId("algo-que-no-existe"))
    }

    @Test
    fun theIdsAreStable() {
        assertEquals(listOf("pending", "done", "skipped"), TutorialState.entries.map { it.id })
    }
}
