package com.app.smishing.domain.classification

import com.app.smishing.domain.classification.ClassificationResult.NotClassified
import com.app.smishing.domain.risk.RiskAssessor
import com.app.smishing.domain.risk.RiskResult
import com.app.smishing.domain.signals.SignalAnalysis
import com.app.smishing.domain.signals.SignalDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class UnavailableMessageClassifierTest {

    @Test
    fun noMessageIsEvaluatedAndNoneIsOfferedForAnalysis() {
        // Con el modelo rechazado (#35) la app no analiza nada ni ofrece "Analizar", pero tampoco falla.
        assertEquals(NotClassified(NotClassified.Reason.MODEL_UNAVAILABLE), UnavailableMessageClassifier.classify("Mensaje de prueba largo"))
        assertFalse(UnavailableMessageClassifier.accepts("Mensaje de prueba largo"))

        val signals = object : SignalDetector {
            override fun detect(message: String): SignalAnalysis = error("No debía buscar señales")
        }
        val assessor = RiskAssessor(UnavailableMessageClassifier, signals)
        assertEquals(RiskResult.NotAssessed(NotClassified.Reason.MODEL_UNAVAILABLE), assessor.assess("Mensaje de prueba largo"))
        assertFalse(assessor.canAssess("Mensaje de prueba largo"))
    }
}
