package com.app.smishing.domain.history

import com.app.smishing.domain.risk.RiskLevel

/**
 * El resumen de la pantalla de inicio (#17): [reviewed] mensajes revisados y [possibleScams] con posible estafa, en los últimos días.
 */
data class WeeklySummary(val reviewed: Int, val possibleScams: Int)

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

/**
 * Cuenta los mensajes analizados en los últimos [days] días (una ventana móvil que termina en [nowMillis], no la semana del
 * calendario). Cuenta como **posible estafa** un mensaje de riesgo alto **que la persona no marcó como seguro**. Solo cuenta lo que
 * está en el historial de este teléfono.
 */
fun weeklySummary(records: List<AnalysisRecord>, nowMillis: Long, days: Int = 7): WeeklySummary {
    val since = nowMillis - days * DAY_MILLIS
    val recent = records.filter { it.analyzedAtMillis in since..nowMillis }
    return WeeklySummary(
        reviewed = recent.size,
        possibleScams = recent.count { it.assessment.level == RiskLevel.High && it.markedSafeAtMillis == null },
    )
}
