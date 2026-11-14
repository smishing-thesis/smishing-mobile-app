package com.app.smishing.domain.history

import com.app.smishing.domain.risk.RiskAssessment

/**
 * Un mensaje analizado y su resultado, tal como se guarda en el teléfono (#12). Conserva el mensaje, el resultado y la explicación
 * (las señales con su regla y su fragmento) con las versiones del modelo, del preprocesamiento, de las reglas y de la política,
 * para poder consultarlo después sin volver a evaluarlo.
 *
 * Lleva el texto del mensaje: no debe escribirse en logs (su `toString` no lo muestra).
 */
data class AnalysisRecord(
    /** El id del registro en la base local; 0 si todavía no se guardó. */
    val id: Long,
    /** El id del mensaje en el buzón del sistema cuando se analizó. El registro sigue existiendo aunque el mensaje se borre. */
    val messageId: Long,
    val sender: String,
    val body: String,
    val receivedAtMillis: Long,
    val analyzedAtMillis: Long,
    val assessment: RiskAssessment,
    /** Cuándo la persona lo marcó como seguro (#16), o `null` si no lo hizo. */
    val markedSafeAtMillis: Long? = null,
) {
    override fun toString() = "AnalysisRecord(id=$id, messageId=$messageId, level=${assessment.level}, analyzedAtMillis=$analyzedAtMillis)"
}

/** El historial de análisis, en el teléfono. */
interface AnalysisHistory {
    /** Guarda el registro y devuelve su id. */
    fun save(record: AnalysisRecord): Long

    /** Todos los registros, el más reciente primero. */
    fun all(): List<AnalysisRecord>

    fun byId(id: Long): AnalysisRecord?

    /** El registro del mensaje con ese id en el buzón, o `null` si no se analizó. */
    fun byMessageId(messageId: Long): AnalysisRecord?

    /** El riesgo de cada mensaje analizado, por su id en el buzón (el registro más reciente de cada uno). Una sola lectura (#60). */
    fun risks(): Map<Long, MessageRisk> = emptyMap()

    /**
     * Borra los registros con esos ids (#52) y devuelve cuántos borró. Con cada registro se van su resultado, sus señales y sus acciones:
     * el texto del mensaje deja de estar en la base.
     */
    fun delete(ids: Collection<Long>): Int

    /** Borra todo el historial (#52) y devuelve cuántos registros borró. */
    fun deleteAll(): Int

    /** Guarda que la persona marcó el mensaje como seguro (#16). */
    fun markSafe(id: Long, nowMillis: Long)

    /** Cambia el id del mensaje en el buzón: al restaurar un mensaje retirado, el buzón le da uno nuevo (#16). */
    fun updateMessageId(id: Long, messageId: Long)

    /**
     * Borra los registros del mensaje con ese id **y ese texto** (#51) y devuelve cuántos borró. El texto debe coincidir: el buzón puede
     * reutilizar ids y no debe borrarse el análisis de otro mensaje. Con el registro se van su resultado, sus señales y sus acciones.
     */
    fun deleteByMessage(messageId: Long, body: String): Int

    /** Avisa cuando el historial cambia (llega un análisis nuevo). Devuelve algo que se cierra para dejar de avisar. */
    fun onChange(listener: () -> Unit): AutoCloseable = AutoCloseable {}
}
