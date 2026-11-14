package com.app.smishing.domain.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ContactBookTest {

    // Nombres y números sintéticos: nunca son de personas reales.
    private val book = ContactBook.of(
        listOf(
            "+51 900 000 001" to "Contacto Uno",
            "900-000-002" to "Contacto Dos",
            "(01) 700 0003" to "Oficina",
        ),
    )

    @Test
    fun theSameNumberMatchesWithOrWithoutCountryCodeAndWhateverTheFormat() {
        assertEquals("Contacto Uno", book.nameOf("900000001"))
        assertEquals("Contacto Uno", book.nameOf("+51900000001"))
        assertEquals("Contacto Dos", book.nameOf("+51 900 000 002"))
        assertEquals("Contacto Dos", book.nameOf("0051900000002".takeLast(12)))
    }

    @Test
    fun aNumberThatIsNotInTheContactsShowsTheNumberAsToday() {
        assertNull(book.nameOf("+51900000099"))
        assertEquals("+51900000099", book.display("+51900000099"))
        assertEquals("Contacto Uno", book.display("900000001"))
    }

    @Test
    fun aSenderThatIsNotAPersonsNumberIsNeverLookedUp() {
        // Un contacto guardado con un código corto no debe aparecer para otro remitente con ese código ni para nombres de empresa.
        val withShortCode = ContactBook.of(listOf("88800" to "Banco", "123" to "Corto"))
        assertNull(withShortCode.nameOf("88800"))
        assertNull(withShortCode.nameOf("123"))
        assertNull(book.nameOf("ONPE"))
        assertNull(book.nameOf("CLARO"))
        assertNull(book.nameOf(""))
        assertEquals("ONPE", book.display("ONPE"))
    }

    @Test
    fun anEmptyBookIsWhatYouGetWithoutThePermission() {
        val empty = ContactBook()
        assertNull(empty.nameOf("+51900000001"))
        assertEquals("+51900000001", empty.display("+51900000001"))
    }

    @Test
    fun theFirstContactWinsWhenANumberIsRepeatedAndBlankNamesAreIgnored() {
        val repeated = ContactBook.of(listOf("900000010" to "Primero", "+51900000010" to "Segundo", "900000011" to "  "))
        assertEquals("Primero", repeated.nameOf("900000010"))
        assertNull(repeated.nameOf("900000011"))
    }

    @Test
    fun theNamesDoNotAppearInToString() {
        assertFalse(book.toString().contains("Contacto"))
        assertEquals("ContactBook(<oculto>)", book.toString())
    }
}
