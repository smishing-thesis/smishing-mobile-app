package com.app.smishing.ui.designsystem.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** La animación del Smishy de Inicio: en bucle, debe empezar y terminar en el Smishy normal para que no dé un salto al repetirse. */
class SmishyIdleTimelineTest {

    private val normal = SmishyFrame(gazeX = 2f, eyeOpen = 1f, accessories = 0f, smile = 1f)

    @Test
    fun itStartsAndEndsAsTheNormalSmishySoTheLoopHasNoJump() {
        assertEquals(normal, SmishyIdleTimeline.frameAt(0f))
        assertEquals(normal, SmishyIdleTimeline.frameAt(SmishyIdleTimeline.DURATION_MS.toFloat()))
    }

    @Test
    fun itBlinksAndGlancesToTheSidesAndNeverWearsTheGlasses() {
        assertTrue(SmishyIdleTimeline.frameAt(930f).eyeOpen < 0.5f)
        assertTrue(SmishyIdleTimeline.frameAt(2200f).gazeX > 4f)
        assertTrue(SmishyIdleTimeline.frameAt(4700f).gazeX < 0f)
        for (t in 0..SmishyIdleTimeline.DURATION_MS step 50) {
            val frame = SmishyIdleTimeline.frameAt(t.toFloat())
            assertEquals(0f, frame.accessories)
            assertEquals(1f, frame.smile)
        }
    }
}
