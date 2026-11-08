package com.app.smishing.data.sms

import android.app.Service
import android.content.Intent
import android.os.IBinder

/** Atiende "responder por mensaje" al rechazar una llamada. Android exige declararlo para el rol de SMS (#2); no hace nada. */
class RespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
