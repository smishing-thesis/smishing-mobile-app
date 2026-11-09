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

    @Query("DELETE FROM analyzed_message WHERE id = :id")
    abstract fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM analyzed_message")
    abstract fun messageCount(): Int

    @Query("SELECT COUNT(*) FROM analysis_result")
    abstract fun resultCount(): Int

    @Query("SELECT COUNT(*) FROM detected_signal")
    abstract fun signalCount(): Int
}
