package com.app.smishing.data.history

import androidx.room.InvalidationTracker
import com.app.smishing.domain.mitigation.ActionNote
import com.app.smishing.domain.mitigation.ActionStatus
import com.app.smishing.domain.mitigation.ActionType
import com.app.smishing.domain.mitigation.MitigationAction
import com.app.smishing.domain.mitigation.MitigationLog

/** Las acciones de mitigación en la base local (#28). Guarda los ids estables de tipo, estado y motivo, no sus nombres. */
class RoomMitigationLog(private val database: HistoryDatabase) : MitigationLog {

    private val dao = database.dao()

    override fun request(recordId: Long, type: ActionType, nowMillis: Long): Long = dao.insertAction(
        MitigationActionEntity(
            analyzedMessageId = recordId,
            type = type.id,
            status = ActionStatus.Requested.id,
            note = null,
            requestedAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
        ),
    )

    override fun update(actionId: Long, status: ActionStatus, note: ActionNote?, nowMillis: Long) =
        dao.updateAction(actionId, status.id, note?.id, nowMillis)

    override fun actionsOf(recordId: Long): List<MitigationAction> = dao.actionsOf(recordId).map(::toAction)

    override fun all(): Map<Long, List<MitigationAction>> = dao.allActions().map(::toAction).groupBy { it.recordId }

    override fun onChange(listener: () -> Unit): AutoCloseable {
        val observer = object : InvalidationTracker.Observer("mitigation_action") {
            override fun onInvalidated(tables: Set<String>) = listener()
        }
        database.invalidationTracker.addObserver(observer)
        return AutoCloseable { database.invalidationTracker.removeObserver(observer) }
    }

    private fun toAction(row: MitigationActionEntity) = MitigationAction(
        id = row.id,
        recordId = row.analyzedMessageId,
        type = ActionType.fromId(row.type),
        status = ActionStatus.fromId(row.status),
        note = row.note?.let(ActionNote::fromId),
        requestedAtMillis = row.requestedAtMillis,
        updatedAtMillis = row.updatedAtMillis,
    )
}
