package com.app.smishing.domain.classification

/** Clasifica el texto de un SMS sin salir del teléfono. */
interface MessageClassifier {
    fun classify(message: String): ClassificationResult
}

/** Puntúa una entrada ya preprocesada. Lo implementa el modelo; en las pruebas, un sustituto. */
fun interface ModelScorer {
    fun score(inputIds: List<Int>): Float
}
