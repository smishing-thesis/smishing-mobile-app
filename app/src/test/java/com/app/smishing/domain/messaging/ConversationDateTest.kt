package com.app.smishing.domain.messaging

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

class ConversationDateTest {

    private val zone = ZoneId.of("America/Lima")
    private val spanish = Locale.forLanguageTag("es")

    private fun millis(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0) =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private val now = millis(2026, 11, 8, 18, 30)

    private fun format(at: Long, nowMillis: Long = now) = formatConversationDate(at, nowMillis, zone, "Ayer", spanish)

    @Test
    fun aMessageFromTodayShowsTheTime() {
        assertEquals("14:20", format(millis(2026, 11, 8, 14, 20)))
        assertEquals("00:05", format(millis(2026, 11, 8, 0, 5)))
    }

    @Test
    fun aMessageFromYesterdayShowsTheLabel() {
        assertEquals("Ayer", format(millis(2026, 11, 7, 23, 59)))
        assertEquals("Ayer", format(millis(2026, 11, 7, 0, 0)))
    }

    @Test
    fun aMessageFromEarlierThisYearShowsMonthAndDayCapitalized() {
        assertEquals("Marzo 12", format(millis(2026, 3, 12)))
        assertEquals("Noviembre 6", format(millis(2026, 11, 6)))
    }

    @Test
    fun aMessageFromAnotherYearShowsTheFullDate() {
        assertEquals("12/03/2025", format(millis(2025, 3, 12)))
    }

    @Test
    fun yesterdayAcrossTheNewYearIsStillYesterday() {
        assertEquals("Ayer", format(millis(2025, 12, 31, 22, 0), nowMillis = millis(2026, 1, 1, 9, 0)))
    }

    @Test
    fun theDayIsDecidedInTheGivenTimeZoneNotInUtc() {
        // 20:00 en Lima del 7 de noviembre ya es 01:00 UTC del 8: sigue siendo "ayer" para quien está en Lima.
        assertEquals("Ayer", format(millis(2026, 11, 7, 20, 0)))
    }
}
