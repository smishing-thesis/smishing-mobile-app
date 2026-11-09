package com.app.smishing.domain.history

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class HistoryDateTest {

    private val lima = ZoneId.of("America/Lima")

    private fun millis(year: Int, month: Int, day: Int, hour: Int = 12) =
        LocalDateTime.of(year, month, day, hour, 0).atZone(lima).toInstant().toEpochMilli()

    @Test
    fun theDateLooksLikeTheDesign() {
        assertEquals("28 sep 2026", formatHistoryDate(millis(2026, 9, 28), lima))
        assertEquals("1 ene 2027", formatHistoryDate(millis(2027, 1, 1), lima))
        assertEquals("31 dic 2025", formatHistoryDate(millis(2025, 12, 31), lima))
    }

    @Test
    fun everyMonthHasItsThreeLetterName() {
        val names = (1..12).map { formatHistoryDate(millis(2026, it, 15), lima).split(" ")[1] }
        assertEquals(listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"), names)
    }

    @Test
    fun theDayIsDecidedInTheGivenTimeZone() {
        // 22:00 en Lima del 8 de noviembre ya es 03:00 UTC del 9.
        assertEquals("8 nov 2026", formatHistoryDate(millis(2026, 11, 8, 22), lima))
        assertEquals("9 nov 2026", formatHistoryDate(millis(2026, 11, 8, 22), ZoneId.of("UTC")))
    }
}
