package com.app.smishing.domain.history

import com.app.smishing.domain.risk.RiskLevel

/**
 * El riesgo guardado de un mensaje, en lo mínimo que hace falta para marcarlo en la lista de mensajes y en su conversación (#60):
 * sin las señales ni la explicación, para poder leer el de todos los mensajes de una sola vez. Lleva el texto del mensaje, así que
 * no debe escribirse en logs.
 */
data class MessageRisk(
    val messageId: Long,
    val recordId: Long,
    val level: RiskLevel,
    val markedSafe: Boolean,
    val body: String,
) {
    override fun toString() = "MessageRisk(messageId=$messageId, recordId=$recordId, level=$level, markedSafe=$markedSafe)"
}

/**
 * Qué avisar de un mensaje en la lista y en la conversación: [RiskLevel.High] o [RiskLevel.Medium], o `null` si no hay nada que
 * avisar. **No se avisa** de uno de riesgo bajo (que no haya aviso no debe leerse como "es legítimo"), de uno que la persona marcó
 * como seguro, ni de uno sin análisis. El registro solo vale si es de ese mensaje: si el almacenamiento de SMS del sistema se borra,
 * sus ids empiezan de nuevo y un mensaje nuevo podría tener el id de un registro viejo, así que el texto debe coincidir.
 */
fun riskNotice(risks: Map<Long, MessageRisk>, messageId: Long, body: String): RiskLevel? =
    risks[messageId]
        ?.takeIf { it.body == body && !it.markedSafe && it.level != RiskLevel.Low }
        ?.level
