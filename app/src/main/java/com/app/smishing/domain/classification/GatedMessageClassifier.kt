package com.app.smishing.domain.classification

import com.app.smishing.domain.sms.AnalysisGate
import com.app.smishing.domain.sms.AnalysisStatus

/**
 * Suspende el análisis cuando falta el rol de SMS o algún permiso: no llama al modelo y devuelve
 * [ClassificationResult.NotClassified.Reason.ANALYSIS_SUSPENDED]. Consulta la compuerta en cada mensaje.
 */
class GatedMessageClassifier(
    private val delegate: MessageClassifier,
    private val gate: AnalysisGate,
) : MessageClassifier {

    override fun classify(message: String): ClassificationResult = when (gate.status()) {
        is AnalysisStatus.Suspended ->
            ClassificationResult.NotClassified(ClassificationResult.NotClassified.Reason.ANALYSIS_SUSPENDED)
        AnalysisStatus.Active -> delegate.classify(message)
    }
}
