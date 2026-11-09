package com.app.smishing.data.sms

import java.io.ByteArrayOutputStream

/**
 * Arma el PDU de un SMS-DELIVER (3GPP TS 23.040) con texto en el alfabeto GSM de 7 bits, para entregarlo al receptor
 * tal como lo haría el sistema. Solo admite letras, números, espacio y `.,:-/`, que en GSM 7 valen lo mismo que en ASCII.
 */
object SmsPdu {

    /** 2026-11-08 12:00:00 UTC: la hora de envío que el operador pondría. */
    const val SENT_AT_UTC_MILLIS = 1_794_139_200_000L

    fun deliver(senderDigits: String, text: String, secondsAfterSentAt: Int = 0): ByteArray {
        require(senderDigits.all(Char::isDigit))
        require(text.all { it.isLetterOrDigit() && it.code < 128 || it in " .,:-/" }) { "Carácter fuera del subconjunto de la prueba" }
        val out = ByteArrayOutputStream()
        out.write(0x00) // sin centro de mensajes en el PDU
        out.write(0x04) // SMS-DELIVER, sin más mensajes por venir
        out.write(senderDigits.length)
        out.write(0x91) // formato internacional
        out.write(semiOctets(senderDigits))
        out.write(0x00) // PID
        out.write(0x00) // DCS: alfabeto GSM de 7 bits
        out.write(timestamp(secondsAfterSentAt))
        out.write(text.length)
        out.write(packSeptets(text))
        return out.toByteArray()
    }

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
