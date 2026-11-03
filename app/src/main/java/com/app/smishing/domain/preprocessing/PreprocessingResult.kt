package com.app.smishing.domain.preprocessing

/**
 * Salida del preprocesamiento. Las tres etapas de texto existen para ubicar dónde se separa de Python.
 * No debe escribirse en logs: contiene el texto del mensaje.
 */
data class PreprocessingResult(
    val preprocessingVersion: String,
    val cleaned: String,
    val normalized: String,
    val masked: String,
    /** Puntos de código del texto limpio, no unidades UTF-16. */
    val cleanedChars: Int,
    /** Falso si el mensaje es demasiado corto para el modelo: no se clasifica ni se alerta. */
    val classify: Boolean,
    val inputIds: List<Int>,
)
