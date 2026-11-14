package com.app.smishing.domain.messaging

import com.app.smishing.domain.history.AnalysisHistory
import com.app.smishing.domain.history.AnalysisRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DeleteMessagesTest {

    // Textos sintéticos: los mensajes de las pruebas nunca son reales.
    private fun incoming(id: Long, body: String = "recibido $id") = ConversationMessage(id, body, id * 1_000, MessageDirection.Incoming)
    private fun outgoing(id: Long, body: String = "enviado $id") = ConversationMessage(id, body, id * 1_000, MessageDirection.Outgoing)

    private class FakeRepository(
        val threads: Map<Long, List<ConversationMessage>>,
        private val failWith: Exception? = null,
    ) : ConversationRepository {
        constructor(thread: List<ConversationMessage>, failWith: Exception? = null) : this(mapOf(9L to thread), failWith)

        val deleted = mutableListOf<Long>()
        override fun message(messageId: Long): StoredMessage? = null
        override fun conversations(): List<Conversation> = emptyList()
        override fun messages(threadId: Long) = threads[threadId].orEmpty()
        override fun markRead(threadId: Long): List<Long> = emptyList()
        override fun delete(messageIds: List<Long>): Int {
            failWith?.let { throw it }
            deleted += messageIds
            return messageIds.size
        }
    }

    private class FakeHistory(val records: MutableList<Pair<Long, String>>) : AnalysisHistory {
        val asked = mutableListOf<Pair<Long, String>>()
        override fun save(record: AnalysisRecord): Long = 0
        override fun all(): List<AnalysisRecord> = emptyList()
        override fun byId(id: Long): AnalysisRecord? = null
        override fun byMessageId(messageId: Long): AnalysisRecord? = null
        override fun delete(ids: Collection<Long>) = 0
        override fun deleteAll() = 0
        override fun deleteByMessage(messageId: Long, body: String): Int {
            asked += messageId to body
            return if (records.remove(messageId to body)) 1 else 0
        }
        override fun markSafe(id: Long, nowMillis: Long) = Unit
        override fun updateMessageId(id: Long, messageId: Long) = Unit
    }

    @Test
    fun deletingAMessageWithoutTheHistoryLeavesItsRecord() {
        val repository = FakeRepository(emptyList())
        val history = FakeHistory(mutableListOf(1L to "recibido 1"))

        val result = DeleteMessages(repository, history).deleteMessages(listOf(incoming(1)), alsoHistory = false)

        assertEquals(listOf(1L), repository.deleted)
        assertEquals(DeleteResult(listOf(1L), 0), result)
        assertEquals(emptyList<Pair<Long, String>>(), history.asked)
        assertEquals(1, history.records.size)
    }

    @Test
    fun deletingAMessageWithTheHistoryRemovesTheRecordOfThatMessageOnly() {
        val repository = FakeRepository(emptyList())
        // Un registro con el mismo id y otro texto es de otro mensaje (el buzón reutilizó el id): no se borra.
        val history = FakeHistory(mutableListOf(1L to "recibido 1", 1L to "otro texto", 2L to "recibido 1"))

        val result = DeleteMessages(repository, history).deleteMessages(listOf(incoming(1)), alsoHistory = true)

        assertEquals(DeleteResult(listOf(1L), 1), result)
        assertEquals(listOf(1L to "otro texto", 2L to "recibido 1"), history.records)
    }

    @Test
    fun deletingAConversationDeletesEveryMessageByIdAndOnlyLooksUpTheReceivedOnesInTheHistory() {
        val repository = FakeRepository(listOf(incoming(1), outgoing(2), incoming(3)))
        val history = FakeHistory(mutableListOf(1L to "recibido 1", 3L to "recibido 3", 2L to "enviado 2"))

        val result = DeleteMessages(repository, history).deleteConversations(listOf(9L), alsoHistory = true)

        assertEquals(listOf(1L, 2L, 3L), repository.deleted)
        assertEquals(DeleteResult(listOf(1L, 2L, 3L), 2), result)
        // Lo enviado no tiene análisis: no se busca en el historial, así que su registro (ajeno) no se toca.
        assertEquals(listOf(1L to "recibido 1", 3L to "recibido 3"), history.asked)
        assertEquals(listOf(2L to "enviado 2"), history.records)
    }

    @Test
    fun deletingSeveralMessagesOrConversationsDeletesAllOfThemInOneGo() {
        val repository = FakeRepository(mapOf(1L to listOf(incoming(1), outgoing(2)), 2L to listOf(incoming(3)), 3L to listOf(incoming(4))))
        val history = FakeHistory(mutableListOf(1L to "recibido 1", 3L to "recibido 3", 4L to "recibido 4"))

        // Dos conversaciones de tres: la tercera no se toca.
        val result = DeleteMessages(repository, history).deleteConversations(listOf(1L, 2L), alsoHistory = true)

        assertEquals(DeleteResult(listOf(1L, 2L, 3L), 2), result)
        assertEquals(listOf(1L, 2L, 3L), repository.deleted)
        assertEquals(listOf(4L to "recibido 4"), history.records)
        // Varios mensajes sueltos de una conversación.
        val several = DeleteMessages(repository, history).deleteMessages(listOf(incoming(4), outgoing(5)), alsoHistory = false)
        assertEquals(listOf(4L, 5L), several.messageIds)
        assertEquals(1, history.records.size)
    }

    @Test
    fun anEmptyConversationDeletesNothing() {
        val repository = FakeRepository(emptyList())
        val history = FakeHistory(mutableListOf())

        assertEquals(DeleteResult(emptyList(), 0), DeleteMessages(repository, history).deleteConversations(listOf(9L), alsoHistory = true))
        assertEquals(emptyList<Long>(), repository.deleted)
    }

    @Test
    fun ifTheInboxFailsTheHistoryIsNotTouched() {
        val repository = FakeRepository(emptyList(), failWith = SecurityException("no es la predeterminada"))
        val history = FakeHistory(mutableListOf(1L to "recibido 1"))

        assertThrows(SecurityException::class.java) { DeleteMessages(repository, history).deleteMessages(listOf(incoming(1)), alsoHistory = true) }

        assertEquals(emptyList<Pair<Long, String>>(), history.asked)
        assertEquals(1, history.records.size)
    }
}
