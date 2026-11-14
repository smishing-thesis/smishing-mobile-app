package com.app.smishing.data.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class HistoryDao {

    @Insert
    protected abstract fun insertMessage(message: AnalyzedMessageEntity): Long

    @Insert
    protected abstract fun insertResult(result: AnalysisResultEntity): Long

    @Insert
    protected abstract fun insertSignals(signals: List<DetectedSignalEntity>)

    /** Guarda el mensaje, su resultado y sus señales juntos: o se guarda todo, o nada. Devuelve el id del mensaje. */
    @Transaction
    open fun insertAll(message: AnalyzedMessageEntity, result: AnalysisResultEntity, signals: List<DetectedSignalEntity>): Long {
        val messageId = insertMessage(message)
        val resultId = insertResult(result.copy(analyzedMessageId = messageId))
        if (signals.isNotEmpty()) insertSignals(signals.map { it.copy(resultId = resultId) })
        return messageId
    }

    @Transaction
    @Query("SELECT * FROM analyzed_message ORDER BY id DESC")
    abstract fun all(): List<MessageWithResult>

    @Transaction
    @Query("SELECT * FROM analyzed_message WHERE id = :id")
    abstract fun byId(id: Long): MessageWithResult?

    /** El registro más reciente del mensaje con ese id en el buzón. */
    @Transaction
    @Query("SELECT * FROM analyzed_message WHERE messageId = :messageId ORDER BY id DESC LIMIT 1")
    abstract fun byMessageId(messageId: Long): MessageWithResult?

    @Query("UPDATE analyzed_message SET markedSafeAtMillis = :nowMillis WHERE id = :id")
    abstract fun markSafe(id: Long, nowMillis: Long)

    @Query("UPDATE analyzed_message SET messageId = :messageId WHERE id = :id")
    abstract fun updateMessageId(id: Long, messageId: Long)

    @Insert
    abstract fun insertAction(action: MitigationActionEntity): Long

    @Query("UPDATE mitigation_action SET status = :status, note = :note, updatedAtMillis = :nowMillis WHERE id = :id")
    abstract fun updateAction(id: Long, status: String, note: String?, nowMillis: Long)

    @Query("SELECT * FROM mitigation_action WHERE analyzedMessageId = :recordId ORDER BY id")
    abstract fun actionsOf(recordId: Long): List<MitigationActionEntity>

    @Query("SELECT * FROM mitigation_action ORDER BY id")
    abstract fun allActions(): List<MitigationActionEntity>

    /** El riesgo de cada mensaje, sin señales ni explicación: una sola lectura para marcar la lista de mensajes (#60). */
    @Query(
        "SELECT m.messageId AS messageId, m.id AS recordId, r.riskLevel AS level, m.markedSafeAtMillis AS markedSafeAt, m.body AS body " +
            "FROM analyzed_message m JOIN analysis_result r ON r.analyzedMessageId = m.id ORDER BY m.id",
    )
    abstract fun risks(): List<RiskRow>

    @Query("DELETE FROM analyzed_message WHERE id IN (:ids)")
    abstract fun deleteByIds(ids: List<Long>): Int

    @Query("DELETE FROM analyzed_message")
    abstract fun deleteAll(): Int

    @Query("DELETE FROM analyzed_message WHERE messageId = :messageId AND body = :body")
    abstract fun deleteByMessage(messageId: Long, body: String): Int

    @Query("DELETE FROM analyzed_message WHERE id = :id")
    abstract fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM analyzed_message")
    abstract fun messageCount(): Int

    @Query("SELECT COUNT(*) FROM analysis_result")
    abstract fun resultCount(): Int

    @Query("SELECT COUNT(*) FROM detected_signal")
    abstract fun signalCount(): Int
}
