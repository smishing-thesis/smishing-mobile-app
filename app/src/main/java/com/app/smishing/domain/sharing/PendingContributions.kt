package com.app.smishing.domain.sharing

import com.app.smishing.domain.history.AnalysisHistory
import com.app.smishing.domain.history.AnalysisRecord
import com.app.smishing.domain.risk.RiskLevel

/** Un mensaje peligroso que espera su turno para compartirse. [body] es del mensaje: no debe escribirse en logs. */
class PendingContribution(val analyzedMessageId: Long, val body: String) {
    override fun toString() = "PendingContribution(analyzedMessageId=$analyzedMessageId)"
}

/**
 * Los aportes pendientes de enviar (#71): los mensajes peligrosos que se detectaron con el aporte activado y todavía no se enviaron, sea
 * por falta de internet o porque aún no hay destino. Solo guarda una referencia al registro del historial: el texto se desidentifica
 * justo antes de enviarlo, y borrar el mensaje del historial lo saca de la cola.
 */
interface PendingContributions {
    /** Pone el registro en la cola (si ya estaba, no cambia nada). Puede bloquear: no se llama desde el hilo principal. */
    fun add(analyzedMessageId: Long, nowMillis: Long)

    /** Hasta [limit] pendientes, los más antiguos primero. */
    fun next(limit: Int): List<PendingContribution>

    fun remove(ids: Collection<Long>)

    /** Vacía la cola: al revocar el consentimiento no queda nada por enviar. */
    fun clear()

    fun count(): Int
}

/** Un texto ya desidentificado, listo para enviarse. [text] es del mensaje: no debe escribirse en logs. */
class Contribution(val text: String, val rulesVersion: String) {
    override fun toString() = "Contribution(rulesVersion=$rulesVersion)"
}

/** Lo que pasó al entregar un lote. */
sealed interface ContributionSendResult {
    /** El destino recibió el lote: se saca de la cola. */
    data object Sent : ContributionSendResult

    /** No se pudo entregar (sin conexión, error del servidor): el lote se conserva y se reintenta. */
    data object Retry : ContributionSendResult

    /** Todavía no hay destino al que enviar (#72): nada sale y todo se conserva. */
    data object NotConfigured : ContributionSendResult
}

/** Entrega un lote de aportes ya desidentificados al destino (el backend, #72). */
fun interface ContributionSender {
    fun send(batch: List<Contribution>): ContributionSendResult
}

/** Cómo terminó una pasada de envío. Solo lleva cuántos se enviaron, nunca texto. */
sealed interface UploadOutcome {
    /** No queda nada pendiente. */
    data class Done(val sent: Int) : UploadOutcome

    /** Quedó algo pendiente por un fallo de entrega: hay que volver a intentar. */
    data class Retry(val sent: Int) : UploadOutcome

    data object NotConfigured : UploadOutcome

    /** La persona revocó el consentimiento: se vació la cola y no se envió más. */
    data object ConsentRevoked : UploadOutcome
}

/**
 * Envía los pendientes **por lotes** de [batchSize] (#71), por si son muchos. Antes de cada texto se vuelve a leer el consentimiento y se
 * desidentifica ([prepare]); un mensaje que no quede anónimo se **descarta** de la cola, no se envía. Si un lote falla, se detiene y lo no
 * enviado queda en la cola. Revocar el consentimiento a mitad vacía la cola.
 */
class SendPendingContributions(
    private val queue: PendingContributions,
    private val prepare: PrepareContribution,
    private val sender: ContributionSender,
    private val batchSize: Int = BATCH_SIZE,
) {

    fun run(): UploadOutcome {
        var sent = 0
        while (true) {
            val items = queue.next(batchSize)
            if (items.isEmpty()) return UploadOutcome.Done(sent)
            val ready = ArrayList<Contribution>()
            val readyIds = ArrayList<Long>()
            val dropped = ArrayList<Long>()
            for (item in items) {
                when (val result = prepare.prepare(item.body)) {
                    is ContributionResult.Ready -> {
                        ready += Contribution(result.text, result.rulesVersion)
                        readyIds += item.analyzedMessageId
                    }
                    is ContributionResult.Rejected -> {
                        if (result.reason == ContributionRejection.NoConsent) {
                            queue.clear()
                            return UploadOutcome.ConsentRevoked
                        }
                        dropped += item.analyzedMessageId
                    }
                }
            }
            if (dropped.isNotEmpty()) queue.remove(dropped)
            if (ready.isEmpty()) continue
            when (sender.send(ready)) {
                ContributionSendResult.Sent -> {
                    queue.remove(readyIds)
                    sent += ready.size
                }
                ContributionSendResult.Retry -> return UploadOutcome.Retry(sent)
                ContributionSendResult.NotConfigured -> return UploadOutcome.NotConfigured
            }
        }
    }

    companion object {
        /** Cuántos aportes lleva cada lote. */
        const val BATCH_SIZE = 20
    }
}

/**
 * El historial que, al guardar un análisis de nivel **alto** con el aporte activado, pone el mensaje en la cola de pendientes y avisa
 * ([onQueued]) para que se programe el envío. Todo lo demás lo hace [delegate]. Si no se puede encolar, el análisis ya guardado no se ve
 * afectado: un fallo del aporte no debe impedir la alerta ni la mitigación.
 */
class QueueingAnalysisHistory(
    private val delegate: AnalysisHistory,
    private val consent: ContributionConsent,
    private val queue: PendingContributions,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onQueued: () -> Unit = {},
) : AnalysisHistory by delegate {

    override fun save(record: AnalysisRecord): Long {
        val id = delegate.save(record)
        if (record.assessment.level == RiskLevel.High) {
            try {
                if (consent.isGranted()) {
                    queue.add(id, clock())
                    onQueued()
                }
            } catch (_: Exception) {
                // Sin registrar el motivo: la excepción podría traer el texto de un mensaje. El análisis ya quedó guardado.
            }
        }
        return id
    }
}
