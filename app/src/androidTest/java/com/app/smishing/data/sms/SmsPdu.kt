package com.app.smishing.data.sms

import java.io.ByteArrayOutputStream

/**
 * Arma el PDU de un SMS-DELIVER (3GPP TS 23.040), para entregarlo al receptor tal como lo haría el sistema. Si el texto solo trae
 * letras, números, espacio y `.,:-/` va en el alfabeto GSM de 7 bits (donde valen lo mismo que en ASCII); con cualquier otro
 * carácter (tildes, signos, saltos de línea, emojis) va en UCS-2, que cabe hasta 70 unidades por mensaje: un texto más largo se
 * parte en varios mensajes con [parts].
 */
object SmsPdu {

    /** 2026-11-08 12:00:00 UTC: la hora de envío que el operador pondría. */
    const val SENT_AT_UTC_MILLIS = 1_794_139_200_000L

    fun deliver(senderDigits: String, text: String, secondsAfterSentAt: Int = 0): ByteArray {
        require(senderDigits.all(Char::isDigit))
        val gsm7 = text.all { it.isLetterOrDigit() && it.code < 128 || it in " .,:-/" }
        require(gsm7 || text.length <= UCS2_MAX_UNITS) { "Un texto en UCS-2 admite ${UCS2_MAX_UNITS} unidades: usar parts()" }
        val out = ByteArrayOutputStream()
        out.write(0x00) // sin centro de mensajes en el PDU
        out.write(0x04) // SMS-DELIVER, sin más mensajes por venir
        out.write(senderDigits.length)
        out.write(0x91) // formato internacional
        out.write(semiOctets(senderDigits))
        out.write(0x00) // PID
        out.write(if (gsm7) 0x00 else 0x08) // DCS: alfabeto GSM de 7 bits, o UCS-2
        out.write(timestamp(secondsAfterSentAt))
        if (gsm7) {
            out.write(text.length)
            out.write(packSeptets(text))
        } else {
            val bytes = text.toByteArray(Charsets.UTF_16BE)
            out.write(bytes.size)
            out.write(bytes)
        }
        return out.toByteArray()
    }

    /** Parte un texto en trozos que caben en un mensaje, sin cortar un par suplente (un emoji) por la mitad. */
    fun parts(text: String): List<String> {
        val result = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var end = minOf(start + UCS2_MAX_UNITS, text.length)
            if (end < text.length && Character.isHighSurrogate(text[end - 1])) end--
            result += text.substring(start, end)
            start = end
        }
        return result
    }

    private const val UCS2_MAX_UNITS = 60

    private fun semiOctets(digits: String): ByteArray {
        val padded = if (digits.length % 2 == 1) digits + "F" else digits
        return ByteArray(padded.length / 2) { i ->
            ((Character.digit(padded[2 * i + 1], 16) shl 4) or Character.digit(padded[2 * i], 16)).toByte()
        }
    }

    // 26-11-08 12:00:SS, huso 00. Cada campo son dos dígitos con los nibbles intercambiados.
    private fun timestamp(seconds: Int): ByteArray =
        semiOctets("261108" + "1200" + "%02d".format(seconds) + "00")

    private fun packSeptets(text: String): ByteArray {
        val out = ByteArray((text.length * 7 + 7) / 8)
        var bit = 0
        for (c in text) {
            for (i in 0..6) {
                if ((c.code shr i) and 1 == 1) out[bit / 8] = (out[bit / 8].toInt() or (1 shl (bit % 8))).toByte()
                bit++
            }
        }
        return out
    }
}
