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

    /**
     * Lo que dice **la persona** al marcar un mensaje como seguro ("Este mensaje es seguro", "Sí, es seguro") es de los mockups y no es
     * una afirmación de la app sobre el mensaje: se excluye de la regla de certeza.
     */
    private val personsOwnWords = setOf("detail_button_mark_safe", "detail_confirm_yes")

    private fun explanation() = strings.filterKeys { it !in personsOwnWords }.filterKeys { it.startsWith("detail_") || it.startsWith("alert_") || it.startsWith("history_") || it.startsWith("settings_") || it.startsWith("home_") || it.startsWith("welcome_") || it.startsWith("protection_") }

    @Test
    fun theTextsToCheckAreTheOnesOfTheDetailAndTheAlert() {
        val names = explanation().keys
        for (expected in listOf("alert_title", "alert_reason_urgency", "detail_banner_high", "detail_reason_link", "detail_disclaimer")) {
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
        assertTrue(text("detail_disclaimer").contains("puede equivocarse"))
    }

    @Test
    fun theReasonsDescribeWhatTheMessageSaysAndDoNotClaimWhatItIs() {
        for (name in listOf("alert_reason_urgency", "alert_reason_impersonation", "alert_reason_data_request", "detail_reason_impersonation")) {
            assertFalse("$name afirma una intención", strings.getValue(name).contains("Finge", ignoreCase = true))
        }
        // El mockup dice "Finge ser de una entidad": afirma una intención que no se conoce, así que dice "Dice ser".
        assertTrue(strings.getValue("detail_reason_impersonation").contains("Dice ser de una entidad"))
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
    fun whatThePersonSaysWhenMarkingAMessageAsSafeIsTheMockupsAndIsNotCheckedForCertainty() {
        assertEquals("Este mensaje es seguro", strings.getValue("detail_button_mark_safe"))
        assertEquals("Sí, es seguro", strings.getValue("detail_confirm_yes"))
        // Es lo único que se excluye: la regla sigue atrapando la misma frase en cualquier otro texto.
        assertTrue(PlainLanguage.violations("Este mensaje es seguro").isNotEmpty())
        assertEquals(setOf("detail_button_mark_safe", "detail_confirm_yes"), personsOwnWords)
    }

    @Test
    fun theTextsThatKeepTheMockupWordingAreTheMockupsOnes() {
        assertEquals("Le pide actuar con urgencia", strings.getValue("detail_reason_urgency"))
        assertEquals("Contiene un enlace", strings.getValue("detail_reason_link"))
        assertEquals("Le pide sus datos o claves", strings.getValue("detail_reason_data_request"))
        assertEquals("No abra el enlace. Si tiene dudas, contacte directamente a la entidad.", strings.getValue("detail_recommendation"))
        assertEquals("Bloquear este número", strings.getValue("detail_button_block"))
        assertEquals("Borrar mensaje", strings.getValue("detail_button_remove"))
        assertEquals("Su teléfono está desprotegido contra mensajes maliciosos.", strings.getValue("protection_off_message"))
    }

    @Test
    fun aVeryLongSentenceIsFlagged() {
        val longSentence = (1..30).joinToString(" ") { "palabra" }
        assertTrue(PlainLanguage.violations(longSentence).any { "oración" in it })
    }
}
