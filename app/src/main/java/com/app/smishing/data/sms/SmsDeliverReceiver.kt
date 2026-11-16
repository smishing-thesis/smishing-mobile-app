package com.app.smishing.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.app.smishing.data.alerts.AndroidMessageNotifier
import com.app.smishing.data.alerts.AndroidRiskAlertNotifier
import com.app.smishing.data.analysis.AnalysisPipeline
import com.app.smishing.data.sharing.ContributionPipeline
import com.app.smishing.data.settings.DataStoreMitigationSettings
import com.app.smishing.domain.alerts.AnalyzeReceivedMessage
import com.app.smishing.domain.sms.IncomingSms
import com.app.smishing.domain.sms.ReceiveIncomingSms
import com.app.smishing.domain.sms.ReceiveOutcome
import com.app.smishing.domain.sms.SmsPart
import java.util.concurrent.Executors

/**
 * Recibe los SMS entrantes cuando la app es la predeterminada (#4), los guarda una sola vez en el buzón del sistema, guarda su
 * análisis en el historial (#12) y, si el mensaje es riesgoso, avisa (#10).
 *
 * Android entrega `SMS_DELIVER` solo a la app predeterminada y solo si tiene `RECEIVE_SMS`. Quien guarda el mensaje
 * es esta app: si no lo guardara, se perdería. Guardarlo va primero y no depende del análisis: si el modelo falla, el
 * mensaje ya está a salvo.
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
                val result = ReceiveIncomingSms(TelephonySmsInbox(appContext)).receive(parts)
                Log.i(TAG, "SMS entrante: ${result.outcome} (${parts.size} fragmentos)")
                val message = result.message
                val messageId = result.messageId
                if (result.outcome == ReceiveOutcome.Stored && message != null && messageId != null) {
                    analyzeIncoming(appContext, messageId, message)
                }
            } catch (e: RuntimeException) {
                // Solo el tipo de error: su mensaje podría traer el contenido.
                Log.e(TAG, "No se pudo guardar el SMS entrante: ${e.javaClass.simpleName}")
            } finally {
                pending?.finish()
            }
        }
    }

    private companion object {
        val EXECUTOR = Executors.newSingleThreadExecutor()
    }
}

private const val TAG = "SmsDeliverReceiver"

/**
 * Evalúa un mensaje ya guardado en el buzón y avisa o actúa según el nivel y el modo de mitigación. Un fallo del análisis no debe
 * tocar el mensaje guardado. Lo usa el receptor y, en la variante de depuración, el cargador de demostración (#65), para que un
 * mensaje de demo pase por el mismo flujo que uno real.
 */
internal fun analyzeIncoming(context: Context, messageId: Long, message: IncomingSms) {
    try {
        val outcome = AnalyzeReceivedMessage(
            assessor = AnalysisPipeline.assessor(context),
            history = ContributionPipeline.history(context),
            notifier = AndroidRiskAlertNotifier(context),
            messageNotifier = AndroidMessageNotifier(context),
            settings = DataStoreMitigationSettings(context),
            mitigator = AnalysisPipeline.mitigator(context),
            // Solo el tipo de error: su mensaje podría traer el contenido.
            onHistoryError = { Log.e(TAG, "No se pudo guardar el análisis en el historial: ${it.javaClass.simpleName}") },
            onMitigationError = { Log.e(TAG, "No se pudo aplicar el modo de mitigación: ${it.javaClass.simpleName}") },
        ).onReceived(messageId, message)
        Log.i(TAG, "Análisis del SMS entrante: $outcome")
    } catch (e: Throwable) {
        // Solo el tipo de error. También se atrapan los `Error` (por ejemplo, la biblioteca nativa del modelo).
        Log.e(TAG, "No se pudo analizar el SMS entrante: ${e.javaClass.simpleName}")
    }
}
