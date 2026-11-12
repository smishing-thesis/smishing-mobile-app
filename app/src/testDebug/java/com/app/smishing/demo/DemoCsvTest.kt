package com.app.smishing.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class DemoCsvTest {

    // Texto sintético: los mensajes de las pruebas nunca son reales.
    private val header = "sender,body,minutes_ago,label\n"

    @Test
    fun readsQuotedFieldsWithCommasNewlinesAndEscapedQuotes() {
        val csv = header + "+51900000001,\"Hola, esto tiene \"\"comillas\"\"\nY dos líneas\",90,legitimo\n"
        val m = parseDemoCsv(csv).single()
        assertEquals("Hola, esto tiene \"comillas\"\nY dos líneas", m.body)
        assertEquals(90L, m.minutesAgo)
        assertEquals("legitimo", m.label)
    }

    @Test
    fun keepsAccentsAndEnyeAndIgnoresTheByteOrderMarkAndCrLf() {
        val csv = "\uFEFF" + header.replace("\n", "\r\n") + "+51900000001,Atención: ñandú ¿qué tal?,5,legitimo\r\n"
        assertEquals("Atención: ñandú ¿qué tal?", parseDemoCsv(csv).single().body)
    }

    @Test
    fun rejectsAFileWithoutTheRequiredColumnsOrWithABadNumberOrUnclosedQuotes() {
        assertThrows(DemoFileException::class.java) { parseDemoCsv("") }
        assertThrows(DemoFileException::class.java) { parseDemoCsv("a,b\n1,2\n") }
        assertThrows(DemoFileException::class.java) { parseDemoCsv(header + "+51900000001,texto,mucho,legitimo\n") }
        assertThrows(DemoFileException::class.java) { parseDemoCsv(header + "+51900000001,\"sin cerrar,5,legitimo\n") }
    }

    @Test
    fun theMessageTextDoesNotAppearInToString() {
        val m = parseDemoCsv(header + "+51900000001,texto privado,5,legitimo\n").single()
        assertFalse(m.toString().contains("texto privado"))
        assertFalse(m.toString().contains("+51900000001"))
    }
}
