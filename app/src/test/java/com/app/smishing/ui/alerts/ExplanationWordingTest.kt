package com.app.smishing.ui.alerts

import com.app.smishing.PlainLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * #11: todas las cadenas del detalle y de la alerta cumplen las reglas de lenguaje: palabras corrientes, sin jerga técnica y sin
 * presentar la sospecha como certeza. Lee `strings.xml` tal cual está en el código, así que un texto nuevo que las incumpla
 * hace fallar esta prueba.
 *
 * Es un control mecánico: no prueba que el texto se entienda (eso es #23, con personas).
 */
class ExplanationWordingTest {

    private val strings: Map<String, String> by lazy {
        val file = listOf(File("src/main/res/values/strings.xml"), File("app/src/main/res/values/strings.xml")).first { it.exists() }
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
        (0 until nodes.length).associate { nodes.item(it).attributes.getNamedItem("name").nodeValue to nodes.item(it).textContent }
    }

    private fun explanation() = strings.filterKeys { it.startsWith("detail_") || it.startsWith("alert_") || it.startsWith("history_") || it.startsWith("settings_") }

    @Test
    fun theTextsToCheckAreTheOnesOfTheDetailAndTheAlert() {
        val names = explanation().keys
        for (expected in listOf("alert_title", "alert_reason_urgency", "detail_banner_high", "detail_summary_high", "detail_reason_link", "detail_disclaimer")) {
            assertTrue("Falta $expected", expected in names)
        }
    }

    @Test
    fun noTextOfTheDetailOrTheAlertBreaksTheLanguageRules() {
        val problems = explanation().flatMap { (name, text) -> PlainLanguage.violations(text).map { "$name → $it" } }
        assertEquals("Textos que incumplen las reglas de lenguaje", emptyList<String>(), problems)
    }

    @Test
    fun theTextsThatAdmitDoubtDoSo() {
        fun text(name: String) = checkNotNull(strings[name]) { "Falta $name" }
        assertTrue(text("alert_title").contains("Posible"))
        assertTrue(text("detail_banner_high").contains("Posible"))
        assertTrue(text("detail_summary_high").contains("No podemos estar seguros"))
        assertTrue(text("detail_summary_medium").contains("no estamos seguros"))
        assertTrue(text("detail_summary_low").contains("no garantiza"))
        assertTrue(text("detail_disclaimer").contains("puede equivocarse"))
    }

    @Test
    fun theReasonsDescribeWhatTheMessageSaysAndDoNotClaimWhatItIs() {
        for (name in listOf("alert_reason_urgency", "alert_reason_impersonation", "alert_reason_data_request", "detail_reason_impersonation")) {
            assertFalse("$name afirma una intención", strings.getValue(name).contains("Finge", ignoreCase = true))
        }
        assertTrue(strings.getValue("detail_reason_impersonation").contains("Nombra a una entidad"))
    }

    // --- Que el control no sea vacío ---

    @Test
    fun theRulesCatchTheWordingTheDesignHadAndOtherCertainties() {
        for (bad in listOf(
            "Mensaje Peligroso",
            "Esto es una estafa",
            "Finge ser de una entidad",
            "Probabilidad de estafa: 97 %",
            "Puntaje 0,97",
            "El modelo lo marcó como sospechoso",
            "Sin duda es un fraude",
            "Este mensaje es seguro",
            "Supera el umbral de riesgo",
        )) {
            assertTrue("Debía incumplir: $bad", PlainLanguage.violations(bad).isNotEmpty())
        }
    }

    @Test
    fun theRulesAcceptPlainHedgedWording() {
        for (good in listOf(
            "Posible estafa",
            "No podemos estar seguros de que lo sea, pero conviene tratarlo con mucho cuidado.",
            "Le mete prisa para que actúe sin pensar",
        )) {
            assertEquals(good, emptyList<String>(), PlainLanguage.violations(good))
        }
    }

    @Test
    fun aVeryLongSentenceIsFlagged() {
        val longSentence = (1..30).joinToString(" ") { "palabra" }
        assertTrue(PlainLanguage.violations(longSentence).any { "oración" in it })
    }
}
