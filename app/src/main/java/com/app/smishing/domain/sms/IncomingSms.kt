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

    /** Lo guarda; el sistema lo asigna a la conversación de su remitente. */
    fun store(message: IncomingSms)
}

enum class ReceiveOutcome { Stored, Duplicate, Ignored }

/** Registra un SMS entrante: lo guarda una sola vez en el buzón. */
class ReceiveIncomingSms(private val inbox: SmsInbox) {

    fun receive(parts: List<SmsPart>): ReceiveOutcome {
        val message = assembleIncoming(parts) ?: return ReceiveOutcome.Ignored
        if (inbox.contains(message)) return ReceiveOutcome.Duplicate
        inbox.store(message)
        return ReceiveOutcome.Stored
    }
}
