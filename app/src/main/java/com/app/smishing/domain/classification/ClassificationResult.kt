package com.app.smishing.domain.classification

/** Resultado de clasificar un SMS en el teléfono. */
sealed interface ClassificationResult {

    /**
     * El modelo evaluó el mensaje. Las dos versiones se guardan con el resultado para poder
     * reproducirlo después (RNF-07).
     */
    data class Classified(
        val score: Float,
        val isSmishing: Boolean,
        val modelVersion: String,
        val preprocessingVersion: String,
    ) : ClassificationResult

    /** El mensaje no llegó al modelo: no se alerta ni se bloquea. */
    data class NotClassified(val reason: Reason) : ClassificationResult {
        enum class Reason {
            /** Menos caracteres limpios que el mínimo de la política de entrada del modelo. */
            TOO_SHORT,

            /** Falta el rol de SMS o algún permiso: el análisis está suspendido (#3). */
            ANALYSIS_SUSPENDED,
        }
    }
}
