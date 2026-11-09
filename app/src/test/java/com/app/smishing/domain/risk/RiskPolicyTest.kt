package com.app.smishing.domain.risk

import com.app.smishing.domain.classification.ClassificationResult
import com.app.smishing.domain.signals.DetectedSignal
import com.app.smishing.domain.signals.SignalAnalysis
import com.app.smishing.domain.signals.SignalType
import com.app.smishing.domain.signals.SignalType.DataRequest
import com.app.smishing.domain.signals.SignalType.Impersonation
import com.app.smishing.domain.signals.SignalType.Link
import com.app.smishing.domain.signals.SignalType.Urgency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskPolicyTest {

    private fun model(smishing: Boolean, score: Float = if (smishing) 0.97f else 0.12f) = ClassificationResult.Classified(
        score = score,
        isSmishing = smishing,
        threshold = 0.9034745693206787,
        modelVersion = "1.0.0",
        preprocessingVersion = "1.1.0",
    )

    private fun signals(vararg types: SignalType) =
        SignalAnalysis("1.1.0", types.map { DetectedSignal(it, "test.${it.name.lowercase()}", "fragmento secreto") })

    private fun decide(smishing: Boolean, vararg types: SignalType) = RiskPolicy.decide(model(smishing), signals(*types))

    // --- Los casos de la política ---

    @Test
    fun theModelFlagsItAndASignalBacksItIsHigh() {
        for (type in listOf(Urgency, Impersonation, DataRequest)) {
            val result = decide(true, type)
            assertEquals("con $type", RiskLevel.High, result.level)
            assertEquals(RiskRule.ModelAndEvidence, result.rule)
        }
        assertEquals(RiskLevel.High, decide(true, Link, Urgency, Impersonation, DataRequest).level)
    }

    @Test
    fun theModelFlagsItWithoutBackingIsMedium() {
        val none = decide(true)
        assertEquals(RiskLevel.Medium, none.level)
        assertEquals(RiskRule.ModelOnly, none.rule)
        // Un enlace solo no es respaldo.
        val linkOnly = decide(true, Link)
        assertEquals(RiskLevel.Medium, linkOnly.level)
        assertEquals(RiskRule.ModelOnly, linkOnly.rule)
    }

    @Test
    fun strongSignalsAloneAreMediumWhenTheyAreImpersonationOrTwoOrMore() {
        assertEquals(RiskRule.SignalsOnly, decide(false, Impersonation).rule)
        assertEquals(RiskLevel.Medium, decide(false, Impersonation).level)
        assertEquals(RiskRule.SignalsOnly, decide(false, Urgency, DataRequest).rule)
        assertEquals(RiskLevel.Medium, decide(false, Urgency, DataRequest, Link).level)
    }

    @Test
    fun noEvidenceIsLow() {
        for (types in listOf(emptyList(), listOf(Link), listOf(Urgency), listOf(DataRequest), listOf(Urgency, Link))) {
            val result = decide(false, *types.toTypedArray())
            assertEquals("con $types", RiskLevel.Low, result.level)
            assertEquals(RiskRule.NoEvidence, result.rule)
        }
    }

    @Test
    fun aLinkAloneNeverCounts() {
        assertEquals(RiskLevel.Low, decide(false, Link).level)
        assertFalse(Link in decide(true, Link, Urgency).evidence)
        assertEquals(setOf(Urgency), decide(true, Link, Urgency).evidence)
    }

    // --- Propiedades de toda la tabla ---

    private val allSignalSets: List<Set<SignalType>> =
        (0 until 16).map { mask -> SignalType.entries.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.toSet() }

    @Test
    fun theDecisionIsDeterministic() {
        for (flag in listOf(true, false)) for (set in allSignalSets) {
            assertEquals(decide(flag, *set.toTypedArray()), decide(flag, *set.toTypedArray()))
        }
    }

    @Test
    fun addingASignalOrAModelFlagNeverLowersTheLevel() {
        for (flag in listOf(true, false)) for (small in allSignalSets) for (type in SignalType.entries) {
            val bigger = small + type
            assertTrue(
                "Agregar $type a $small con modelo=$flag bajó el nivel",
                decide(flag, *bigger.toTypedArray()).level >= decide(flag, *small.toTypedArray()).level,
            )
        }
        for (set in allSignalSets) {
            assertTrue(
                "Que el modelo marque con $set bajó el nivel",
                decide(true, *set.toTypedArray()).level >= decide(false, *set.toTypedArray()).level,
            )
        }
    }

    @Test
    fun everyCombinationGivesALevelFromItsRuleAndHighNeverComesWithoutTheModel() {
        for (flag in listOf(true, false)) for (set in allSignalSets) {
            val result = decide(flag, *set.toTypedArray())
            assertEquals(result.rule.level, result.level)
            if (!flag) assertTrue("Sin el modelo nunca es alto: $set", result.level != RiskLevel.High)
        }
    }

    // --- Trazabilidad ---

    @Test
    fun theAssessmentKeepsEverythingNeededToExplainIt() {
        val classified = model(true, score = 0.95f)
        val analysis = signals(Urgency, Link, DataRequest)
        val result = RiskPolicy.decide(classified, analysis)

        assertEquals(classified, result.model)
        assertEquals(0.95f, result.model.score)
        assertEquals(0.9034745693206787, result.model.threshold, 0.0)
        assertEquals("1.0.0", result.model.modelVersion)
        assertEquals("1.1.0", result.signals.rulesVersion)
        assertEquals(analysis, result.signals)
        assertEquals(setOf(Urgency, DataRequest), result.evidence)
        assertEquals("1.0.0", result.policyVersion)
        assertEquals("risk.model_and_evidence", result.rule.id)
    }

    @Test
    fun everyRuleHasAnIdThatIsStableAndUnique() {
        assertEquals(RiskRule.entries.size, RiskRule.entries.map { it.id }.toSet().size)
        assertTrue(RiskRule.entries.all { it.id.startsWith("risk.") })
    }

    @Test
    fun theStringFormNeverShowsTheFragments() {
        val text = decide(true, Urgency, Link).toString()
        assertFalse("fragmento secreto" in text)
    }
}
