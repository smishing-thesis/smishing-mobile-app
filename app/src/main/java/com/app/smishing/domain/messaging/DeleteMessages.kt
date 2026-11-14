package com.app.smishing.domain.messaging

import com.app.smishing.domain.history.AnalysisHistory

/**
 * Lo que se borró (#51): los ids de los mensajes que se pidió borrar (para descartar sus notificaciones) y cuántos registros del
 * historial se borraron con ellos.
 */
data class DeleteResult(val messageIds: List<Long>, val records: Int)

/**
 * Borra mensajes y conversaciones (#51), uno o varios. Borrar un mensaje del buzón **no** borra su análisis del historial, que guarda el
 * texto: por eso la persona decide, y [alsoHistory] dice si también se borran los registros de los mensajes borrados.
 *
 * - Primero se borra del buzón y después el historial: si el buzón falla, no se pierde el registro de un mensaje que sigue ahí.
 * - Un registro se borra solo si es **de ese mensaje**: mismo id y mismo texto. El buzón puede reutilizar ids si se restablece, y no
 *   debe borrarse el análisis de otro mensaje.
 * - Solo los mensajes **recibidos** tienen análisis; los enviados no se buscan en el historial.
 * - Una conversación se borra por los ids de sus mensajes, no por su hilo: un mensaje que llegue mientras tanto no se pierde sin que
 *   se haya podido decidir sobre su registro.
 */
class DeleteMessages(
    private val repository: ConversationRepository,
    private val history: AnalysisHistory,
) {

    fun deleteMessages(messages: List<ConversationMessage>, alsoHistory: Boolean): DeleteResult {
        if (messages.isEmpty()) return DeleteResult(emptyList(), 0)
        repository.delete(messages.map { it.id })
        val records = if (alsoHistory) {
            messages.filter { it.direction == MessageDirection.Incoming }.sumOf { history.deleteByMessage(it.id, it.body) }
        } else {
            0
        }
        return DeleteResult(messages.map { it.id }, records)
    }

    fun deleteConversations(threadIds: List<Long>, alsoHistory: Boolean): DeleteResult =
        deleteMessages(threadIds.flatMap { repository.messages(it) }, alsoHistory)
}
