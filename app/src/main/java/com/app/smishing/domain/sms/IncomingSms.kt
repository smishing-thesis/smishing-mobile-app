package com.app.smishing.domain.sms

/** Un SMS entrante completo: remitente, texto y la hora en que el operador lo envió. */
data class IncomingSms(
    val sender: String,
    val body: String,
    val sentAtMillis: Long,
) {
    // El contenido es del usuario: no debe salir en logs ni en mensajes de error, ni siquiera por accidente.
    override fun toString() = "IncomingSms(sender=<oculto>, body=<oculto, ${body.length} caracteres>, sentAtMillis=$sentAtMillis)"
}

/** Un fragmento de SMS tal como lo entrega el sistema. Un SMS largo llega en varios. */
data class SmsPart(val sender: String?, val body: String?, val sentAtMillis: Long)

/**
 * Une los fragmentos de una misma entrega en un solo mensaje, en el orden en que llegaron.
 * Devuelve `null` si no hay remitente o si el texto queda vacío: no hay nada que guardar.
 */
fun assembleIncoming(parts: List<SmsPart>): IncomingSms? {
    val sender = parts.firstNotNullOfOrNull { it.sender?.takeIf(String::isNotBlank) } ?: return null
    val body = parts.joinToString(separator = "") { it.body.orEmpty() }
    if (body.isEmpty()) return null
    return IncomingSms(sender = sender, body = body, sentAtMillis = parts.first().sentAtMillis)
}

/** El buzón de SMS del teléfono, donde viven las conversaciones. */
interface SmsInbox {
    /** `true` si el mensaje ya está en el buzón. */
    fun contains(message: IncomingSms): Boolean

    /** Lo guarda y devuelve su id en el buzón; el sistema lo asigna a la conversación de su remitente. */
    fun store(message: IncomingSms): Long
}

enum class ReceiveOutcome { Stored, Duplicate, Ignored }

/**
 * Lo que pasó al recibir un SMS. Solo si se guardó ([ReceiveOutcome.Stored]) trae su [messageId] en el buzón y el [message]:
 * es lo que se necesita para analizarlo y avisar.
 */
data class ReceiveResult(val outcome: ReceiveOutcome, val messageId: Long? = null, val message: IncomingSms? = null)

/** Registra un SMS entrante: lo guarda una sola vez en el buzón. */
class ReceiveIncomingSms(private val inbox: SmsInbox) {

    fun receive(parts: List<SmsPart>): ReceiveResult {
        val message = assembleIncoming(parts) ?: return ReceiveResult(ReceiveOutcome.Ignored)
        if (inbox.contains(message)) return ReceiveResult(ReceiveOutcome.Duplicate)
        val id = inbox.store(message)
        return ReceiveResult(ReceiveOutcome.Stored, id, message)
    }
}
