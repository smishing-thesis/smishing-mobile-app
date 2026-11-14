package com.app.smishing.domain.messaging

/**
 * Una conversación: todos los SMS con un mismo remitente. Muestra el último mensaje y cuántos recibidos siguen sin leer (#50).
 *
 * El texto y el remitente son del usuario: `toString` no los incluye, para que no salgan en logs ni en errores.
 */
data class Conversation(
    val threadId: Long,
    val address: String,
    val snippet: String,
    val lastMessageAtMillis: Long,
    val unreadCount: Int = 0,
    /** El id en el buzón del último mensaje (el que muestra la fila), para buscar su riesgo (#60). */
    val lastMessageId: Long = 0,
) {
    override fun toString() = "Conversation(threadId=$threadId, lastMessageAtMillis=$lastMessageAtMillis, unreadCount=$unreadCount)"
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

/**
 * Una fila del buzón, tal como la entrega el sistema, para armar la lista de conversaciones. [unread] es un mensaje **recibido**
 * que todavía no se leyó.
 */
data class SmsRow(val threadId: Long, val address: String, val body: String, val atMillis: Long, val unread: Boolean = false, val id: Long = 0)

/**
 * Queda con el último mensaje de cada conversación, de la más reciente a la más antigua, y cuenta los recibidos sin leer de **toda**
 * la conversación (no solo del último). Las filas deben venir ordenadas de la más reciente a la más antigua.
 */
fun latestPerConversation(rowsNewestFirst: Sequence<SmsRow>): List<Conversation> {
    val latest = LinkedHashMap<Long, Conversation>()
    val unread = HashMap<Long, Int>()
    for (row in rowsNewestFirst) {
        latest.getOrPut(row.threadId) { Conversation(row.threadId, row.address, row.body, row.atMillis, lastMessageId = row.id) }
        if (row.unread) unread.merge(row.threadId, 1, Int::plus)
    }
    return latest.values.map { it.copy(unreadCount = unread[it.threadId] ?: 0) }
}

/** Un mensaje del buzón, con su remitente, para abrir su detalle. Igual que los demás, no muestra su texto en `toString`. */
data class StoredMessage(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val atMillis: Long,
    val direction: MessageDirection,
) {
    override fun toString() = "StoredMessage(id=$id, threadId=$threadId, atMillis=$atMillis, direction=$direction)"
}

/** Las conversaciones del teléfono y los mensajes de cada una. */
interface ConversationRepository {
    /** Un mensaje por su id en el buzón, o `null` si ya no está. */
    fun message(messageId: Long): StoredMessage?

    /** Las conversaciones, la más reciente primero. */
    fun conversations(): List<Conversation>

    /** Los mensajes de una conversación, el más antiguo primero. */
    fun messages(threadId: Long): List<ConversationMessage>

    /**
     * Marca como leídos los mensajes **recibidos** de la conversación (#50) y devuelve los ids de los que estaban sin leer, para
     * descartar sus notificaciones. No toca los enviados.
     */
    fun markRead(threadId: Long): List<Long>

    /** Borra del buzón los mensajes con esos ids (#51) y devuelve cuántos borró. Los que ya no estén no cuentan. */
    fun delete(messageIds: List<Long>): Int
}
