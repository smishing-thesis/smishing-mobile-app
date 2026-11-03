package com.app.smishing.domain.preprocessing

import org.json.JSONArray
import org.json.JSONObject

/**
 * Compara el preprocesamiento de la app con `preprocessing_vectors.json`, generado con Python.
 * Lo usan las pruebas de la JVM y las del dispositivo, para que ambas midan lo mismo.
 */
object PreprocessingVectorsChecker {

    /** Categorías que el criterio de aceptación de #33 exige cubrir. */
    val REQUIRED_CATEGORIES = listOf(
        "homoglyph", "emoji", "fullwidth", "zero_width", "line_breaks_tabs", "long_text", "abbreviation",
    )

    /** Devuelve las discrepancias; una lista vacía significa que todos los casos coinciden. */
    fun check(config: PreprocessingConfig, vectorsJson: String): List<String> {
        val vectors = JSONObject(vectorsJson)
        val problems = mutableListOf<String>()

        if (vectors.getString("preprocessing_version") != config.version) {
            problems += "versión: vectores ${vectors.getString("preprocessing_version")} ≠ config ${config.version}"
        }
        if (vectors.getString("spec_sha256") != config.specSha256) {
            problems += "spec_sha256 de los vectores no coincide con el del JSON"
        }

        val preprocessor = TextPreprocessor(config)
        val cases = vectors.getJSONArray("cases")
        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            val id = case.getString("id")
            val result = preprocessor.preprocess(case.getString("message"))

            fun compare(stage: String, expected: Any, actual: Any) {
                if (expected != actual) problems += "$id · $stage: esperado «$expected», obtenido «$actual»"
            }
            compare("cleaned", case.getString("cleaned"), result.cleaned)
            compare("normalized", case.getString("normalized"), result.normalized)
            compare("masked", case.getString("masked"), result.masked)
            compare("cleaned_chars", case.getInt("cleaned_chars"), result.cleanedChars)
            compare("classify", case.getBoolean("classify"), result.classify)
            compare("input_ids", case.getJSONArray("input_ids").toIntList(), result.inputIds)
        }
        return problems
    }

    fun categories(vectorsJson: String): Set<String> {
        val cases = JSONObject(vectorsJson).getJSONArray("cases")
        return (0 until cases.length()).mapTo(mutableSetOf()) { cases.getJSONObject(it).getString("category") }
    }

    fun caseCount(vectorsJson: String): Int = JSONObject(vectorsJson).getJSONArray("cases").length()

    private fun JSONArray.toIntList(): List<Int> = List(length()) { getInt(it) }
}
