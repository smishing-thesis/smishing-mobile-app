package com.app.smishing.data.messaging

import android.content.Context
import android.provider.Telephony
import com.app.smishing.domain.messaging.Conversation
import com.app.smishing.domain.messaging.ConversationMessage
import com.app.smishing.domain.messaging.ConversationRepository
import com.app.smishing.domain.messaging.MessageDirection
import com.app.smishing.domain.messaging.SmsRow
import com.app.smishing.domain.messaging.StoredMessage
import com.app.smishing.domain.messaging.latestPerConversation

/**
 * Lee las conversaciones del buzón del sistema (`Telephony.Sms`), que necesita `READ_SMS`. Los borradores no cuentan:
 * no son mensajes enviados ni recibidos. Solo SMS: los MMS no se leen (ver docs/conversaciones.md).
 */
class TelephonyConversationRepository(context: Context) : ConversationRepository {

    private val resolver = context.contentResolver

    override fun conversations(): List<Conversation> =
        resolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            NOT_A_DRAFT,
            null,
            "${Telephony.Sms.DATE} DESC",
        )?.use { cursor ->
            latestPerConversation(
                generateSequence {
                    if (!cursor.moveToNext()) null
                    else SmsRow(cursor.getLong(0), cursor.getString(1).orEmpty(), cursor.getString(2).orEmpty(), cursor.getLong(3))
                },
            )
        }.orEmpty()

    override fun messages(threadId: Long): List<ConversationMessage> =
        resolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms._ID, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE),
            "${Telephony.Sms.THREAD_ID} = ? AND $NOT_A_DRAFT",
            arrayOf(threadId.toString()),
            "${Telephony.Sms.DATE} ASC, ${Telephony.Sms._ID} ASC",
        )?.use { cursor ->
            generateSequence {
                if (!cursor.moveToNext()) null
                else ConversationMessage(
                    id = cursor.getLong(0),
                    body = cursor.getString(1).orEmpty(),
                    atMillis = cursor.getLong(2),
                    // Recibido es solo el tipo "buzón de entrada"; enviado, en cola, fallido o saliente son del usuario.
                    direction = if (cursor.getInt(3) == Telephony.Sms.MESSAGE_TYPE_INBOX) MessageDirection.Incoming else MessageDirection.Outgoing,
                    failed = cursor.getInt(3) == Telephony.Sms.MESSAGE_TYPE_FAILED,
                )
            }.toList()
        }.orEmpty()

    override fun message(messageId: Long): StoredMessage? =
        resolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE),
            "${Telephony.Sms._ID} = ?",
            arrayOf(messageId.toString()),
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) null
            else StoredMessage(
                id = cursor.getLong(0),
                threadId = cursor.getLong(1),
                address = cursor.getString(2).orEmpty(),
                body = cursor.getString(3).orEmpty(),
                atMillis = cursor.getLong(4),
                direction = if (cursor.getInt(5) == Telephony.Sms.MESSAGE_TYPE_INBOX) MessageDirection.Incoming else MessageDirection.Outgoing,
            )
        }

    private companion object {
        const val NOT_A_DRAFT = "${Telephony.Sms.TYPE} != ${Telephony.Sms.MESSAGE_TYPE_DRAFT}"
    }
}
