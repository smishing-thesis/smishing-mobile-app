package com.app.smishing.domain.risk

import com.app.smishing.domain.signals.SignalType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RecommendationTest {

    @Test
    fun theLinkAdviceIsOnlyForMessagesThatHaveALink() {
        assertEquals(Recommendation.Link, Recommendation.of(setOf(SignalType.Link)))
        assertEquals(Recommendation.Link, Recommendation.of(setOf(SignalType.Link, SignalType.Urgency)))
        for (types in listOf(emptySet(), setOf(SignalType.Urgency), setOf(SignalType.Impersonation), setOf(SignalType.DataRequest))) {
            assertNotEquals("Sin enlace no se aconseja no abrirlo: $types", Recommendation.Link, Recommendation.of(types))
        }
    }

    @Test
    fun aMessageThatAsksForDataGetsTheDataAdviceAndBothWhenItAlsoHasALink() {
        assertEquals(Recommendation.Data, Recommendation.of(setOf(SignalType.DataRequest, SignalType.Urgency)))
        assertEquals(Recommendation.LinkAndData, Recommendation.of(setOf(SignalType.DataRequest, SignalType.Link)))
    }

    @Test
    fun withNothingConcreteTheGeneralAdviceApplies() {
        assertEquals(Recommendation.General, Recommendation.of(emptySet()))
        assertEquals(Recommendation.General, Recommendation.of(setOf(SignalType.Urgency, SignalType.Impersonation)))
    }
}
