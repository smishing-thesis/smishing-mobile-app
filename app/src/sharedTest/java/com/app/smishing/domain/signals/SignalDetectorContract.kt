package com.app.smishing.domain.signals

import com.app.smishing.ControlledMessages
import com.app.smishing.data.preprocessing.PreprocessingConfigParser
import com.app.smishing.data.signals.SignalRulesParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lo que debe cumplir el detector de señales (#8). Lo ejecutan las pruebas de la JVM y las del dispositivo, para comprobar
 * que el motor de expresiones regulares de Android da lo mismo que el de la JVM.
 *
 * Los mensajes de los ejemplos son inventados. Los del conjunto controlado vienen de `controlled_messages.csv`, el conjunto
 * de mensajes sintéticos del repo del modelo.
 */
abstract class SignalDetectorContract {

    protected abstract fun rulesJson(): String
    protected abstract fun preprocessingJson(): String
    protected abstract fun controlledCsv(): String

    private val detector: RuleBasedSignalDetector by lazy {
        val homoglyphs = PreprocessingConfigParser.parse(preprocessingJson()).confusables.toHomoglyphs()
        RuleBasedSignalDetector(SignalRulesParser.parse(rulesJson()), homoglyphs)
    }

    private fun types(message: String) = detector.detect(message).types
    private fun fragments(message: String, type: SignalType) = detector.detect(message).signals.filter { it.type == type }.map { it.fragment }

    // --- Enlace ---

    @Test
    fun aUrlWithSchemeIsALinkAndItsFragmentIsTheUrlWithoutTheClosingPeriod() {
        assertEquals(listOf("http://bcp-seguro.info"), fragments("Verifique aquí http://bcp-seguro.info.", SignalType.Link))
    }

    @Test
    fun wwwAndBareDomainsAreLinks() {
        assertEquals(listOf("www.ejemplo.com"), fragments("Entre a www.ejemplo.com ahora", SignalType.Link))
        assertEquals(listOf("bit.ly/3kfj2"), fragments("Entra aquí bit.ly/3kfj2", SignalType.Link))
        assertEquals(listOf("interbank.pe"), fragments("Revísalo en interbank.pe", SignalType.Link))
        // Terminaciones baratas que usan las estafas (el dominio es inventado).
        assertEquals(listOf("abcdef.sbs/nc"), fragments("Actualice la dirección; abcdef.sbs/nc", SignalType.Link))
    }

    @Test
    fun twoLinksAreTwoSignalsAndOneUrlIsNotCountedTwice() {
        val signals = fragments("Mira https://uno.example.com/a y también dos.xyz/b", SignalType.Link)
        assertEquals(listOf("https://uno.example.com/a", "dos.xyz/b"), signals)
    }

    @Test
    fun anEmailAddressOrOrdinaryTextWithDotsIsNotALink() {
        for (message in listOf(
            "Escríbeme a ayuda@empresa.com cuando puedas",
            "Nos vemos a las 5 p.m. Gracias.",
            "Se abonó S/ 1,200.00 a tu cuenta",
            "Hola.Nos vemos mañana",
            "Son las 10:30, llego en 5 min...",
        )) {
            assertFalse("No debía detectar un enlace en: $message", SignalType.Link in types(message))
        }
    }

    // --- Urgencia ---

    @Test
    fun pressureToActFastIsUrgency() {
        for (message in listOf(
            "Su tarjeta será suspendida hoy",
            "Su cuenta ha sido bloqueada por seguridad",
            "Responda antes de 24 horas",
            "Hágalo de inmediato",
            "Regularice ahora para evitar problemas",
            "Evite el embargo cancelando hoy",
            "Actualice su clave o perderá el acceso",
            "Su clave de internet vence hoy",
            "Último aviso antes de la multa",
            "De no responder, procederemos con una demanda judicial en su contra",
            "Tomaremos acciones legales si no responde",
        )) {
            assertTrue("Debía detectar urgencia en: $message", SignalType.Urgency in types(message))
        }
    }

    @Test
    fun theUsualWaysALegitimateMessageMentionsTimeAreNotUrgency() {
        for (message in listOf(
            "Tu código vence en 5 minutos.",
            "Promo: solo hoy 2x1 en entradas",
            "Tu cupón de S/ 20 vence pronto",
            "Corte programado de agua el jueves de 8 a.m. a 2 p.m.",
            "Tu pedido llega hoy entre 2 y 6 pm",
            "Tu recibo vence el 15",
        )) {
            assertFalse("No debía detectar urgencia en: $message", SignalType.Urgency in types(message))
        }
    }

    // --- Solicitud de datos ---

    @Test
    fun askingForDataOrKeysIsADataRequest() {
        for (message in listOf(
            "Actualice sus datos en el siguiente enlace",
            "Confirme su identidad para continuar",
            "Ingresa tu clave y tu DNI",
            "Valida tus datos aquí",
            "Envíe su código de seguridad por este medio",
            "Confirma tu dirección para recibirlo",
        )) {
            assertTrue("Debía detectar una solicitud de datos en: $message", SignalType.DataRequest in types(message))
        }
    }

    @Test
    fun warningNotToShareADataItemIsNotARequestForIt() {
        for (message in listOf(
            "Tu código de verificación es 458920. No lo compartas con nadie.",
            "BCP: tu clave de operaciones es 774411. Nunca la compartas, el banco no te la pedirá por llamada.",
            "Usa el código 908172 para confirmar tu transferencia",
            "Ten tu DNI a la mano.",
        )) {
            assertFalse("No debía detectar una solicitud de datos en: $message", SignalType.DataRequest in types(message))
        }
    }

    // --- Suplantación ---

    @Test
    fun anEntityWithALinkThatIsNotItsOwnIsImpersonation() {
        assertEquals(listOf("BCP"), fragments("BCP: Su cuenta ha sido bloqueada. Verifique aquí http://bcp-seguro.info", SignalType.Impersonation))
        assertEquals(listOf("SUNAT"), fragments("SUNAT: tiene una deuda pendiente. Regularice en http://sunat-pe.info", SignalType.Impersonation))
    }

    @Test
    fun anEntityWithItsOwnLinkIsNotImpersonation() {
        for (message in listOf(
            "Interbank: Tu estado de cuenta ya está disponible. Revísalo en https://interbank.pe",
            "SUNAT: Tu declaración fue recibida. Descarga la constancia en https://www.sunat.gob.pe",
            "Claro: Consulta tu saldo y tus consumos en https://www.claro.com.pe",
            "BBVA: tu cita en agencia está confirmada. Detalle en https://www.bbva.pe",
        )) {
            assertFalse("No debía detectar suplantación en: $message", SignalType.Impersonation in types(message))
        }
    }

    @Test
    fun anEntityNamedWithoutLinkOrDataRequestIsNotImpersonation() {
        assertFalse(SignalType.Impersonation in types("BCP: Realizaste una compra de S/ 85.00 con tu tarjeta terminada en 4521."))
    }

    @Test
    fun anEntityThatAsksForDataIsImpersonationEvenWithoutALink() {
        assertTrue(SignalType.Impersonation in types("Interbank: confirme su clave para continuar"))
    }

    @Test
    fun aCommonWordThatIsAlsoACompanyNameOnlyCountsAsAPrefixOrInsideALink() {
        assertFalse(SignalType.Impersonation in types("Claro, nos vemos mañana. Mira esto http://fotos.xyz"))
        assertTrue(SignalType.Impersonation in types("Claro: tu cuenta será suspendida http://claro-pago.xyz"))
        assertTrue(SignalType.Impersonation in types("Felicidades! Ganaste. Reclama tu premio en http://premio-claro.top"))
    }

    @Test
    fun obfuscatedNamesAreStillRecognized() {
        assertEquals(listOf("8CP"), fragments("8CP: Su cu3nta ha sid0 bl0queada. Verifiqu3 aqui http://bcp-seguro.info", SignalType.Impersonation))
        assertTrue(SignalType.Impersonation in types("I n t e r b a n k: cargo no reconocido, ingrese a http://interbank-clientes.net"))
        assertTrue(SignalType.Impersonation in types("S U N A T: deuda pendiente, regulariza hoy en http://sunat-pe.info"))
        assertTrue(SignalType.Impersonation in types("Y4pe: t u cu3nta s3rá d3sactivada, v4lida aquí http://yape-validacion.net"))
    }

    @Test
    fun aLookalikeLetterFromAnotherAlphabetIsReadAsTheLatinOne() {
        // La "Ѕ" de "Ѕunat" es la letra cirílica U+0405, no una S latina.
        assertTrue(SignalType.Impersonation in types("ЅUNAT: deuda pendiente, regularice en http://sunat-pe.info"))
    }

    // --- Obfuscación de las señales de palabras ---

    @Test
    fun obfuscatedWordsStillCountForUrgencyAndDataRequests() {
        assertTrue(SignalType.Urgency in types("Su cu3nta s3rá bl0queada hoy"))
        assertTrue(SignalType.DataRequest in types("Actualiza tus dat0s en el enlace"))
    }

    // --- Forma del resultado ---

    @Test
    fun aLegitimateOneTimeCodeHasNoSignalsAtAll() {
        assertEquals(emptySet<SignalType>(), types("Tu código de verificación es 458920. No lo compartas con nadie."))
    }

    @Test
    fun everySignalNamesItsRuleAndTheAnalysisNamesTheRulesVersion() {
        val analysis = detector.detect("BCP: Su cuenta será bloqueada. Actualice sus datos en http://bcp-seguro.info")
        assertEquals("1.1.0", analysis.rulesVersion)
        assertEquals(
            setOf(SignalType.Urgency, SignalType.Impersonation, SignalType.Link, SignalType.DataRequest),
            analysis.types,
        )
        for (signal in analysis.signals) {
            assertTrue("La regla debía tener id: $signal", signal.ruleId.contains('.'))
        }
        assertTrue(analysis.signals.any { it.ruleId == "impersonation.bcp" })
        assertTrue(analysis.signals.any { it.ruleId == "link.url" })
    }

    @Test
    fun signalsComeInTheOrderOfTheDesign() {
        val order = detector.detect("BCP: Su cuenta será bloqueada. Actualice sus datos en http://bcp-seguro.info").signals.map { it.type }
        assertEquals(order.sortedBy { it.ordinal }, order)
    }

    @Test
    fun aSignalNeverShowsTheMessageInItsStringForm() {
        val analysis = detector.detect("BCP: confirme su clave en http://bcp-seguro.info")
        assertTrue(analysis.signals.isNotEmpty())
        val text = analysis.toString() + analysis.signals.joinToString { it.toString() }
        assertFalse("bcp-seguro" in text)
        assertFalse("clave" in text)
    }

    @Test
    fun foldingKeepsTheLengthOfTheText() {
        for (message in controlled().map { it.message }) {
            assertEquals(message.length, foldText(message, leet = true, homoglyphs = emptyMap()).length)
        }
    }

    // --- El conjunto controlado ---

    private fun controlled(): List<ControlledMessages.Case> = ControlledMessages.parse(controlledCsv())

    @Test
    fun theControlledSetIsTheExpectedOne() {
        val all = controlled()
        assertEquals(84, all.size)
        assertEquals(setOf("legitimate", "smishing"), all.map { it.label }.toSet())
    }

    @Test
    fun everyFragmentIsAPieceOfTheOriginalMessage() {
        for (case in controlled()) {
            for (signal in detector.detect(case.message).signals) {
                assertNotNull(signal)
                assertTrue("${case.id}: el fragmento no es parte del mensaje", case.message.contains(signal.fragment))
            }
        }
    }

    @Test
    fun legitimateMessagesWithoutALinkRaiseNoSignal() {
        val raised = controlled().filter { it.label == "legitimate" && it.category != "legit_with_link" }
            .filter { detector.detect(it.message).signals.isNotEmpty() }
            .map { "${it.id}: ${detector.detect(it.message).types}" }
        assertEquals("Falsas alarmas en mensajes legítimos", emptyList<String>(), raised)
    }

    @Test
    fun legitimateMessagesWithTheirOwnLinkOnlyRaiseTheLinkSignal() {
        for (case in controlled().filter { it.category == "legit_with_link" }) {
            assertEquals("${case.id}", setOf(SignalType.Link), detector.detect(case.message).types)
        }
    }

    /**
     * Todos los mensajes de smishing del conjunto traen un enlace, y el detector debe verlo. Que además tengan otra señal
     * no se exige: algunos (premios, enlaces sueltos) no tienen urgencia, suplantación ni solicitud de datos tal como se
     * definen, y las reglas no se ajustan a esos casos. Cuántos son se mide en `reportSignalsPerCategory`.
     */
    @Test
    fun everySmishingMessageHasItsLinkDetected() {
        val missing = controlled().filter { it.label == "smishing" }.filter { SignalType.Link !in detector.detect(it.message).types }.map { it.id }
        assertEquals("Mensajes de smishing sin el enlace detectado", emptyList<String>(), missing)
    }

    /** Escribe la tabla de resultados por categoría: se lee del informe de la prueba para la documentación. */
    @Test
    fun reportSignalsPerCategory() {
        val rows = controlled().groupBy { it.category }.toSortedMap().map { (category, cases) ->
            val counts = SignalType.entries.associateWith { type -> cases.count { type in detector.detect(it.message).types } }
            "SIGNALS category=$category n=${cases.size} " + counts.entries.joinToString(" ") { "${it.key.name}=${it.value}" }
        }
        rows.forEach(::println)
        assertTrue(rows.isNotEmpty())
    }
}
