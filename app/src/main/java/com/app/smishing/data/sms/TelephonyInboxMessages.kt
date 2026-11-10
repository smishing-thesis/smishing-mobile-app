package com.app.smishing.data.sms

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.Telephony
import com.app.smishing.domain.mitigation.InboxMessages

/**
 * Retira y devuelve mensajes del buzón del sistema (#15, #16). Solo la app de SMS predeterminada puede borrar y escribir.
 *
 * Al restaurar, el mensaje vuelve como recibido y **ya leído** (la persona lo vio al marcarlo como seguro) y con su hora original;
 * el sistema le da un id nuevo y lo vuelve a poner en la conversación de su remitente.
 */
class TelephonyInboxMessages(context: Context) : InboxMessages {

    private val resolver = context.contentResolver

    override fun remove(messageId: Long): Boolean =
        resolver.delete(ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, messageId), null, null) > 0

    override fun restore(sender: String, body: String, receivedAtMillis: Long): Long? {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, sender)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, receivedAtMillis)
            put(Telephony.Sms.DATE_SENT, receivedAtMillis)
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
        }
        return resolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)?.let(ContentUris::parseId)
    }
}
