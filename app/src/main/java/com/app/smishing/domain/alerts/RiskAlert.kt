package com.app.smishing.domain.alerts

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
enum class AlertOutcome { Alerted, NotRisky, NotAssessed }

/**
 * Evalúa un SMS recién recibido y, si su nivel de riesgo pide alerta, avisa (#10). No guarda nada: el detalle vuelve a evaluar el
 * mensaje cuando se abre, y el resultado es el mismo porque la evaluación es determinista.
 */
class AlertOnRiskyMessage(
    private val assessor: RiskAssessor,
    private val notifier: RiskAlertNotifier,
) {

    fun onReceived(messageId: Long, message: IncomingSms): AlertOutcome =
        when (val result = assessor.assess(message.body)) {
            // Muy corto o análisis suspendido: sin nivel, no se avisa (política de entrada del modelo).
            is RiskResult.NotAssessed -> AlertOutcome.NotAssessed
            is RiskResult.Assessed -> {
                val reason = AlertPolicy.reasonFor(result.assessment)
                if (reason == null) AlertOutcome.NotRisky
                else {
                    notifier.notify(RiskAlert(messageId, message.sender, reason))
                    AlertOutcome.Alerted
                }
            }
        }
}
