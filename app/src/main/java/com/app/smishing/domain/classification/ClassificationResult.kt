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
        /** El umbral con el que se decidió `isSmishing` (el del JSON, no 0.5): es parte de lo que hace trazable el resultado. */
        val threshold: Double,
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

            /** El modelo empaquetado se rechazó al arrancar (versión incompatible o archivo que no es el declarado): no se analiza (#35). */
            MODEL_UNAVAILABLE,
        }
    }
}
