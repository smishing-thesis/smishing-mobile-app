package com.app.smishing.domain.risk

import com.app.smishing.domain.classification.ClassificationResult
import com.app.smishing.domain.classification.ClassificationResult.NotClassified.Reason
import com.app.smishing.domain.classification.MessageClassifier
import com.app.smishing.domain.signals.DetectedSignal
import com.app.smishing.domain.signals.SignalAnalysis
import com.app.smishing.domain.signals.SignalDetector
import com.app.smishing.domain.signals.SignalType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskAssessorTest {

    private class FakeClassifier(private val result: ClassificationResult) : MessageClassifier {
        override fun classify(message: String) = result
    }

    private class FakeDetector(private val analysis: SignalAnalysis) : SignalDetector {
        var calls = 0
        override fun detect(message: String): SignalAnalysis {
            calls++
            return analysis
        }
    }

    private val classified = ClassificationResult.Classified(0.97f, true, 0.9, "1.0.0", "1.1.0")
    private val analysis = SignalAnalysis("1.1.0", listOf(DetectedSignal(SignalType.Urgency, "urgency.x", "texto")))

    @Test
    fun aClassifiedMessageGetsALevelFromTheModelAndTheSignals() {
        val detector = FakeDetector(analysis)
        val result = RiskAssessor(FakeClassifier(classified), detector).assess("mensaje")

        assertTrue(result is RiskResult.Assessed)
        result as RiskResult.Assessed
        assertEquals(RiskLevel.High, result.assessment.level)
        assertEquals(classified, result.assessment.model)
        assertEquals(analysis, result.assessment.signals)
        assertEquals(1, detector.calls)
    }

    @Test
    fun aMessageTheModelDidNotEvaluateGetsNoLevelAndNoSignalsAreLookedFor() {
        for (reason in Reason.entries) {
            val detector = FakeDetector(analysis)
            val result = RiskAssessor(FakeClassifier(ClassificationResult.NotClassified(reason)), detector).assess("mensaje")

            assertEquals(RiskResult.NotAssessed(reason), result)
            assertEquals("No debía buscar señales si el modelo no evaluó", 0, detector.calls)
        }
    }
}
