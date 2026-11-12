package com.app.smishing.data.history

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * Las tablas del historial (#12), como las define la arquitectura: un mensaje analizado, su resultado (1:1) y las señales que
 * lo explican (1:N). Borrar un mensaje borra en cascada su resultado y sus señales.
 *
 * Las tablas llevan texto de mensajes: la base vive en el almacenamiento privado de la app y está **excluida de las copias de
 * seguridad** (ver `data_extraction_rules.xml`). No está cifrada.
 */
@Entity(tableName = "analyzed_message", indices = [Index("messageId")])
data class AnalyzedMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** El id del mensaje en el buzón del sistema cuando se analizó. No es una clave foránea: el mensaje puede borrarse del buzón. */
    val messageId: Long,
    val sender: String,
    val body: String,
    val receivedAtMillis: Long,
    /** Cuándo la persona lo marcó como seguro (#16), o `null`. Se agregó en la versión 2 de la base. */
    val markedSafeAtMillis: Long? = null,
)

@Entity(
    tableName = "analysis_result",
    foreignKeys = [
        ForeignKey(
            entity = AnalyzedMessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["analyzedMessageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("analyzedMessageId", unique = true), Index("analyzedAtMillis")],
)
data class AnalysisResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val analyzedMessageId: Long,
    val score: Float,
    val modelFlagsIt: Boolean,
    val threshold: Double,
    val modelVersion: String,
    val preprocessingVersion: String,
    val signalRulesVersion: String,
    val policyVersion: String,
    /** `Low`, `Medium` o `High` (el nombre del nivel, estable). */
    val riskLevel: String,
    /** El id de la regla de la política que decidió el nivel (`risk.model_and_evidence`…). */
    val riskRule: String,
    /** Los tipos de señal que contaron como respaldo, separados por coma. */
    val evidence: String,
    val analyzedAtMillis: Long,
)

@Entity(
    tableName = "detected_signal",
    foreignKeys = [
        ForeignKey(
            entity = AnalysisResultEntity::class,
            parentColumns = ["id"],
            childColumns = ["resultId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("resultId")],
)
data class DetectedSignalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val resultId: Long,
    /** `Urgency`, `Impersonation`, `Link` o `DataRequest`. */
    val type: String,
    /** La regla que la detectó (`urgency.status_threat`, `link.url`…). */
    @ColumnInfo(name = "ruleId") val ruleId: String,
    /** La parte del mensaje que la motivó. */
    val fragment: String,
)

/**
 * Una acción de mitigación sobre un mensaje analizado (#28): bloquear al remitente o retirar el mensaje, con su estado. Se agregó
 * en la versión 2 de la base. Borrar el mensaje analizado borra en cascada sus acciones.
 */
@Entity(
    tableName = "mitigation_action",
    foreignKeys = [
        ForeignKey(
            entity = AnalyzedMessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["analyzedMessageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("analyzedMessageId")],
)
data class MitigationActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val analyzedMessageId: Long,
    /** `block` o `remove` (el id estable del tipo). */
    val type: String,
    /** `requested`, `completed`, `failed` o `reverted`. */
    val status: String,
    /** El motivo que acompaña al estado (id estable), o `null`. */
    val note: String?,
    val requestedAtMillis: Long,
    val updatedAtMillis: Long,
)

/** Una fila de la consulta de riesgos (#60). */
data class RiskRow(
    val messageId: Long,
    val recordId: Long,
    val level: String,
    val markedSafeAt: Long?,
    val body: String,
)

/** Un resultado con sus señales. */
data class ResultWithSignals(
    @Embedded val result: AnalysisResultEntity,
    @Relation(parentColumn = "id", entityColumn = "resultId") val signals: List<DetectedSignalEntity>,
)

/** Un mensaje con su resultado y las señales de este. */
data class MessageWithResult(
    @Embedded val message: AnalyzedMessageEntity,
    @Relation(parentColumn = "id", entityColumn = "analyzedMessageId", entity = AnalysisResultEntity::class)
    val result: ResultWithSignals,
)
