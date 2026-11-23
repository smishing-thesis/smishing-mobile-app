package com.app.smishing.domain.classification

/**
 * El clasificador de cuando el modelo empaquetado se rechazó (#35): no evalúa nada, así que ningún mensaje se alerta ni se bloquea,
 * y la app sigue recibiendo, mostrando y enviando mensajes. Como [accepts] es `false`, la pantalla de mensajes no ofrece "Analizar".
 */
object UnavailableMessageClassifier : MessageClassifier {

    override fun accepts(message: String): Boolean = false

    override fun classify(message: String): ClassificationResult =
        ClassificationResult.NotClassified(ClassificationResult.NotClassified.Reason.MODEL_UNAVAILABLE)
}
