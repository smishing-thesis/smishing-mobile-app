package com.app.smishing.domain.sharing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Una cola en memoria, en el orden en que se agregó. */
internal class FakeQueue(items: List<Pair<Long, String>> = emptyList()) : PendingContributions {
    val rows = LinkedHashMap<Long, String>().apply { items.forEach { put(it.first, it.second) } }
    override fun add(analyzedMessageId: Long, nowMillis: Long) { rows.putIfAbsent(analyzedMessageId, "") }
    override fun next(limit: Int) = rows.entries.take(limit).map { PendingContribution(it.key, it.value) }
    override fun remove(ids: Collection<Long>) { ids.forEach(rows::remove) }
    override fun clear() = rows.clear()
    override fun count() = rows.size
}

internal class FakeConsent(var current: Boolean = true) : ContributionConsent {
    override fun isGranted() = current
    override fun setGranted(granted: Boolean) { current = granted }
}

class SendPendingContributionsTest {

    // Textos y números sintéticos: nunca son reales.
    private fun queueOf(count: Int) = FakeQueue((1L..count).map { it to "Mensaje de prueba $it" })

    private class FakeSender(vararg results: ContributionSendResult, private val onSend: () -> Unit = {}) : ContributionSender {
        private val results = ArrayDeque(results.toList())
        val batches = mutableListOf<List<Contribution>>()
        override fun send(batch: List<Contribution>): ContributionSendResult {
            batches += batch
            onSend()
            return results.removeFirstOrNull() ?: ContributionSendResult.Sent
        }
    }

    private fun uploader(queue: FakeQueue, consent: FakeConsent, sender: ContributionSender, batchSize: Int = 2) =
        SendPendingContributions(queue, PrepareContribution(consent), sender, batchSize)

    @Test
    fun manyPendingGoOutInBatchesOfTheGivenSizeAndTheQueueEndsEmpty() {
        val queue = queueOf(5)
        val sender = FakeSender()
        assertEquals(UploadOutcome.Done(5), uploader(queue, FakeConsent(), sender).run())
        assertEquals(listOf(2, 2, 1), sender.batches.map { it.size })
        assertEquals(0, queue.count())
    }

    @Test
    fun whenABatchFailsTheRestStaysPendingAndAnotherAttemptIsRequested() {
        val queue = queueOf(5)
        val sender = FakeSender(ContributionSendResult.Sent, ContributionSendResult.Retry)
        assertEquals(UploadOutcome.Retry(2), uploader(queue, FakeConsent(), sender).run())
        // El lote que falló y lo que seguía se conservan; solo salió el primero.
        assertEquals(listOf(3L, 4L, 5L), queue.rows.keys.toList())
    }

    @Test
    fun withoutADestinationNothingLeavesAndEverythingIsKept() {
        val queue = queueOf(3)
        assertEquals(UploadOutcome.NotConfigured, uploader(queue, FakeConsent(), FakeSender(ContributionSendResult.NotConfigured)).run())
        assertEquals(3, queue.count())
    }

    @Test
    fun revokingTheConsentBetweenBatchesStopsTheSendingAndEmptiesTheQueue() {
        val queue = queueOf(5)
        val consent = FakeConsent()
        val sender = FakeSender(onSend = { consent.current = false })
        assertEquals(UploadOutcome.ConsentRevoked, uploader(queue, consent, sender).run())
        assertEquals("Solo salió el primer lote", 1, sender.batches.size)
        assertEquals(0, queue.count())
    }

    @Test
    fun theTextIsAnonymizedBeforeItLeavesAndOneThatStaysIdentifiableIsDroppedNotSent() {
        val queue = FakeQueue(listOf(1L to "Llame al 912345678 hoy", 2L to "Mensaje de prueba"))
        // Un desidentificador que deja pasar el primer texto tal cual: la comprobación final lo rechaza.
        val leaky = object : TextAnonymizer {
            override fun anonymize(text: String) =
                if (text.contains("912345678")) Anonymized(text, AnonymizationRules.VERSION, 0) else Anonymizer().anonymize(text)
            override fun isAnonymized(text: String) = Anonymizer().isAnonymized(text)
        }
        val sender = FakeSender()
        val uploader = SendPendingContributions(queue, PrepareContribution(FakeConsent()) { leaky }, sender, 5)
        assertEquals(UploadOutcome.Done(1), uploader.run())
        assertEquals(listOf("Mensaje de prueba"), sender.batches.single().map { it.text })
        assertEquals(0, queue.count())
    }

    @Test
    fun aPhoneNumberNeverLeavesInTheClear() {
        val queue = FakeQueue(listOf(1L to "Llame al 912345678 hoy"))
        val sender = FakeSender()
        uploader(queue, FakeConsent(), sender).run()
        val text = sender.batches.single().single().text
        assertTrue(text.contains("<PHONE_NUMBER>"))
        assertFalse(text.contains("912345678"))
    }
}
