package com.app.smishing.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.app.smishing.domain.sms.ReceiveIncomingSms
import com.app.smishing.domain.sms.SmsPart
import java.util.concurrent.Executors

/**
 * Recibe los SMS entrantes cuando la app es la predeterminada (#4) y los guarda una sola vez en el buzón del sistema.
 *
 * Android entrega `SMS_DELIVER` solo a la app predeterminada y solo si tiene `RECEIVE_SMS`. Quien guarda el mensaje
 * es esta app: si no lo guardara, se perdería. El análisis no se dispara aquí todavía: no hay dónde guardar su
 * resultado (ver docs/recepcion-sms.md).
 *
 * No se registra el contenido del mensaje, ni el remitente.
 */
class SmsDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent).orEmpty().map {
            SmsPart(sender = it.originatingAddress, body = it.messageBody, sentAtMillis = it.timestampMillis)
        }
        val appContext = context.applicationContext
        // Escribir en el buzón es una operación de disco: se hace fuera del hilo principal, avisando al sistema.
        // Fuera de una entrega real del sistema (en las pruebas) no hay resultado pendiente: `goAsync()` devuelve null.
        val pending: PendingResult? = goAsync()
        EXECUTOR.execute {
            try {
                val outcome = ReceiveIncomingSms(TelephonySmsInbox(appContext)).receive(parts)
                Log.i(TAG, "SMS entrante: $outcome (${parts.size} fragmentos)")
            } catch (e: RuntimeException) {
                // Solo el tipo de error: su mensaje podría traer el contenido.
                Log.e(TAG, "No se pudo guardar el SMS entrante: ${e.javaClass.simpleName}")
            } finally {
                pending?.finish()
            }
        }
    }

    private companion object {
        const val TAG = "SmsDeliverReceiver"
        val EXECUTOR = Executors.newSingleThreadExecutor()
    }
}
