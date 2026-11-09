package com.app.smishing.domain.classification

import com.app.smishing.domain.classification.ClassificationResult.Classified
import com.app.smishing.domain.classification.ClassificationResult.NotClassified
import com.app.smishing.domain.sms.AnalysisGate
import com.app.smishing.domain.sms.AnalysisStatus
import com.app.smishing.domain.sms.SuspensionReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** #3: con el análisis suspendido no se llama al modelo; al reanudarse, se vuelve a analizar. */
class GatedMessageClassifierTest {

    private class FakeGate(var status: AnalysisStatus) : AnalysisGate {
        override fun status() = status
    }

    private class CountingClassifier(private val result: ClassificationResult) : MessageClassifier {
        var calls = 0

        override fun classify(message: String): ClassificationResult {
            calls++
            return result
        }
    }

    private val classified = Classified(score = 0.95f, isSmishing = true, threshold = 0.9, modelVersion = "1.0.0", preprocessingVersion = "1.1.0")
    private val suspended = AnalysisStatus.Suspended(setOf(SuspensionReason.PermissionsNotGranted))

    @Test
    fun activeAnalysisReturnsTheClassifierResult() {
        val delegate = CountingClassifier(classified)
        val gated = GatedMessageClassifier(delegate, FakeGate(AnalysisStatus.Active))

        assertEquals(classified, gated.classify("mensaje"))
        assertEquals(1, delegate.calls)
    }

    @Test
    fun suspendedAnalysisDoesNotCallTheModel() {
        val delegate = CountingClassifier(classified)
        val gated = GatedMessageClassifier(delegate, FakeGate(suspended))

        assertEquals(NotClassified(NotClassified.Reason.ANALYSIS_SUSPENDED), gated.classify("mensaje"))
        assertEquals("El modelo no debe usarse con el análisis suspendido", 0, delegate.calls)
    }

    @Test
    fun analysisSuspendsAndResumesAsThePermissionsChange() {
        val delegate = CountingClassifier(classified)
        val gate = FakeGate(AnalysisStatus.Active)
        val gated = GatedMessageClassifier(delegate, gate)

        assertTrue(gated.classify("uno") is Classified)

        gate.status = suspended
        assertEquals(NotClassified(NotClassified.Reason.ANALYSIS_SUSPENDED), gated.classify("dos"))

        gate.status = AnalysisStatus.Active
        assertTrue(gated.classify("tres") is Classified)
        assertEquals("Solo el primero y el tercero llegan al modelo", 2, delegate.calls)
    }
}
