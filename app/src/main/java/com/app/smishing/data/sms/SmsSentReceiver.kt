package com.app.smishing.data.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import java.util.concurrent.Executors

/**
 * Recibe el resultado de enviar un SMS (lo pide [AndroidSmsSender]) y actualiza el mensaje en el buzón: de saliente a
 * enviado si la radio lo aceptó, o a fallido si no. La actualización es condicional para que el resultado de una parte de
 * un mensaje largo no pise el de otra: lo fallido no vuelve a enviado.
 *
 * No se registra el texto ni el destinatario, solo si salió bien o el código de error.
 */
class SmsSentReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SENT) return
        val row = intent.data ?: return
        val code = resultCode
        val appContext = context.applicationContext
        // Fuera de una entrega real del sistema (en las pruebas) no hay resultado pendiente: `goAsync()` devuelve null.
        val pending: PendingResult? = goAsync()
        EXECUTOR.execute {
            try {
                val resolver = appContext.contentResolver
                if (code == Activity.RESULT_OK) {
                    val values = ContentValues().apply { put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT) }
                    resolver.update(row, values, "${Telephony.Sms.TYPE} = ?", arrayOf(Telephony.Sms.MESSAGE_TYPE_OUTBOX.toString()))
                    Log.i(TAG, "Envío aceptado por la radio")
                } else {
                    val values = ContentValues().apply { put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_FAILED) }
                    resolver.update(row, values, "${Telephony.Sms.TYPE} != ?", arrayOf(Telephony.Sms.MESSAGE_TYPE_FAILED.toString()))
                    Log.i(TAG, "Envío fallido, código $code")
                }
            } catch (e: RuntimeException) {
                Log.e(TAG, "No se pudo actualizar el estado del envío: ${e.javaClass.simpleName}")
            } finally {
                pending?.finish()
            }
        }
    }

    companion object {
        const val ACTION_SENT = "com.app.smishing.action.SMS_SENT"
        private const val TAG = "SmsSentReceiver"
        private val EXECUTOR = Executors.newSingleThreadExecutor()
    }
}
