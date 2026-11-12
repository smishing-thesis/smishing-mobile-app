package com.app.smishing.data.history

import androidx.room.InvalidationTracker
import com.app.smishing.domain.classification.ClassificationResult
import com.app.smishing.domain.history.AnalysisHistory
import com.app.smishing.domain.history.AnalysisRecord
import com.app.smishing.domain.history.MessageRisk
import com.app.smishing.domain.risk.RiskAssessment
import com.app.smishing.domain.risk.RiskLevel
import com.app.smishing.domain.risk.RiskRule
import com.app.smishing.domain.signals.DetectedSignal
import com.app.smishing.domain.signals.SignalAnalysis
import com.app.smishing.domain.signals.SignalType

/**
 * El historial en la base local (Room). Guarda el registro completo y lo devuelve igual: el nivel, la regla que lo decidió, lo
 * que dijo el modelo con sus versiones y las señales con su fragmento, de modo que el detalle se arma desde lo guardado.
 */
class RoomAnalysisHistory(private val database: HistoryDatabase) : AnalysisHistory {

    private val dao = database.dao()

    override fun onChange(listener: () -> Unit): AutoCloseable {
        val observer = object : InvalidationTracker.Observer("analyzed_message") {
            override fun onInvalidated(tables: Set<String>) = listener()
        }
        database.invalidationTracker.addObserver(observer)
        return AutoCloseable { database.invalidationTracker.removeObserver(observer) }
    }

    override fun save(record: AnalysisRecord): Long {
        val assessment = record.assessment
        val model = assessment.model
        val message = AnalyzedMessageEntity(
            messageId = record.messageId,
            sender = record.sender,
            body = record.body,
            receivedAtMillis = record.receivedAtMillis,
            markedSafeAtMillis = record.markedSafeAtMillis,
        )
        val result = AnalysisResultEntity(
            analyzedMessageId = 0, // lo completa el DAO al guardar el mensaje
            score = model.score,
            modelFlagsIt = model.isSmishing,
            threshold = model.threshold,
            modelVersion = model.modelVersion,
            preprocessingVersion = model.preprocessingVersion,
            signalRulesVersion = assessment.signals.rulesVersion,
            policyVersion = assessment.policyVersion,
            riskLevel = assessment.level.name,
            riskRule = assessment.rule.id,
            evidence = assessment.evidence.joinToString(",") { it.name },
            analyzedAtMillis = record.analyzedAtMillis,
        )
        val signals = assessment.signals.signals.map {
            DetectedSignalEntity(resultId = 0, type = it.type.name, ruleId = it.ruleId, fragment = it.fragment)
        }
        return dao.insertAll(message, result, signals)
    }

    override fun risks(): Map<Long, MessageRisk> =
        // Ordenadas por registro: si un mismo mensaje tiene más de uno, queda el más reciente.
        dao.risks().associate {
            it.messageId to MessageRisk(it.messageId, it.recordId, RiskLevel.valueOf(it.level), it.markedSafeAt != null, it.body)
        }

    override fun markSafe(id: Long, nowMillis: Long) = dao.markSafe(id, nowMillis)

    override fun updateMessageId(id: Long, messageId: Long) = dao.updateMessageId(id, messageId)

    override fun all(): List<AnalysisRecord> = dao.all().map(::toRecord)

    override fun byId(id: Long): AnalysisRecord? = dao.byId(id)?.let(::toRecord)

    override fun byMessageId(messageId: Long): AnalysisRecord? = dao.byMessageId(messageId)?.let(::toRecord)

    private fun toRecord(row: MessageWithResult): AnalysisRecord {
        val result = row.result.result
        val signals = row.result.signals.sortedBy { it.id }.map {
            DetectedSignal(SignalType.valueOf(it.type), it.ruleId, it.fragment)
        }
        val assessment = RiskAssessment(
            level = RiskLevel.valueOf(result.riskLevel),
            rule = RiskRule.entries.first { it.id == result.riskRule },
            model = ClassificationResult.Classified(
                score = result.score,
                isSmishing = result.modelFlagsIt,
                threshold = result.threshold,
                modelVersion = result.modelVersion,
                preprocessingVersion = result.preprocessingVersion,
            ),
            signals = SignalAnalysis(result.signalRulesVersion, signals),
            evidence = result.evidence.split(",").filter { it.isNotEmpty() }.mapTo(LinkedHashSet()) { SignalType.valueOf(it) },
            policyVersion = result.policyVersion,
        )
        return AnalysisRecord(
            id = row.message.id,
            messageId = row.message.messageId,
            sender = row.message.sender,
            body = row.message.body,
            receivedAtMillis = row.message.receivedAtMillis,
            analyzedAtMillis = result.analyzedAtMillis,
            assessment = assessment,
            markedSafeAtMillis = row.message.markedSafeAtMillis,
        )
    }
}
