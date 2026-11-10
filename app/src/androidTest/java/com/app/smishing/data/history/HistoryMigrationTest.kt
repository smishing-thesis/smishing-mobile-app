package com.app.smishing.data.history

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.app.smishing.domain.mitigation.ActionType
import com.app.smishing.domain.risk.RiskLevel
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * La migración de la base del historial de la versión 1 (#12) a la 2 (#28) **conserva lo guardado**. La base de la versión 1 se
 * arma con el SQL exacto del esquema exportado (`app/schemas/.../1.json`), con datos sintéticos; luego se abre con Room, que corre
 * la migración y **valida el resultado contra el esquema de la versión 2** (si faltara una columna o un índice, no abre).
 */
@RunWith(AndroidJUnit4::class)
class HistoryMigrationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val name = "migration-test.db"

    @Before
    fun setUp() {
        context.deleteDatabase(name)
        createVersion1WithOneAnalysis()
    }

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    private fun version1Sql(): List<String> {
        val json = instrumentation.context.assets.open("com.app.smishing.data.history.HistoryDatabase/1.json").bufferedReader().use { it.readText() }
        val entities = JSONObject(json).getJSONObject("database").getJSONArray("entities")
        val statements = mutableListOf<String>()
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            statements += entity.getString("createSql").replace("\${TABLE_NAME}", table)
            val indices = entity.optJSONArray("indices")
            for (j in 0 until (indices?.length() ?: 0)) {
                statements += indices!!.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table)
            }
        }
        return statements
    }

    private fun createVersion1WithOneAnalysis() {
        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null)
        try {
            for (sql in version1Sql()) db.execSQL(sql)
            db.execSQL("INSERT INTO analyzed_message (id, messageId, sender, body, receivedAtMillis) VALUES (1, 10, '+10000000001', 'Texto sintético', 500)")
            db.execSQL(
                "INSERT INTO analysis_result (id, analyzedMessageId, score, modelFlagsIt, threshold, modelVersion, preprocessingVersion, " +
                    "signalRulesVersion, policyVersion, riskLevel, riskRule, evidence, analyzedAtMillis) " +
                    "VALUES (1, 1, 0.97, 1, 0.9, '1.0.0', '1.1.0', '1.1.0', '1.0.0', 'High', 'risk.model_and_evidence', 'Urgency', 600)",
            )
            db.execSQL("INSERT INTO detected_signal (id, resultId, type, ruleId, fragment) VALUES (1, 1, 'Urgency', 'urgency.test', 'fragmento')")
            db.version = 1
        } finally {
            db.close()
        }
    }

    @Test
    fun theVersion1DatabaseHasNoActionsTableYet() {
        val db = SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            db.rawQuery("SELECT name FROM sqlite_master WHERE name = 'mitigation_action'", null).use { assertEquals(0, it.count) }
        } finally {
            db.close()
        }
    }

    @Test
    fun migratingKeepsWhatWasSavedAndTheDatabaseOpensWithTheVersion2Schema() {
        val database = HistoryDatabase.inFile(context, name)
        try {
            val history = RoomAnalysisHistory(database)
            val record = history.all().single()
            assertEquals("+10000000001", record.sender)
            assertEquals("Texto sintético", record.body)
            assertEquals(500L, record.receivedAtMillis)
            assertEquals(10L, record.messageId)
            assertEquals(RiskLevel.High, record.assessment.level)
            assertEquals(listOf("urgency.test"), record.assessment.signals.signals.map { it.ruleId })
            assertNull("Lo viejo queda sin marca de seguro", record.markedSafeAtMillis)
            assertTrue(RoomMitigationLog(database).all().isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun onAnOldAnalysisActionsCanBeRecordedAndItCanBeMarkedAsSafe() {
        val database = HistoryDatabase.inFile(context, name)
        try {
            val history = RoomAnalysisHistory(database)
            val log = RoomMitigationLog(database)
            val record = history.all().single()

            log.request(record.id, ActionType.BlockSender, 700)
            history.markSafe(record.id, 800)

            assertEquals(1, log.actionsOf(record.id).size)
            assertEquals(800L, history.byId(record.id)?.markedSafeAtMillis)
        } finally {
            database.close()
        }
    }
}
