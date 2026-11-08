package com.app.smishing.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Recibe los SMS entrantes cuando la app es la predeterminada. Android exige declararlo para ofrecer el rol de SMS (#2).
 *
 * ATENCIÓN: todavía no hace nada. Una app predeterminada que no guarda el mensaje lo pierde, así que mientras #4 no
 * esté hecho esta app solo debe usarse como gestor de SMS en un emulador, no en un teléfono con mensajes reales.
 */
class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}
