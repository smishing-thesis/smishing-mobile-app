package com.app.smishing.domain.mitigation

import com.app.smishing.domain.mitigation.MitigationDecision.Act
import com.app.smishing.domain.mitigation.MitigationDecision.AskUser
import com.app.smishing.domain.mitigation.MitigationDecision.DoNothing
import com.app.smishing.domain.risk.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MitigationPolicyTest {

    @Test
    fun aHighRiskMessageFollowsTheChosenMode() {
        assertEquals(Act, MitigationPolicy.decide(RiskLevel.High, MitigationMode.Automatic))
        assertEquals(AskUser, MitigationPolicy.decide(RiskLevel.High, MitigationMode.Ask))
    }

    @Test
    fun mediumAndLowNeverTriggerAnActionWhateverTheMode() {
        for (level in listOf(RiskLevel.Medium, RiskLevel.Low)) for (mode in MitigationMode.entries) {
            assertEquals("$level con $mode", DoNothing, MitigationPolicy.decide(level, mode))
        }
    }

    @Test
    fun onlyTheHighLevelCanBeActedOn() {
        assertEquals(setOf(RiskLevel.High), MitigationPolicy.ACTIONABLE_LEVELS)
    }

    @Test
    fun theWholeTableHasAnAnswerAndOnlyAutomaticActsWithoutAsking() {
        for (level in RiskLevel.entries) for (mode in MitigationMode.entries) {
            val decision = MitigationPolicy.decide(level, mode)
            if (decision == Act) assertTrue("Solo actúa sin preguntar el modo automático con riesgo alto", mode == MitigationMode.Automatic && level == RiskLevel.High)
        }
    }

    // --- El modo guardado ---

    @Test
    fun theDefaultModeAsksBecauseTheAutomaticOneRemovesMessages() {
        assertEquals(MitigationMode.Ask, MitigationMode.DEFAULT)
        assertEquals(MitigationMode.Ask, MitigationMode.fromId(null))
    }

    @Test
    fun aSavedIdComesBackAsItsModeAndAnUnknownOneAsTheDefault() {
        for (mode in MitigationMode.entries) assertEquals(mode, MitigationMode.fromId(mode.id))
        assertEquals(MitigationMode.DEFAULT, MitigationMode.fromId("algo-que-no-existe"))
        assertEquals(MitigationMode.DEFAULT, MitigationMode.fromId(""))
    }

    @Test
    fun theStoredIdsAreStableAndUnique() {
        assertEquals(listOf("automatic", "ask"), MitigationMode.entries.map { it.id })
        assertEquals(MitigationMode.entries.size, MitigationMode.entries.map { it.id }.toSet().size)
    }
}
