package com.app.smishing.data.history

import com.app.smishing.domain.sharing.ContributionLabel
import com.app.smishing.domain.sharing.PendingContribution
import com.app.smishing.domain.sharing.PendingContributions

/** La cola de aportes pendientes (#71) en la base local del historial. */
class RoomPendingContributions(database: HistoryDatabase) : PendingContributions {

    private val dao = database.dao()

    override fun add(analyzedMessageId: Long, nowMillis: Long) = dao.insertPending(PendingContributionEntity(analyzedMessageId, nowMillis))

    override fun next(limit: Int): List<PendingContribution> = dao.nextPending(limit).map {
        PendingContribution(it.analyzedMessageId, it.body, ContributionLabel.of(it.modelFlagsIt), it.modelVersion)
    }

    override fun remove(ids: Collection<Long>) {
        if (ids.isNotEmpty()) dao.deletePending(ids.toList())
    }

    override fun clear() = dao.clearPending()

    override fun count(): Int = dao.countPending()
}
