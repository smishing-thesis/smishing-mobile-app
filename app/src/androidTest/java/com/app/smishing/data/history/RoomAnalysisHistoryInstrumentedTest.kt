package com.app.smishing.data.history

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.app.smishing.domain.classification.ClassificationResult
import com.app.smishing.domain.history.AnalysisRecord
import com.app.smishing.domain.risk.RiskPolicy
import com.app.smishing.domain.risk.RiskLevel
import com.app.smishing.domain.signals.DetectedSignal
import com.app.smishing.domain.signals.SignalAnalysis
import com.app.smishing.domain.signals.SignalType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * #12: cada registro conserva, de forma local, el resultado, el nivel, la explicación y las versiones del modelo. Se prueba con
 * una base real (Room sobre SQLite) en el dispositivo, con mensajes sintéticos.
 */
@RunWith(AndroidJUnit4::class)
class RoomAnalysisHistoryInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: HistoryDatabase
    private lateinit var history: RoomAnalysisHistory

    @Before
    fun open() {
        database = HistoryDatabase.inMemory(context)
        history = RoomAnalysisHistory(database)
    }

    @After
    fun close() = database.close()

    private fun assessment(
        level: RiskLevel = RiskLevel.High,
        flagged: Boolean = true,
        score: Float = 0.9731f,
        signals: List<DetectedSignal> = ALL_SIGNALS,
    ) = RiskPolicy.decide(
        ClassificationResult.Classified(score, flagged, 0.9034745693206787, "1.0.0", "1.1.0"),
        SignalAnalysis("1.1.0", signals),
    ).also { assertEquals("El nivel de prueba debía ser el que decide la política", level, it.level) }

    private fun record(
        messageId: Long = 7,
        sender: String = "+10000000001",
        body: String = "Mensaje sintético con http://ejemplo.invalid",
        at: Long = 1_000L,
        assessment: com.app.smishing.domain.risk.RiskAssessment = assessment(),
    ) = AnalysisRecord(0, messageId, sender, body, at, at + 50, assessment)

    @Test
    fun deletingByMessageRemovesOnlyTheRecordOfThatMessageWithItsResultAndSignals() {
        val mine = history.save(record(messageId = 7, body = "Mensaje sintético uno"))
        val sameIdOtherText = history.save(record(messageId = 7, body = "Otro texto con el mismo id"))
        val otherId = history.save(record(messageId = 8, body = "Mensaje sintético uno"))
        assertEquals(3, database.dao().messageCount())

        // Mismo id y mismo texto: es de ese mensaje. Un registro con otro texto (el buzón reutilizó el id) o con otro id no se toca.
        assertEquals(1, history.deleteByMessage(7, "Mensaje sintético uno"))
        assertNull(history.byId(mine))
        assertNotNull(history.byId(sameIdOtherText))
        assertNotNull(history.byId(otherId))
        // Con el registro se va su resultado y sus señales.
        assertEquals(2, database.dao().messageCount())
        assertEquals(2, database.dao().resultCount())
        assertEquals(0, history.deleteByMessage(7, "Mensaje sintético uno"))
    }

    @Test
    fun aRecordComesBackExactlyAsItWasSaved() {
        val saved = record()
        val id = history.save(saved)

        val read = checkNotNull(history.byId(id))
        assertEquals(saved.copy(id = id), read)
        // Y todo lo que pide la historia: resultado, nivel, explicación y versión del modelo.
        assertEquals(RiskLevel.High, read.assessment.level)
        assertEquals("risk.model_and_evidence", read.assessment.rule.id)
        assertEquals(0.9731f, read.assessment.model.score)
        assertEquals(0.9034745693206787, read.assessment.model.threshold, 0.0)
        assertTrue(read.assessment.model.isSmishing)
        assertEquals("1.0.0", read.assessment.model.modelVersion)
        assertEquals("1.1.0", read.assessment.model.preprocessingVersion)
        assertEquals("1.1.0", read.assessment.signals.rulesVersion)
        assertEquals("1.0.0", read.assessment.policyVersion)
        assertEquals(
            listOf("urgency.status_threat" to "sera suspendida", "impersonation.bbva" to "BBVA", "link.url" to "http://bbva-pe.top", "data_request.ask_for_data" to "Actualice sus datos"),
            read.assessment.signals.signals.map { it.ruleId to it.fragment },
        )
        assertEquals(setOf(SignalType.Urgency, SignalType.Impersonation, SignalType.DataRequest), read.assessment.evidence)
    }

    @Test
    fun theVersionsAreStoredInTheDatabaseItselfNotOnlyInMemory() {
        history.save(record())

        database.openHelper.readableDatabase.query(
            "SELECT modelVersion, preprocessingVersion, signalRulesVersion, policyVersion, riskLevel, riskRule FROM analysis_result",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(
                listOf("1.0.0", "1.1.0", "1.1.0", "1.0.0", "High", "risk.model_and_evidence"),
                (0 until 6).map { cursor.getString(it) },
            )
        }
        database.openHelper.readableDatabase.query("SELECT type, ruleId, fragment FROM detected_signal ORDER BY id").use { cursor ->
            assertEquals(4, cursor.count)
        }
    }

    @Test
    fun everyLevelAndAMessageWithoutSignalsAreKept() {
        val low = assessment(RiskLevel.Low, flagged = false, score = 0.05f, signals = emptyList())
        val medium = assessment(RiskLevel.Medium, flagged = true, score = 0.95f, signals = emptyList())
        val lowId = history.save(record(messageId = 1, assessment = low))
        val mediumId = history.save(record(messageId = 2, assessment = medium))

        assertEquals(low, history.byId(lowId)!!.assessment)
        assertEquals(medium, history.byId(mediumId)!!.assessment)
        assertEquals(emptyList<DetectedSignal>(), history.byId(lowId)!!.assessment.signals.signals)
        assertEquals("risk.no_evidence", history.byId(lowId)!!.assessment.rule.id)
        assertEquals("risk.model_only", history.byId(mediumId)!!.assessment.rule.id)
    }

    @Test
    fun theNewestRecordComesFirst() {
        history.save(record(messageId = 1, sender = "+1"))
        history.save(record(messageId = 2, sender = "+2"))
        history.save(record(messageId = 3, sender = "+3"))
        assertEquals(listOf("+3", "+2", "+1"), history.all().map { it.sender })
    }

    @Test
    fun aMessageIsFoundByItsIdInTheInboxAndTheLatestRecordWins() {
        history.save(record(messageId = 9, body = "primero"))
        history.save(record(messageId = 9, body = "segundo"))
        history.save(record(messageId = 10, body = "otro"))

        assertEquals("segundo", history.byMessageId(9)?.body)
        assertNull(history.byMessageId(404))
        assertNull(history.byId(404))
    }

    @Test
    fun deletingAMessageDeletesItsResultAndItsSignalsOnly() {
        val first = history.save(record(messageId = 1))
        history.save(record(messageId = 2))
        val dao = database.dao()
        assertEquals(listOf(2, 2, 8), listOf(dao.messageCount(), dao.resultCount(), dao.signalCount()))

        dao.delete(first)

        assertEquals(listOf(1, 1, 4), listOf(dao.messageCount(), dao.resultCount(), dao.signalCount()))
        assertNull(history.byId(first))
        assertEquals(1, history.all().size)
    }

    @Test
    fun theRecordSurvivesClosingAndReopeningTheDatabase() {
        val file = "history-test-${System.nanoTime()}.db"
        try {
            val first = HistoryDatabase.inFile(context, file)
            val id = RoomAnalysisHistory(first).save(record())
            first.close()

            val second = HistoryDatabase.inFile(context, file)
            try {
                val read = checkNotNull(RoomAnalysisHistory(second).byId(id))
                assertEquals(record().copy(id = id), read)
            } finally {
                second.close()
            }
        } finally {
            context.deleteDatabase(file)
        }
    }

    @Test
    fun savingIsAllOrNothing() {
        val dao = database.dao()
        val message = AnalyzedMessageEntity(messageId = 1, sender = "s", body = "b", receivedAtMillis = 1)
        val result = AnalysisResultEntity(
            analyzedMessageId = 0, score = 0.5f, modelFlagsIt = false, threshold = 0.9, modelVersion = "1.0.0", preprocessingVersion = "1.1.0",
            signalRulesVersion = "1.1.0", policyVersion = "1.0.0", riskLevel = "Low", riskRule = "risk.no_evidence", evidence = "", analyzedAtMillis = 2,
        )
        val signal = DetectedSignalEntity(resultId = 0, type = "Urgency", ruleId = "x", fragment = "f")
        // Si algo falla después de guardar el mensaje, su resultado y sus señales (aquí, un error dentro de la misma transacción),
        // no queda nada a medias.
        val failing = IllegalStateException("falla a mitad del guardado")
        try {
            database.runInTransaction {
                dao.insertAll(message, result, listOf(signal))
                throw failing
            }
        } catch (expected: IllegalStateException) {
            assertTrue(expected === failing)
        }
        assertEquals(listOf(0, 0, 0), listOf(dao.messageCount(), dao.resultCount(), dao.signalCount()))
    }

    @Test
    fun listenersAreToldWhenTheHistoryChangesAndStopWhenClosed() {
        val calls = AtomicInteger()
        val changed = CountDownLatch(1)
        val subscription = history.onChange {
            calls.incrementAndGet()
            changed.countDown()
        }

        history.save(record())
        assertTrue("Debía avisar del cambio", changed.await(5, TimeUnit.SECONDS))
        val callsWhileSubscribed = calls.get()

        subscription.close()
        // Cerrada la suscripción, otro cambio no debe llegar al oyente.
        history.save(record(messageId = 2))
        Thread.sleep(700)
        assertEquals(callsWhileSubscribed, calls.get())
        assertNotNull(history.byMessageId(2))
    }

    @Test
    fun theStringFormOfARecordNeverShowsItsText() {
        val text = record().toString()
        assertFalse("ejemplo.invalid" in text)
        assertFalse("+10000000001" in text)
    }

    private companion object {
        val ALL_SIGNALS = listOf(
            DetectedSignal(SignalType.Urgency, "urgency.status_threat", "sera suspendida"),
            DetectedSignal(SignalType.Impersonation, "impersonation.bbva", "BBVA"),
            DetectedSignal(SignalType.Link, "link.url", "http://bbva-pe.top"),
            DetectedSignal(SignalType.DataRequest, "data_request.ask_for_data", "Actualice sus datos"),
        )
    }
}
