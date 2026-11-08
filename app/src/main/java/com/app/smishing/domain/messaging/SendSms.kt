package com.app.smishing.domain.messaging

/**
 * Deja el número como se envía: un `+` inicial opcional y solo dígitos. Acepta espacios, guiones, puntos y paréntesis al
 * escribirlo ("987 654 321"). Devuelve `null` si no es un número: letras, un `+` fuera del inicio, o menos de 3 o más de 15
 * dígitos (el máximo de un número internacional).
 */
fun normalizeRecipient(input: String): String? {
    val text = input.trim()
    if (text.isEmpty()) return null
    val body = text.removePrefix("+")
    if (!body.all { it.isDigit() || it in " -.()" }) return null
    val digits = body.filter(Char::isDigit)
    if (digits.length !in 3..15) return null
    return (if (text.startsWith("+")) "+" else "") + digits
}

/** Lo que pasó al intentar enviar. */
sealed interface SendOutcome {
    /** El mensaje quedó en la conversación [threadId] y se entregó al sistema para enviarlo. */
    data class Sent(val threadId: Long) : SendOutcome

    data object InvalidRecipient : SendOutcome
    data object EmptyMessage : SendOutcome

    /** Falta el permiso de enviar, o la app ya no es la de SMS predeterminada. */
    data object NotAllowed : SendOutcome

    data object Failed : SendOutcome
}

/** Guarda el mensaje en el buzón y lo entrega al sistema para enviarlo. */
interface SmsSender {
    /** [recipient] ya viene normalizado y [body] no está vacío. */
    fun send(recipient: String, body: String): SendOutcome
}

/** Redactar y enviar un SMS (#6): valida lo que la persona escribió y lo envía. */
class SendSms(private val sender: SmsSender) {

    fun send(recipientInput: String, body: String): SendOutcome {
        val recipient = normalizeRecipient(recipientInput) ?: return SendOutcome.InvalidRecipient
        if (body.isBlank()) return SendOutcome.EmptyMessage
        return sender.send(recipient, body)
    }
}
