package com.app.smishing.domain.classification

import com.app.smishing.domain.preprocessing.TextPreprocessor

/**
 * Une el preprocesamiento (#33) y el modelo (#34): aplica la política de entrada, puntúa y decide con el
 * umbral del JSON. No usa red ni conoce LiteRT.
 */
class LocalMessageClassifier(
    private val preprocessor: TextPreprocessor,
    private val scorer: ModelScorer,
    private val modelInfo: ModelInfo,
) : MessageClassifier {

    override fun accepts(message: String): Boolean = preprocessor.preprocess(message).classify

    override fun classify(message: String): ClassificationResult {
        val input = preprocessor.preprocess(message)
        if (!input.classify) {
            return ClassificationResult.NotClassified(ClassificationResult.NotClassified.Reason.TOO_SHORT)
        }
        val score = scorer.score(input.inputIds)
        return ClassificationResult.Classified(
            score = score,
            isSmishing = modelInfo.isSmishing(score),
            threshold = modelInfo.threshold,
            modelVersion = modelInfo.version,
            preprocessingVersion = input.preprocessingVersion,
        )
    }
}
