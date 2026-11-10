package com.app.smishing.domain.alerts

import com.app.smishing.domain.sms.IncomingSms

/**
 * Avisa de un SMS recibido que **no** es de riesgo alto (#49), para que la persona se entere sin abrir la app. El de riesgo alto no
 * pasa por aquí: sigue mostrando solo la alerta de riesgo (#10).
 */
fun interface IncomingMessageNotifier {
    fun notify(messageId: Long, message: IncomingSms)
}
