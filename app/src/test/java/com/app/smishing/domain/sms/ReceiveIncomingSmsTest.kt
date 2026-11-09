package com.app.smishing.domain.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiveIncomingSmsTest {

    private class FakeInbox : SmsInbox {
        val stored = mutableListOf<IncomingSms>()
        override fun contains(message: IncomingSms) = message in stored
        override fun store(message: IncomingSms): Long {
            stored += message
            return stored.size.toLong()
        }
    }

    private val inbox = FakeInbox()
    private val receive = ReceiveIncomingSms(inbox)

    private fun part(body: String?, sender: String? = "+10000000001", sentAt: Long = 1_000L) = SmsPart(sender, body, sentAt)

    @Test
    fun aSinglePartMessageIsStoredOnce() {
        assertEquals(ReceiveOutcome.Stored, receive.receive(listOf(part("Hola"))).outcome)
        assertEquals(listOf(IncomingSms("+10000000001", "Hola", 1_000L)), inbox.stored)
    }

    @Test
    fun aLongMessageArrivingInPartsIsJoinedIntoOne() {
        receive.receive(listOf(part("Primera parte, "), part("segunda parte, ", sentAt = 1_001L), part("tercera.")))
        assertEquals(1, inbox.stored.size)
        assertEquals("Primera parte, segunda parte, tercera.", inbox.stored.single().body)
        assertEquals(1_000L, inbox.stored.single().sentAtMillis)
    }

    @Test
    fun theSameDeliveryTwiceIsStoredOnlyOnce() {
        assertEquals(ReceiveOutcome.Stored, receive.receive(listOf(part("Hola"))).outcome)
        assertEquals(ReceiveOutcome.Duplicate, receive.receive(listOf(part("Hola"))).outcome)
        assertEquals(1, inbox.stored.size)
    }

    @Test
    fun theSameTextSentAtAnotherTimeIsANewMessage() {
        receive.receive(listOf(part("Hola", sentAt = 1_000L)))
        assertEquals(ReceiveOutcome.Stored, receive.receive(listOf(part("Hola", sentAt = 61_000L))).outcome)
        assertEquals(2, inbox.stored.size)
    }

    @Test
    fun theSameTextFromAnotherSenderIsANewMessage() {
        receive.receive(listOf(part("Hola")))
        assertEquals(ReceiveOutcome.Stored, receive.receive(listOf(part("Hola", sender = "+10000000002"))).outcome)
        assertEquals(2, inbox.stored.size)
    }

    @Test
    fun nothingIsStoredWithoutSenderOrText() {
        assertEquals(ReceiveOutcome.Ignored, receive.receive(emptyList()).outcome)
        assertEquals(ReceiveOutcome.Ignored, receive.receive(listOf(part("Hola", sender = null))).outcome)
        assertEquals(ReceiveOutcome.Ignored, receive.receive(listOf(part("Hola", sender = "  "))).outcome)
        assertEquals(ReceiveOutcome.Ignored, receive.receive(listOf(part(null))).outcome)
        assertEquals(ReceiveOutcome.Ignored, receive.receive(listOf(part(""))).outcome)
        assertEquals(emptyList<IncomingSms>(), inbox.stored)
    }

    @Test
    fun theSenderIsTakenFromTheFirstPartThatHasOne() {
        assertEquals("+10000000001", assembleIncoming(listOf(part("a", sender = null), part("b")))?.sender)
        assertNull(assembleIncoming(listOf(part("a", sender = null))))
    }

    @Test
    fun aStoredMessageComesBackWithItsInboxIdAndTheAssembledMessage() {
        val result = receive.receive(listOf(part("Hola, "), part("mundo", sentAt = 1_001L)))

        assertEquals(ReceiveOutcome.Stored, result.outcome)
        assertEquals(1L, result.messageId)
        assertEquals(IncomingSms("+10000000001", "Hola, mundo", 1_000L), result.message)
    }

    @Test
    fun aDuplicateOrAnIgnoredMessageHasNoIdSoNothingIsAnalysedTwice() {
        receive.receive(listOf(part("Hola")))
        for (result in listOf(receive.receive(listOf(part("Hola"))), receive.receive(emptyList()))) {
            assertEquals(null, result.messageId)
            assertEquals(null, result.message)
        }
    }

    @Test
    fun theMessageTextNeverAppearsInItsStringForm() {
        val text = IncomingSms("+10000000001", "Tu codigo es 458920", 1_000L).toString()
        assertFalse("458920" in text)
        assertFalse("+10000000001" in text)
    }
}
