package com.app.smishing.domain.messaging

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * La fecha de un mensaje en la lista, como en el diseño: la hora si es de hoy ("14:20"), "Ayer", el mes y el día si es
 * de este año ("Marzo 12") y la fecha completa si es de antes ("12/03/2025").
 *
 * [yesterday] es el texto de "Ayer": viene de los recursos, no está fijo aquí.
 */
fun formatConversationDate(
    atMillis: Long,
    nowMillis: Long,
    zone: ZoneId,
    yesterday: String,
    locale: Locale,
): String {
    val day = Instant.ofEpochMilli(atMillis).atZone(zone)
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    return when {
        day.toLocalDate() == today -> day.format(DateTimeFormatter.ofPattern("HH:mm", locale))
        day.toLocalDate() == today.minusDays(1) -> yesterday
        day.year == today.year ->
            day.format(DateTimeFormatter.ofPattern("MMMM d", locale)).replaceFirstChar { it.titlecase(locale) }
        else -> day.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", locale))
    }
}
