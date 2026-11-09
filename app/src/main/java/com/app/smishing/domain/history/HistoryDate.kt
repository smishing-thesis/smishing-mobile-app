package com.app.smishing.domain.history

import java.time.Instant
import java.time.ZoneId

private val MONTHS = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

/** La fecha de un registro como en el diseño del historial: "28 sep 2026". El día se decide en la zona horaria dada. */
fun formatHistoryDate(atMillis: Long, zone: ZoneId): String {
    val date = Instant.ofEpochMilli(atMillis).atZone(zone)
    return "${date.dayOfMonth} ${MONTHS[date.monthValue - 1]} ${date.year}"
}
