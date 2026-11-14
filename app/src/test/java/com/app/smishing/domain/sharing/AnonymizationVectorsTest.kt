package com.app.smishing.domain.sharing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** La desidentificación de la app da lo mismo que el anonimizador de referencia (#19), caso por caso. */
class AnonymizationVectorsTest {

    private val json = checkNotNull(javaClass.classLoader?.getResourceAsStream("anonymization_vectors.json")) {
        "No se encontró anonymization_vectors.json"
    }.bufferedReader(Charsets.UTF_8).use { it.readText() }

    @Test
    fun everyVectorMatchesTheReferenceAnonymizer() {
        val problems = AnonymizationVectorsChecker.check(json)
        assertTrue("Discrepancias con el anonimizador de referencia:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun theVectorsCoverEveryRuleAndTheKnownCases() {
        assertTrue(AnonymizationVectorsChecker.caseCount(json) >= 25)
        assertEquals("1.3.0", AnonymizationRules.VERSION)
    }
}
