package com.app.smishing.domain.risk

import com.app.smishing.domain.classification.ClassificationResult
import com.app.smishing.domain.classification.MessageClassifier
import com.app.smishing.domain.signals.SignalDetector

/**
 * Evalúa el riesgo de un SMS (#9): clasifica con el modelo, busca las señales y aplica la [RiskPolicy].
 *
 * Si el modelo no evalúa el mensaje (muy corto, o análisis suspendido con `GatedMessageClassifier`), no hay nivel y no se
 * buscan señales: la política de entrada del modelo dice que no se alerta ni se bloquea.
 */
class RiskAssessor(
    private val classifier: MessageClassifier,
    private val signalDetector: SignalDetector,
) {

    fun assess(message: String): RiskResult = when (val classification = classifier.classify(message)) {
        is ClassificationResult.NotClassified -> RiskResult.NotAssessed(classification.reason)
        is ClassificationResult.Classified ->
            RiskResult.Assessed(RiskPolicy.decide(classification, signalDetector.detect(message)))
    }
}
