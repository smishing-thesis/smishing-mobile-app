package com.app.smishing.domain.sharing

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * #19 en el dispositivo: la desidentificación da lo mismo que el anonimizador de referencia, con el motor de expresiones regulares de
 * Android (que no es el de la JVM de escritorio). Mismos vectores que la prueba de la JVM.
 */
@RunWith(AndroidJUnit4::class)
class AnonymizationVectorsInstrumentedTest {

    @Test
    fun everyVectorMatchesTheReferenceAnonymizerOnTheDevice() {
        val json = InstrumentationRegistry.getInstrumentation().context.assets.open("anonymization_vectors.json")
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        val problems = AnonymizationVectorsChecker.check(json)
        assertTrue("Discrepancias con el anonimizador de referencia:\n" + problems.joinToString("\n"), problems.isEmpty())
    }
}
