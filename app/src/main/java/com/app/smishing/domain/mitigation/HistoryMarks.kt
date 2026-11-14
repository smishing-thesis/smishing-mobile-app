package com.app.smishing.domain.mitigation

import com.app.smishing.domain.history.AnalysisRecord

/** Lo que el historial dice de las acciones sobre un mensaje (#28). */
enum class HistoryMark { MarkedSafe, Blocked, Removed, ActionFailed, ActionPending }

/**
 * Las marcas de un mensaje en la lista del historial, según el **estado vigente** de cada acción.
 *
 * - Si la persona lo marcó como seguro (#16), solo eso: lo demás ya se deshizo.
 * - Una acción **fallida** da `ActionFailed` y nunca `Blocked` ni `Removed`.
 * - Una acción pedida cuyo resultado aún no se sabe da `ActionPending`.
 * - Una acción revertida no deja marca.
 */
/**
 * Cuántos de los [records] son de un mensaje que la app **retiró del buzón** y que todavía se puede restaurar (#16, #52). Su registro es
 * la única copia del texto: borrarlo impide restaurarlo, y la confirmación de borrar debe decirlo.
 */
fun restorableCount(records: List<AnalysisRecord>, actions: Map<Long, List<MitigationAction>>): Int =
    records.count { HistoryMark.Removed in historyMarks(it, actions[it.id].orEmpty()) }

fun historyMarks(record: AnalysisRecord, actions: List<MitigationAction>): List<HistoryMark> {
    if (record.markedSafeAtMillis != null) return listOf(HistoryMark.MarkedSafe)
    val latest = actions.latest()
    return buildList {
        if (latest[ActionType.BlockSender]?.status == ActionStatus.Completed) add(HistoryMark.Blocked)
        if (latest[ActionType.RemoveMessage]?.status == ActionStatus.Completed) add(HistoryMark.Removed)
        if (latest.values.any { it.status == ActionStatus.Failed || it.note == ActionNote.RevertFailed }) add(HistoryMark.ActionFailed)
        if (latest.values.any { it.status == ActionStatus.Requested }) add(HistoryMark.ActionPending)
    }
}
