package com.app.smishing.domain.preprocessing

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.app.smishing.data.preprocessing.PreprocessingConfigLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Los mismos vectores de #33, pero con las bibliotecas de Android (ICU, org.json) y el JSON
 * empaquetado en la app, que es lo que corre en el teléfono.
 */
@RunWith(AndroidJUnit4::class)
class PreprocessingVectorsInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun packagedConfigLoadsFromAssets() {
        val config = PreprocessingConfigLoader(instrumentation.targetContext).load()
        assertEquals("1.1.0", config.version)
    }

    @Test
    fun everyVectorMatchesPythonOnDevice() {
        val config = PreprocessingConfigLoader(instrumentation.targetContext).load()
        val vectorsJson = instrumentation.context.assets.open("preprocessing_vectors.json")
            .bufferedReader(Charsets.UTF_8).use { it.readText() }

        val problems = PreprocessingVectorsChecker.check(config, vectorsJson)
        assertTrue(
            "${problems.size} discrepancias de ${PreprocessingVectorsChecker.caseCount(vectorsJson)} casos:\n" +
                problems.joinToString("\n"),
            problems.isEmpty(),
        )
    }
}
