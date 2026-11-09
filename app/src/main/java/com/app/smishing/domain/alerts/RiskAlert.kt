package com.app.smishing.domain.alerts

import com.app.smishing.domain.history.AnalysisHistory
import com.app.smishing.domain.history.AnalysisRecord
import com.app.smishing.domain.risk.RiskAssessment
import com.app.smishing.domain.risk.RiskAssessor
import com.app.smishing.domain.risk.RiskLevel
import com.app.smishing.domain.risk.RiskResult
import com.app.smishing.domain.signals.SignalType
import com.app.smishing.domain.sms.IncomingSms

/**
 * El motivo que se le dice a la persona en la notificación. Es el primero, en el orden del diseño, de los respaldos del nivel
 * (urgencia, suplantación, solicitud de datos). El enlace solo no es un respaldo, así que nunca es el motivo.
 */
enum class AlertReason { Urgency, Impersonation, DataRequest }

/**
 * Lo que dice una alerta: de qué mensaje es, quién lo envía y por qué se avisa. **No lleva el texto del mensaje**: la
 * notificación no debe revelarlo en la pantalla de bloqueo. El remitente tampoco sale en `toString`.
 */
data class RiskAlert(val messageId: Long, val sender: String, val reason: AlertReason) {
    override fun toString() = "RiskAlert(messageId=$messageId, reason=$reason)"
}

/** Muestra una alerta a la persona. */
fun interface RiskAlertNotifier {
    fun notify(alert: RiskAlert)
}

/** Cuándo se avisa y por qué motivo (#10). */
object AlertPolicy {

    /** Los niveles que producen alerta. Solo *alto*: el modelo lo marca y hay señales que lo respaldan (ver docs/alerta-de-riesgo.md). */
    val ALERT_LEVELS: Set<RiskLevel> = setOf(RiskLevel.High)

    private val REASON_ORDER = listOf(
        SignalType.Urgency to AlertReason.Urgency,
        SignalType.Impersonation to AlertReason.Impersonation,
        SignalType.DataRequest to AlertReason.DataRequest,
    )

    fun shouldAlert(level: RiskLevel): Boolean = level in ALERT_LEVELS

    /** El motivo para avisar, o `null` si el nivel no pide alerta o no hay un respaldo que nombrar. */
    fun reasonFor(assessment: RiskAssessment): AlertReason? {
        if (!shouldAlert(assessment.level)) return null
        return REASON_ORDER.firstOrNull { (type, _) -> type in assessment.evidence }?.second
    }
}

/** Qué pasó al evaluar un mensaje recibido. */
enum class AnalysisOutcome {
    /** Se guardó en el historial y se avisó. */
    Alerted,

    /** Se guardó en el historial y no pidió alerta. */
    Recorded,

    /** El modelo no lo evaluó (muy corto o análisis suspendido): no hay nivel, ni registro, ni alerta. */
    NotAssessed,
}

/**
 * Evalúa un SMS recién recibido, lo guarda en el historial (#12) y, si su nivel de riesgo pide alerta, avisa (#10).
 *
 * El registro se guarda **antes** de avisar, así el detalle de la alerta encuentra lo guardado. Si guardar falla, igual se avisa
 * ([onHistoryError] lo anota): la alerta no depende del historial.
 */
class AnalyzeReceivedMessage(
    private val assessor: RiskAssessor,
    private val history: AnalysisHistory,
    private val notifier: RiskAlertNotifier,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onHistoryError: (Throwable) -> Unit = {},
) {

    fun onReceived(messageId: Long, message: IncomingSms): AnalysisOutcome =
        when (val result = assessor.assess(message.body)) {
            // Muy corto o análisis suspendido: sin nivel, no se guarda ni se avisa (política de entrada del modelo).
            is RiskResult.NotAssessed -> AnalysisOutcome.NotAssessed
            is RiskResult.Assessed -> {
                val now = clock()
                try {
                    history.save(AnalysisRecord(0, messageId, message.sender, message.body, now, now, result.assessment))
                } catch (e: Exception) {
                    onHistoryError(e)
                }
                val reason = AlertPolicy.reasonFor(result.assessment)
                if (reason == null) AnalysisOutcome.Recorded
                else {
                    notifier.notify(RiskAlert(messageId, message.sender, reason))
                    AnalysisOutcome.Alerted
                }
            }
        }
}
