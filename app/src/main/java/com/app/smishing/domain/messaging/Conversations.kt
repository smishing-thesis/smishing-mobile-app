package com.app.smishing.domain.messaging

/**
 * Una conversación: todos los SMS con un mismo remitente. Muestra el último mensaje.
 *
 * El texto y el remitente son del usuario: `toString` no los incluye, para que no salgan en logs ni en errores.
 */
data class Conversation(
    val threadId: Long,
    val address: String,
    val snippet: String,
    val lastMessageAtMillis: Long,
) {
    override fun toString() = "Conversation(threadId=$threadId, lastMessageAtMillis=$lastMessageAtMillis)"
}

enum class MessageDirection { Incoming, Outgoing }

/** Un mensaje dentro de una conversación, recibido o enviado. Igual que [Conversation], no muestra su texto en `toString`. */
data class ConversationMessage(
    val id: Long,
    val body: String,
    val atMillis: Long,
    val direction: MessageDirection,
    /** Un mensaje enviado que el sistema no pudo entregar a la radio. */
    val failed: Boolean = false,
) {
    override fun toString() = "ConversationMessage(id=$id, atMillis=$atMillis, direction=$direction, failed=$failed)"
}

/** Una fila del buzón, tal como la entrega el sistema, para armar la lista de conversaciones. */
data class SmsRow(val threadId: Long, val address: String, val body: String, val atMillis: Long)

/**
 * Queda con el último mensaje de cada conversación, de la más reciente a la más antigua.
 * Las filas deben venir ordenadas de la más reciente a la más antigua.
 */
fun latestPerConversation(rowsNewestFirst: Sequence<SmsRow>): List<Conversation> {
    val latest = LinkedHashMap<Long, Conversation>()
    for (row in rowsNewestFirst) {
        latest.getOrPut(row.threadId) { Conversation(row.threadId, row.address, row.body, row.atMillis) }
    }
    return latest.values.toList()
}

/** Las conversaciones del teléfono y los mensajes de cada una. */
interface ConversationRepository {
    /** Las conversaciones, la más reciente primero. */
    fun conversations(): List<Conversation>

    /** Los mensajes de una conversación, el más antiguo primero. */
    fun messages(threadId: Long): List<ConversationMessage>
}
