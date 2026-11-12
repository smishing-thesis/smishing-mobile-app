package com.app.smishing.domain.messaging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ConversationsTest {

    private fun row(thread: Long, address: String, body: String, at: Long) = SmsRow(thread, address, body, at)

    @Test
    fun keepsOneConversationPerThreadWithItsLatestMessage() {
        val rows = sequenceOf(
            row(2, "+10000000002", "tercero, de B", 300),
            row(1, "+10000000001", "segundo, de A", 200),
            row(1, "+10000000001", "primero, de A", 100),
        )

        val conversations = latestPerConversation(rows)

        assertEquals(listOf(2L, 1L), conversations.map { it.threadId })
        assertEquals("segundo, de A", conversations.last().snippet)
        assertEquals(200L, conversations.last().lastMessageAtMillis)
    }

    @Test
    fun theMostRecentConversationComesFirst() {
        val rows = sequenceOf(
            row(3, "C", "c", 900),
            row(1, "A", "a", 800),
            row(2, "B", "b", 700),
        )
        assertEquals(listOf(3L, 1L, 2L), latestPerConversation(rows).map { it.threadId })
    }

    @Test
    fun noMessagesMeansNoConversations() {
        assertEquals(emptyList<Conversation>(), latestPerConversation(emptySequence()))
    }

    @Test
    fun theStringFormNeverShowsTheTextOrTheSender() {
        val conversation = Conversation(1, "+10000000001", "Tu codigo es 458920", 100)
        val message = ConversationMessage(7, "Tu codigo es 458920", 100, MessageDirection.Incoming)
        for (text in listOf(conversation.toString(), message.toString())) {
            assertFalse("458920" in text)
            assertFalse("+10000000001" in text)
        }
    }

    // --- Lo no leído (#50) ---

    private fun unreadRow(thread: Long, at: Long, unread: Boolean) = SmsRow(thread, "+10000000001", "texto", at, unread)

    @Test
    fun theUnreadCountCoversTheWholeConversationNotOnlyTheLastMessage() {
        val rows = sequenceOf(
            unreadRow(1, 30, unread = true),
            unreadRow(1, 20, unread = false),
            unreadRow(1, 10, unread = true),
            unreadRow(2, 5, unread = false),
        )
        val byThread = latestPerConversation(rows).associateBy { it.threadId }
        assertEquals(2, byThread.getValue(1).unreadCount)
        assertEquals(0, byThread.getValue(2).unreadCount)
    }

    @Test
    fun aConversationWithNothingUnreadHasAZeroCountAndTheOrderIsKept() {
        val result = latestPerConversation(sequenceOf(unreadRow(3, 30, false), unreadRow(1, 20, true), unreadRow(2, 10, false)))
        assertEquals(listOf(3L, 1L, 2L), result.map { it.threadId })
        assertEquals(listOf(0, 1, 0), result.map { it.unreadCount })
    }

    @Test
    fun theStringFormOfAConversationNeverShowsTheTextOrTheSender() {
        val text = latestPerConversation(sequenceOf(unreadRow(1, 10, true))).single().toString()
        assertEquals(false, "texto" in text)
        assertEquals(false, "+10000000001" in text)
    }

    @Test
    fun aConversationKeepsTheIdOfItsLastMessageToLookUpItsRisk() {
        val rows = sequenceOf(SmsRow(1, "+10000000001", "nuevo", 30, id = 21), SmsRow(1, "+10000000001", "viejo", 10, id = 20))
        assertEquals(21L, latestPerConversation(rows).single().lastMessageId)
    }
}
