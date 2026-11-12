package com.app.smishing.domain.history

import com.app.smishing.domain.alerts.AnalysisOutcome
import com.app.smishing.domain.risk.RiskAssessor
import com.app.smishing.domain.risk.RiskResult

/**
 * Analiza un mensaje que ya estaba en el buzón y no tiene análisis (#59): uno anterior a la app o uno que llegó con el análisis
 * suspendido. Guarda el resultado en el historial como el de cualquier otro (nivel, explicación y versiones), con la hora de recepción
 * del mensaje. **No avisa ni actúa**: es un mensaje antiguo, así que no hay alerta, ni bloqueo, ni se retira nada.
 *
 * Se analiza de a uno, a pedido de la persona, dentro de su conversación: no se lee todo el buzón.
 */
class AnalyzeStoredMessage(
    private val assessor: RiskAssessor,
    private val history: AnalysisHistory,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** [AnalysisOutcome.Recorded] si quedó guardado; [AnalysisOutcome.NotAssessed] si el modelo no lo evaluó (y no se guarda nada). */
    fun analyze(messageId: Long, sender: String, body: String, receivedAtMillis: Long): AnalysisOutcome =
        when (val result = assessor.assess(body)) {
            is RiskResult.NotAssessed -> AnalysisOutcome.NotAssessed
            is RiskResult.Assessed -> {
                history.save(AnalysisRecord(0, messageId, sender, body, receivedAtMillis, clock(), result.assessment))
                AnalysisOutcome.Recorded
            }
        }
}
