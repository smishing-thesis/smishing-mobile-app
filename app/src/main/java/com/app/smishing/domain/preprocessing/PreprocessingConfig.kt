package com.app.smishing.domain.preprocessing

/**
 * Reglas de preprocesamiento tal como las define `preprocessing_config.json` del repo del modelo.
 * Nada de esto se copia al código: si el JSON cambia sin cambiar de versión, no hay que tocar Kotlin.
 */
data class PreprocessingConfig(
    val version: String,
    val specSha256: String,
    val normalizationVersion: String,
    val maxLength: Int,
    val padIndex: Int,
    val unknownIndex: Int,
    val minCleanedChars: Int,
    val modelAlphabet: Set<String>,
    val charToIndex: Map<String, Int>,
    val cleaningSteps: List<CleaningStep>,
    val confusables: Map<String, String>,
    val maskingRules: List<MaskingRule>,
) {
    companion object {
        /** Versiones del preprocesamiento que esta app sabe replicar. */
        val SUPPORTED_VERSIONS = setOf("1.1.0")
        const val SUPPORTED_NORMALIZATION_VERSION = "2"
        const val SUPPORTED_UNICODE_FORM = "NFKC"
    }
}

sealed interface CleaningStep {
    val name: String

    data class RegexReplace(override val name: String, val pattern: String, val replacement: String) : CleaningStep

    data class Translate(override val name: String, val mapping: Map<String, String>) : CleaningStep
}

/** Una regla de enmascarado; sin patrón solo existe `lowercase`. */
data class MaskingRule(val name: String, val pattern: String?, val replacement: String?)

/** El JSON declara una versión o una regla que esta app no conoce. */
class UnsupportedPreprocessingException(message: String) : IllegalArgumentException(message)
