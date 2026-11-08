package com.app.smishing.domain.messaging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SendSmsTest {

    private class FakeSender(private val outcome: SendOutcome = SendOutcome.Sent(7)) : SmsSender {
        val sent = mutableListOf<Pair<String, String>>()
        override fun send(recipient: String, body: String): SendOutcome {
            sent += recipient to body
            return outcome
        }
    }

    @Test
    fun aLocalNumberTypedWithSpacesIsSentWithoutThem() {
        assertEquals("987654321", normalizeRecipient("987 654 321"))
        assertEquals("987654321", normalizeRecipient("  987-654-321  "))
        assertEquals("987654321", normalizeRecipient("(987) 654.321"))
    }

    @Test
    fun anInternationalNumberKeepsItsPlus() {
        assertEquals("+51987654321", normalizeRecipient("+51 987 654 321"))
    }

    @Test
    fun shortCodesAreAccepted() {
        assertEquals("4040", normalizeRecipient("4040"))
        assertEquals("123", normalizeRecipient("123"))
    }

    @Test
    fun thingsThatAreNotNumbersAreRejected() {
        for (bad in listOf("", "   ", "12", "abc", "98765x321", "Mamá", "98+7654321", "++51987654321", "+", "1234567890123456")) {
            assertNull("Debía rechazar \"$bad\"", normalizeRecipient(bad))
        }
    }

    @Test
    fun aValidMessageIsHandedToTheSenderWithTheNormalizedNumber() {
        val sender = FakeSender()
        val outcome = SendSms(sender).send("987 654 321", "Hola, ¿cómo estás?")

        assertEquals(SendOutcome.Sent(7), outcome)
        assertEquals(listOf("987654321" to "Hola, ¿cómo estás?"), sender.sent)
    }

    @Test
    fun theTextIsSentAsTypedWithoutTrimmingIt() {
        val sender = FakeSender()
        SendSms(sender).send("987654321", "  con espacios  ")
        assertEquals("  con espacios  ", sender.sent.single().second)
    }

    @Test
    fun anInvalidRecipientNeverReachesTheSender() {
        val sender = FakeSender()
        assertEquals(SendOutcome.InvalidRecipient, SendSms(sender).send("abc", "Hola"))
        assertEquals(emptyList<Pair<String, String>>(), sender.sent)
    }

    @Test
    fun anEmptyOrBlankMessageNeverReachesTheSender() {
        val sender = FakeSender()
        assertEquals(SendOutcome.EmptyMessage, SendSms(sender).send("987654321", ""))
        assertEquals(SendOutcome.EmptyMessage, SendSms(sender).send("987654321", "  \n "))
        assertEquals(emptyList<Pair<String, String>>(), sender.sent)
    }

    @Test
    fun theRecipientIsCheckedBeforeTheText() {
        assertEquals(SendOutcome.InvalidRecipient, SendSms(FakeSender()).send("abc", ""))
    }

    @Test
    fun theSendersFailureIsPassedOn() {
        assertEquals(SendOutcome.Failed, SendSms(FakeSender(SendOutcome.Failed)).send("987654321", "Hola"))
        assertEquals(SendOutcome.NotAllowed, SendSms(FakeSender(SendOutcome.NotAllowed)).send("987654321", "Hola"))
    }
}
