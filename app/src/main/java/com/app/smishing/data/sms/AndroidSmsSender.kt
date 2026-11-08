package com.app.smishing.data.sms

import android.Manifest
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.app.smishing.domain.messaging.SendOutcome
import com.app.smishing.domain.messaging.SmsSender

/**
 * Envía SMS con `SmsManager` y los refleja en el buzón del sistema. Necesita `SEND_SMS` y ser la app predeterminada:
 * Android no guarda por ella los mensajes que envía.
 *
 * 1. El mensaje se guarda primero como saliente (`Outbox`), así aparece al instante en su conversación; el sistema le
 *    asigna la conversación del destinatario.
 * 2. Se entrega a `SmsManager`. Cuando la radio responde, [SmsSentReceiver] lo pasa a enviado o a fallido.
 *
 * No se registra el texto ni el destinatario.
 */
class AndroidSmsSender(private val context: Context) : SmsSender {

    private val resolver = context.contentResolver

    override fun send(recipient: String, body: String): SendOutcome {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            return SendOutcome.NotAllowed
        }
        val row = try {
            resolver.insert(Telephony.Sms.Outbox.CONTENT_URI, outgoingValues(recipient, body))
        } catch (e: SecurityException) {
            return SendOutcome.NotAllowed // ya no es la app de SMS predeterminada
        } ?: return SendOutcome.Failed

        return try {
            val manager = smsManager()
            val parts = manager.divideMessage(body)
            // Una respuesta por parte: el mensaje pasa a enviado con la primera que salga bien y a fallido si alguna falla.
            val sentIntents = ArrayList<PendingIntent>(parts.map { sentIntent(row) })
            manager.sendMultipartTextMessage(recipient, null, parts, sentIntents, null)
            SendOutcome.Sent(threadIdOf(row))
        } catch (e: SecurityException) {
            markFailed(row)
            SendOutcome.NotAllowed
        } catch (e: RuntimeException) {
            markFailed(row)
            SendOutcome.Failed
        }
    }

    private fun outgoingValues(recipient: String, body: String) = ContentValues().apply {
        put(Telephony.Sms.ADDRESS, recipient)
        put(Telephony.Sms.BODY, body)
        put(Telephony.Sms.DATE, System.currentTimeMillis())
        put(Telephony.Sms.READ, 1)
        put(Telephony.Sms.SEEN, 1)
    }

    private fun threadIdOf(row: Uri): Long =
        resolver.query(row, arrayOf(Telephony.Sms.THREAD_ID), null, null, null)?.use {
            if (it.moveToFirst()) it.getLong(0) else null
        } ?: error("El buzón no asignó conversación al mensaje")

    private fun markFailed(row: Uri) {
        resolver.update(row, ContentValues().apply { put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_FAILED) }, null, null)
    }

    private fun sentIntent(row: Uri): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(SmsSentReceiver.ACTION_SENT).setClass(context, SmsSentReceiver::class.java).setData(row),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun smsManager(): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getSystemService(SmsManager::class.java)
        else @Suppress("DEPRECATION") SmsManager.getDefault()
}
