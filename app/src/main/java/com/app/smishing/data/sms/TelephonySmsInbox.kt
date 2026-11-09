package com.app.smishing.data.sms

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.Telephony
import com.app.smishing.domain.sms.IncomingSms
import com.app.smishing.domain.sms.SmsInbox

/**
 * El buzón del sistema (`Telephony.Sms.Inbox`). Solo la app de SMS predeterminada puede escribir en él.
 *
 * El sistema asigna cada mensaje a la conversación de su remitente (`thread_id`) al insertarlo; la app no
 * crea conversaciones por su cuenta.
 */
class TelephonySmsInbox(context: Context) : SmsInbox {

    private val resolver = context.contentResolver

    override fun contains(message: IncomingSms): Boolean =
        resolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms._ID),
            "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.DATE_SENT} = ? AND ${Telephony.Sms.BODY} = ?",
            arrayOf(message.sender, message.sentAtMillis.toString(), message.body),
            null,
        )?.use { it.moveToFirst() } ?: false

    override fun store(message: IncomingSms): Long {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, message.sender)
            put(Telephony.Sms.BODY, message.body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.DATE_SENT, message.sentAtMillis)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
        }
        val row = checkNotNull(resolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)) { "El buzón no guardó el mensaje" }
        return ContentUris.parseId(row)
    }
}
