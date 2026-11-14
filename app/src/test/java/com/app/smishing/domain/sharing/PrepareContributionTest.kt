package com.app.smishing.domain.sharing

import com.app.smishing.domain.contacts.ContactBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrepareContributionTest {

    // Textos, nombres y números sintéticos: nunca son reales.
    private val message = "Hola Carla, llame al 912345678 o entre a https://banco.example/x"

    private class FakeConsent(private var value: Boolean = false) : ContributionConsent {
        var reads = 0
        override fun isGranted(): Boolean { reads++; return value }
        override fun setGranted(granted: Boolean) { value = granted }
    }

    @Test
    fun withoutAnyChoiceNothingIsPrepared() {
        // Desactivado por defecto: una persona que nunca tocó el interruptor no comparte nada.
        val consent = FakeConsent()
        assertEquals(ContributionResult.Rejected(ContributionRejection.NoConsent), PrepareContribution(consent).prepare(message))
    }

    @Test
    fun withConsentTheTextIsAnonymizedAndReadyAndKeepsTheRulesVersion() {
        val prepare = PrepareContribution(FakeConsent(true)) { Anonymizer.fromContactNames(listOf("Carla")) }
        val result = prepare.prepare(message) as ContributionResult.Ready
        assertEquals("Hola <NAMED_ENTITY>, llame al <PHONE_NUMBER> o entre a <URL>", result.text)
        assertEquals(AnonymizationRules.VERSION, result.rulesVersion)
    }

    @Test
    fun revokingStopsTheNextContributionBecauseTheConsentIsReadEveryTime() {
        val consent = FakeConsent(true)
        val prepare = PrepareContribution(consent)
        assertTrue(prepare.prepare(message) is ContributionResult.Ready)

        consent.setGranted(false)
        assertEquals(ContributionResult.Rejected(ContributionRejection.NoConsent), prepare.prepare(message))

        consent.setGranted(true)
        assertTrue(prepare.prepare(message) is ContributionResult.Ready)
        assertEquals(3, consent.reads)
    }

    @Test
    fun withoutConsentTheAnonymizerIsNotEvenBuilt() {
        var built = 0
        val prepare = PrepareContribution(FakeConsent(false)) { built++; Anonymizer() }
        prepare.prepare(message)
        assertEquals(0, built)
    }

    @Test
    fun anExampleThatIsStillNotAnonymousIsRejectedNotFixed() {
        // Un desidentificador que deja pasar el texto tal cual: la comprobación final lo rechaza.
        val leaky = object : TextAnonymizer {
            override fun anonymize(text: String) = Anonymized(text, AnonymizationRules.VERSION, 0)
            override fun isAnonymized(text: String) = Anonymizer().isAnonymized(text)
        }
        val result = PrepareContribution(FakeConsent(true)) { leaky }.prepare(message)
        assertEquals(ContributionResult.Rejected(ContributionRejection.NotAnonymized), result)
    }

    @Test
    fun aTextThatIsEmptyAfterAnonymizingIsRejected() {
        assertEquals(ContributionResult.Rejected(ContributionRejection.Empty), PrepareContribution(FakeConsent(true)).prepare("   "))
    }

    @Test
    fun theResultNeverShowsTheTextInToString() {
        val ready = PrepareContribution(FakeConsent(true)).prepare("Llame al 912345678 hoy") as ContributionResult.Ready
        assertFalse(ready.toString().contains("hoy"))
        assertFalse(Anonymizer().anonymize("Llame al 912345678 hoy").toString().contains("hoy"))
    }

    @Test
    fun contactNamesBecomeTheNamesToRemoveWordByWordAndIgnoreShortOnes() {
        val book = ContactBook.of(listOf("900000001" to "Mama", "900000002" to "Carlos Perez", "900000003" to "Al"))
        val anonymizer = Anonymizer.fromContactNames(book.displayNames())
        assertEquals("<NAMED_ENTITY> y <NAMED_ENTITY> llamaron, Al no", anonymizer.anonymize("Mama y Carlos Perez llamaron, Al no").text)
        assertEquals(setOf("Mama", "Carlos Perez", "Al"), book.displayNames().toSet())
    }

    @Test
    fun aNameThatIsNotInTheListIsNotDetectedAndThatIsAKnownLimit() {
        // Sin NER en el teléfono: un nombre que no está en los contactos pasa. La app no lo puede saber.
        val anonymizer = Anonymizer.fromContactNames(listOf("Mama"))
        assertEquals("Hola Roberto", anonymizer.anonymize("Hola Roberto").text)
        assertTrue(anonymizer.isAnonymized("Hola Roberto"))
    }

    @Test
    fun rawTextWithIdentifiersIsNotAnonymizedAndTheResultIs() {
        val anonymizer = Anonymizer()
        assertFalse(anonymizer.isAnonymized(message))
        assertTrue(anonymizer.isAnonymized(anonymizer.anonymize(message).text))
    }
}
