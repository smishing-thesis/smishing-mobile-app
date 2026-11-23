package com.app.smishing.domain.sharing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Una cola en memoria, en el orden en que se agregó. Todos los pendientes son de la versión de modelo `1.0.0` y marcados como estafa. */
internal class FakeQueue(items: List<Pair<Long, String>> = emptyList()) : PendingContributions {
    val rows = LinkedHashMap<Long, String>().apply { items.forEach { put(it.first, it.second) } }
    override fun add(analyzedMessageId: Long, nowMillis: Long) { rows.putIfAbsent(analyzedMessageId, "") }
    override fun next(limit: Int) =
        rows.entries.take(limit).map { PendingContribution(it.key, it.value, ContributionLabel.Smishing, "1.0.0") }
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

    private fun accepted(count: Int) = ContributionSendResult.Delivered(List(count) { ItemResult(ItemOutcome.Accepted) })

    /** Contesta con lo que se le indique, en orden; si se le acaba la lista, acepta todo el lote. */
    private class FakeSender(vararg results: ContributionSendResult, private val onSend: () -> Unit = {}) : ContributionSender {
        private val results = ArrayDeque(results.toList())
        val batches = mutableListOf<List<Contribution>>()
        override fun send(batch: List<Contribution>): ContributionSendResult {
            batches += batch
            onSend()
            return results.removeFirstOrNull() ?: ContributionSendResult.Delivered(List(batch.size) { ItemResult(ItemOutcome.Accepted) })
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
        val sender = FakeSender(accepted(2), ContributionSendResult.Retry())
        assertEquals(UploadOutcome.Retry(2), uploader(queue, FakeConsent(), sender).run())
        // El lote que falló y lo que seguía se conservan; solo salió el primero.
        assertEquals(listOf(3L, 4L, 5L), queue.rows.keys.toList())
    }

    @Test
    fun whatTheDestinationAsksToWaitIsPassedOn() {
        val queue = queueOf(3)
        val sender = FakeSender(ContributionSendResult.Retry(retryAfterSeconds = 30))
        assertEquals(UploadOutcome.Retry(0, 30), uploader(queue, FakeConsent(), sender).run())
        assertEquals(3, queue.count())
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
    fun aTextTooLongForTheBackendIsDroppedAndDoesNotBlockTheOthers() {
        // Si saliera, el backend rechazaría el lote entero (422) y la cola quedaría detenida por un solo mensaje.
        val queue = FakeQueue(listOf(1L to "a".repeat(PrepareContribution.MAX_TEXT_LENGTH + 1), 2L to "Mensaje de prueba"))
        val sender = FakeSender()
        assertEquals(UploadOutcome.Done(1), uploader(queue, FakeConsent(), sender, batchSize = 5).run())
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

    // --- Lo que contesta el backend (#72) ---

    @Test
    fun theLabelAndTheModelVersionTravelWithEachText() {
        val sender = FakeSender()
        uploader(queueOf(1), FakeConsent(), sender).run()
        val sent = sender.batches.single().single()
        assertEquals(ContributionLabel.Smishing, sent.label)
        assertEquals("1.0.0", sent.modelVersion)
    }

    @Test
    fun aDuplicateAndARejectedOneAlsoLeaveTheQueueAndOnlyTheRejectedAreCountedAsSuchWithoutRetrying() {
        val queue = queueOf(3)
        val sender = FakeSender(
            ContributionSendResult.Delivered(
                listOf(
                    ItemResult(ItemOutcome.Accepted),
                    ItemResult(ItemOutcome.Duplicate),
                    ItemResult(ItemOutcome.Rejected, "not_anonymized"),
                ),
            ),
        )
        assertEquals(UploadOutcome.Done(sent = 2, rejected = 1), uploader(queue, FakeConsent(), sender, batchSize = 3).run())
        assertEquals("Lo rechazado no se reintenta sin fin", 0, queue.count())
        assertEquals(1, sender.batches.size)
    }

    @Test
    fun ifTheNumberOfResultsDoesNotMatchTheBatchNothingIsRemovedAndItStopsWithoutRetryingForever() {
        val queue = queueOf(3)
        val sender = FakeSender(accepted(2))
        assertEquals(UploadOutcome.Failed, uploader(queue, FakeConsent(), sender, batchSize = 3).run())
        assertEquals(3, queue.count())
        assertEquals("No se reintentó en la misma pasada", 1, sender.batches.size)
    }

    @Test
    fun aKeyTheDestinationDoesNotKnowStopsTheSendingAndKeepsTheQueue() {
        val queue = queueOf(4)
        val sender = FakeSender(ContributionSendResult.Unauthorized)
        assertEquals(UploadOutcome.Unauthorized, uploader(queue, FakeConsent(), sender).run())
        assertEquals(4, queue.count())
        assertEquals(1, sender.batches.size)
    }

    @Test
    fun aBatchTheDestinationRefusesAsAWholeKeepsTheQueueAndStops() {
        val queue = queueOf(4)
        val sender = FakeSender(ContributionSendResult.Failed)
        assertEquals(UploadOutcome.Failed, uploader(queue, FakeConsent(), sender).run())
        assertEquals(4, queue.count())
        assertEquals(1, sender.batches.size)
    }
}
