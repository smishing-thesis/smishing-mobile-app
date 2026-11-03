package com.app.smishing.domain.classification

/** Lo que `preprocessing_config.json` declara del modelo: versión, umbral y hash del `.tflite`. */
data class ModelInfo(
    val version: String,
    val threshold: Double,
    val tfliteSha256: String,
) {
    /** Regla del JSON: sospechoso si el puntaje supera el umbral. No se usa 0.5. */
    fun isSmishing(score: Float): Boolean = score.toDouble() > threshold
}
