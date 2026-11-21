package com.app.smishing.domain.mitigation

import com.app.smishing.domain.history.AnalysisHistory
import com.app.smishing.domain.history.AnalysisRecord

/** Lo que la app puede hacer sobre un mensaje de riesgo alto (#14, #15). */
enum class ActionType(val id: String) {
    /** Bloquear al remitente en el sistema. */
    BlockSender("block"),

    /** Retirar el mensaje del buzón, conservando una copia local para poder restaurarlo. */
    RemoveMessage("remove");

    companion object {
        fun fromId(id: String): ActionType = entries.first { it.id == id }
    }
}

/**
 * En qué quedó una acción (#28). Una acción que falló **nunca** figura como completada.
 *
 * - [Requested]: se pidió y todavía no se sabe el resultado.
 * - [Completed]: se hizo.
 * - [Failed]: no se pudo; la causa está en [ActionNote].
 * - [Reverted]: se hizo y la persona la deshizo (#16).
 */
enum class ActionStatus(val id: String) {
    Requested("requested"),
    Completed("completed"),
    Failed("failed"),
    Reverted("reverted");

    companion object {
        fun fromId(id: String): ActionStatus = entries.first { it.id == id }
    }
}

/** El motivo que acompaña a un estado. Son datos, no texto: la pantalla los explica con sus propias palabras. */
enum class ActionNote(val id: String) {
    /** El sistema no ofrece la capacidad (por ejemplo, bloquear sin ser la app de mensajería principal). */
    NotSupported("not_supported"),

    /** El sistema negó el permiso. */
    NotAllowed("not_allowed"),

    /** No hay copia local suficiente del mensaje para explicar y revertir el retiro: no se retira. */
    NoLocalCopy("no_local_copy"),

    /** Falló al ejecutarse. */
    Error("error"),

    /** Ya estaba hecho antes (el número ya estaba bloqueado): la app no lo tocó, así que tampoco lo deshace. */
    AlreadyApplied("already_applied"),

    /** Se intentó deshacer y no se pudo: la acción sigue vigente. */
    RevertFailed("revert_failed");

    companion object {
        fun fromId(id: String): ActionNote = entries.first { it.id == id }
    }
}

data class MitigationAction(
    val id: Long,
    /** El id del registro del historial sobre el que se actuó. */
    val recordId: Long,
    val type: ActionType,
    val status: ActionStatus,
    val note: ActionNote?,
    val requestedAtMillis: Long,
    val updatedAtMillis: Long,
)

/** La acción más reciente de cada tipo: si se reintentó, solo cuenta el último intento. */
fun List<MitigationAction>.latest(): Map<ActionType, MitigationAction> =
    groupBy { it.type }.mapValues { (_, list) -> list.maxBy { it.id } }

/** Las acciones tomadas, guardadas en el teléfono (#28). */
interface MitigationLog {
    /** Anota que se pidió una acción y devuelve su id. */
    fun request(recordId: Long, type: ActionType, nowMillis: Long): Long

    fun update(actionId: Long, status: ActionStatus, note: ActionNote?, nowMillis: Long)

    /** Las acciones de un registro, de la más antigua a la más reciente. */
    fun actionsOf(recordId: Long): List<MitigationAction>

    /** Todas las acciones, agrupadas por registro. */
    fun all(): Map<Long, List<MitigationAction>>

    /** Avisa cuando algo cambia. Devuelve algo que se cierra para dejar de avisar. */
    fun onChange(listener: () -> Unit): AutoCloseable = AutoCloseable {}
}

/** Bloquear remitentes en el sistema (#14). */
interface SenderBlocker {
    /** Si el sistema deja a esta app bloquear números ahora. */
    fun isAvailable(): Boolean

    fun isBlocked(sender: String): Boolean

    fun block(sender: String): Boolean

    fun unblock(sender: String): Boolean
}

/** Retirar y devolver mensajes del buzón del sistema (#15, #16). */
interface InboxMessages {
    /** Borra el mensaje del buzón. `true` si lo había y se borró. */
    fun remove(messageId: Long): Boolean

    /** Vuelve a guardar el mensaje en el buzón, ya leído y con su hora original. Devuelve su id nuevo, o `null` si no se pudo. */
    fun restore(sender: String, body: String, receivedAtMillis: Long): Long?
}

/** Los tipos de acción que no se pudieron deshacer al marcar un mensaje como seguro. */
data class RevertResult(val notReverted: Set<ActionType>) {
    val complete: Boolean get() = notReverted.isEmpty()
}

/**
 * Hace, anota y deshace las acciones sobre un mensaje (#14, #15, #16, #28).
 *
 * - **Lo automático solo si se puede leer la configuración:** si no se puede leer el modo no se hace nada y no se anota nada.
 *   **Lo manual** ([performManual], cuando la persona toca el botón en el detalle, #29) **no consulta el modo**: es su decisión explícita.
 * - **Solo si hay registro local:** sin el registro guardado no se actúa, porque no habría de dónde explicar ni revertir.
 * - **Lo que falla se anota como fallido**, con su causa, y nunca como hecho. Ninguna operación lanza: el resultado se lee en
 *   el estado.
 * - El retiro del buzón comprueba que el historial guarda el mensaje completo antes de borrarlo.
 */
class MitigationExecutor(
    private val log: MitigationLog,
    private val history: AnalysisHistory,
    private val settings: MitigationSettings,
    private val blocker: SenderBlocker,
    private val inbox: InboxMessages,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** Lo que hace el modo automático con un mensaje de riesgo alto: bloquear al remitente y retirar el mensaje. */
    fun act(record: AnalysisRecord) {
        perform(record, ActionType.BlockSender)
        perform(record, ActionType.RemoveMessage)
    }

    /**
     * Hace la acción **automática** y devuelve en qué quedó, o `null` si no se hizo porque no se pudo leer la configuración o no hay
     * registro local. Si ya estaba completada no la repite.
     */
    fun perform(record: AnalysisRecord, type: ActionType): ActionStatus? = if (allowed()) run(record, type) else null

    /**
     * Hace la acción porque **la persona la pidió** (#29): vale con cualquier modo, para cualquier mensaje analizado. Queda anotada y
     * explica sus fallos igual que la automática. Devuelve `null` solo si no hay registro local.
     */
    fun performManual(record: AnalysisRecord, type: ActionType): ActionStatus? = run(record, type)

    private fun run(record: AnalysisRecord, type: ActionType): ActionStatus? {
        if (record.id == 0L) return null
        val current = log.actionsOf(record.id).latest()[type]
        if (current?.status == ActionStatus.Completed) return ActionStatus.Completed

        val actionId = log.request(record.id, type, clock())
        val (status, note) = try {
            execute(record, type)
        } catch (e: SecurityException) {
            ActionStatus.Failed to ActionNote.NotAllowed
        } catch (e: Exception) {
            ActionStatus.Failed to ActionNote.Error
        }
        log.update(actionId, status, note, clock())
        return status
    }

    private fun execute(record: AnalysisRecord, type: ActionType): Pair<ActionStatus, ActionNote?> = when (type) {
        ActionType.BlockSender -> when {
            !blocker.isAvailable() -> ActionStatus.Failed to ActionNote.NotSupported
            blocker.isBlocked(record.sender) -> ActionStatus.Completed to ActionNote.AlreadyApplied
            blocker.block(record.sender) -> ActionStatus.Completed to null
            else -> ActionStatus.Failed to ActionNote.Error
        }
        ActionType.RemoveMessage -> when {
            // La copia local debe estar completa antes de borrar nada.
            history.byId(record.id)?.let { it.body == record.body && it.sender == record.sender } != true ->
                ActionStatus.Failed to ActionNote.NoLocalCopy
            inbox.remove(record.messageId) -> ActionStatus.Completed to null
            else -> ActionStatus.Failed to ActionNote.Error
        }
    }

    /**
     * La persona dice que el mensaje es seguro (#16): se guarda su decisión y se deshace cada acción completada (desbloquear,
     * restaurar el mensaje a su bandeja con su hora original). Lo que no se pueda deshacer queda vigente, anotado como tal, y se
     * informa en el resultado.
     */
    fun markSafe(record: AnalysisRecord): RevertResult {
        if (record.id == 0L) return RevertResult(emptySet())
        history.markSafe(record.id, clock())
        val failed = mutableSetOf<ActionType>()
        for ((type, action) in log.actionsOf(record.id).latest()) {
            if (action.status != ActionStatus.Completed) continue
            val reverted = try {
                revert(record, action)
            } catch (e: Exception) {
                false
            }
            if (reverted) log.update(action.id, ActionStatus.Reverted, action.note, clock())
            else {
                log.update(action.id, ActionStatus.Completed, ActionNote.RevertFailed, clock())
                failed += type
            }
        }
        return RevertResult(failed)
    }

    private fun revert(record: AnalysisRecord, action: MitigationAction): Boolean = when (action.type) {
        // Si el número ya estaba bloqueado antes, no lo puso la app y no lo quita.
        ActionType.BlockSender -> action.note == ActionNote.AlreadyApplied || blocker.unblock(record.sender)
        ActionType.RemoveMessage -> inbox.restore(record.sender, record.body, record.receivedAtMillis)?.also {
            history.updateMessageId(record.id, it)
        } != null
    }

    // Solo se actúa si se puede leer el modo: lo que no se pudo comprobar no se da por permitido.
    private fun allowed(): Boolean = try {
        settings.mode()
        true
    } catch (e: Exception) {
        false
    }
}

/** El ejecutor del modo automático (#13): con la decisión de actuar, bloquea y retira; con cualquier otra no hace nada. */
class ApplyMitigation(private val executor: MitigationExecutor) : Mitigator {
    override fun apply(decision: MitigationDecision, record: AnalysisRecord) {
        if (decision == MitigationDecision.Act) executor.act(record)
    }
}
