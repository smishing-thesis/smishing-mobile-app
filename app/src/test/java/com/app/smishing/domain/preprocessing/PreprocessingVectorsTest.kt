package com.app.smishing.domain.preprocessing

import com.app.smishing.data.preprocessing.PreprocessingConfigParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas de #33 en la JVM: la app debe producir lo mismo que Python para cada vector. */
class PreprocessingVectorsTest {

    private val configJson = resource("preprocessing_config.json")
    private val vectorsJson = resource("preprocessing_vectors.json")
    private val config = PreprocessingConfigParser.parse(configJson)

    @Test
    fun everyVectorMatchesPython() {
        val problems = PreprocessingVectorsChecker.check(config, vectorsJson)
        assertTrue(
            "${problems.size} discrepancias de ${PreprocessingVectorsChecker.caseCount(vectorsJson)} casos:\n" +
                problems.joinToString("\n"),
            problems.isEmpty(),
        )
    }

    @Test
    fun vectorsCoverTheRequiredCategories() {
        val missing = PreprocessingVectorsChecker.REQUIRED_CATEGORIES - PreprocessingVectorsChecker.categories(vectorsJson)
        assertEquals("Faltan categorías en los vectores", emptyList<String>(), missing)
    }

    @Test
    fun resultRecordsThePreprocessingVersion() {
        val result = TextPreprocessor(config).preprocess("Tu codigo de verificacion es 458920.")
        assertEquals("1.1.0", result.preprocessingVersion)
    }

    @Test
    fun emptyMessageIsNotClassified() {
        val result = TextPreprocessor(config).preprocess("")
        assertEquals(false, result.classify)
        assertEquals(List(config.maxLength) { config.padIndex }, result.inputIds)
    }

    private fun resource(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) { "Falta el recurso $name" }
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
}
