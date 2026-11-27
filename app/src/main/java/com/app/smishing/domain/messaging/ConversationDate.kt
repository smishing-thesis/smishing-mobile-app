package com.app.smishing.domain.messaging

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * La fecha de un mensaje en la lista, como en el diseño: la hora si es de hoy ("14:20"), "Ayer", el mes y el día si es
 * de este año ("Marzo 12") y la fecha completa si es de antes ("12/03/2025").
 *
 * [yesterday] es el texto de "Ayer": viene de los recursos, no está fijo aquí. La hora sigue el reloj del teléfono ([is24Hour]).
 */
fun formatConversationDate(
    atMillis: Long,
    nowMillis: Long,
    zone: ZoneId,
    yesterday: String,
    locale: Locale,
    is24Hour: Boolean = true,
): String {
    val day = Instant.ofEpochMilli(atMillis).atZone(zone)
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    return when {
        day.toLocalDate() == today -> clockTime(day, is24Hour)
        day.toLocalDate() == today.minusDays(1) -> yesterday
        day.year == today.year ->
            day.format(DateTimeFormatter.ofPattern("MMMM d", locale)).replaceFirstChar { it.titlecase(locale) }
        else -> day.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", locale))
    }
}

/**
 * El día y la hora de un mensaje, para mostrarlos centrados sobre él en la conversación: "Hoy · 14:20", "Ayer · 23:59",
 * "Marzo 12 · 09:05" si es de este año y "12/03/2025 · 09:05" si es de antes. [today] y [yesterday] vienen de los recursos.
 */
fun formatMessageStamp(
    atMillis: Long,
    nowMillis: Long,
    zone: ZoneId,
    today: String,
    yesterday: String,
    locale: Locale,
    is24Hour: Boolean = true,
): String {
    val moment = Instant.ofEpochMilli(atMillis).atZone(zone)
    val now = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val day = when {
        moment.toLocalDate() == now -> today
        moment.toLocalDate() == now.minusDays(1) -> yesterday
        moment.year == now.year -> moment.format(DateTimeFormatter.ofPattern("MMMM d", locale)).replaceFirstChar { it.titlecase(locale) }
        else -> moment.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", locale))
    }
    return day + " · " + clockTime(moment, is24Hour)
}

/**
 * La hora como la muestra el reloj del teléfono: con el formato de 24 horas, "18:00"; con el de 12 horas, "8:00 pm" o "10:05 am"
 * (medianoche es "12:05 am" y mediodía "12:00 pm"). Se arma aquí y no con el formato de la región para que "am" y "pm" salgan siempre igual.
 */
internal fun clockTime(moment: ZonedDateTime, is24Hour: Boolean): String {
    val minute = moment.minute.toString().padStart(2, '0')
    if (is24Hour) return moment.hour.toString().padStart(2, '0') + ":" + minute
    val hour = if (moment.hour % 12 == 0) 12 else moment.hour % 12
    return "$hour:$minute " + if (moment.hour < 12) "am" else "pm"
}
