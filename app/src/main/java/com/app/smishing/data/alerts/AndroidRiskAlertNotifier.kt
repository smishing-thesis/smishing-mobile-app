package com.app.smishing.data.alerts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.app.smishing.R
import com.app.smishing.domain.alerts.AlertReason
import com.app.smishing.domain.alerts.RiskAlert
import com.app.smishing.domain.alerts.RiskAlertNotifier
import com.app.smishing.ui.MainActivity
import com.app.smishing.ui.designsystem.theme.SmishingPalette
import com.app.smishing.ui.designsystem.theme.Status

/**
 * Muestra la alerta como una notificación (#10): "Posible estafa detectada", con el remitente y el texto del mensaje.
 *
 * - **Lleva el texto del mensaje** (al expandirla se ve completo), como las notificaciones de mensajes (#49). En la pantalla de
 *   bloqueo solo dice que hay una alerta, sin remitente ni texto (versión pública). El motivo se ve en el detalle.
 * - Tocarla abre la **conversación** del mensaje, donde el aviso "Parece una estafa" lleva a "Ver riesgo". "Ver detalles" abre directo el
 *   detalle del análisis. En los dos el mensaje es texto plano: ningún enlace se activa.
 * - Sin el permiso de notificaciones (Android 13 o superior) o con ellas desactivadas, no se muestra y solo se anota.
 *
 * No se registra el remitente, el texto ni el motivo.
 */
class AndroidRiskAlertNotifier(private val context: Context) : RiskAlertNotifier {

    override fun notify(alert: RiskAlert) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            Log.i(TAG, "Alerta no mostrada: las notificaciones están desactivadas")
            return
        }
        createChannel()
        val open = PendingIntent.getActivity(
            context,
            alert.messageId.toInt(),
            MainActivity.conversationIntent(context, alert.messageId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val details = PendingIntent.getActivity(
            context,
            alert.messageId.toInt(),
            MainActivity.detailIntent(context, alert.messageId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = context.getString(R.string.alert_title)
        val summary = "${alert.sender} — ${alert.body}"
        val red = SmishingPalette.statusColors(Status.Danger).onContainer.toArgb()
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.alert_public_text))
            .setColor(red)
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setColor(red)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.alert_view_details), details)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(alert.messageId.toInt(), notification)
        } catch (e: SecurityException) {
            Log.i(TAG, "Alerta no mostrada: falta el permiso de notificaciones")
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.alert_channel_name), NotificationManager.IMPORTANCE_HIGH)
            .apply { description = context.getString(R.string.alert_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "risk_alerts"
        private const val TAG = "RiskAlertNotifier"

        fun reasonText(reason: AlertReason): Int = when (reason) {
            AlertReason.Urgency -> R.string.alert_reason_urgency
            AlertReason.Impersonation -> R.string.alert_reason_impersonation
            AlertReason.DataRequest -> R.string.alert_reason_data_request
        }
    }
}
