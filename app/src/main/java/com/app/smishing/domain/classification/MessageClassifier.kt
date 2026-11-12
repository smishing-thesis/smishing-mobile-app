package com.app.smishing.domain.classification

/** Clasifica el texto de un SMS sin salir del teléfono. */
interface MessageClassifier {
    fun classify(message: String): ClassificationResult

    /** Si la política de entrada deja evaluar este texto (no es demasiado corto), sin puntuarlo. */
    fun accepts(message: String): Boolean = true
}

/** Puntúa una entrada ya preprocesada. Lo implementa el modelo; en las pruebas, un sustituto. */
fun interface ModelScorer {
    fun score(inputIds: List<Int>): Float
}
