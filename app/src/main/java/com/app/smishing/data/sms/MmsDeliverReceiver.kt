package com.app.smishing.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Recibe los MMS entrantes. Android exige declararlo para ofrecer el rol de SMS (#2); todavía no hace nada. */
class MmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}
