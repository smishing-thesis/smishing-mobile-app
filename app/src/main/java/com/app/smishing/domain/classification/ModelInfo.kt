package com.app.smishing.domain.classification

/** Lo que `preprocessing_config.json` declara del modelo: versión, umbral y hash del `.tflite`. */
data class ModelInfo(
    val version: String,
    val threshold: Double,
    val tfliteSha256: String,
) {
    /** Regla del JSON: sospechoso si el puntaje supera el umbral. No se usa 0.5. */
    fun isSmishing(score: Float): Boolean = score.toDouble() > threshold

    companion object {
        /**
         * Versión mayor del modelo que esta app sabe usar (#35). Una versión menor o de parche (`1.0.1`, `1.1.0`) mantiene la entrada y la
         * salida, así que entra con una actualización de la app; una mayor (`2.0.0`) puede cambiarlas y exige revisar el código.
         */
        const val SUPPORTED_MAJOR = 1

        private val VERSION = Regex("""(\d+)\.\d+\.\d+""")

        /** Si [version] tiene la forma `mayor.menor.parche` y su versión mayor es la [SUPPORTED_MAJOR]. */
        fun isSupportedVersion(version: String): Boolean =
            VERSION.matchEntire(version)?.groupValues?.get(1)?.toIntOrNull() == SUPPORTED_MAJOR
    }
}

/** El JSON declara un modelo que esta versión de la app no sabe usar: no se carga (#35). */
class UnsupportedModelException(message: String) : IllegalArgumentException(message)
