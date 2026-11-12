package com.app.smishing.data.alerts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.app.smishing.R
import com.app.smishing.domain.alerts.IncomingMessageNotifier
import com.app.smishing.domain.sms.IncomingSms
import com.app.smishing.ui.MainActivity

/**
 * Avisa de un SMS recibido que no es de riesgo alto (#49): una notificación por mensaje, en su propio canal "Mensajes", con el
 * remitente y el inicio del texto. **En la pantalla de bloqueo solo dice que hay un mensaje nuevo**, sin remitente ni texto. Tocarla
 * abre la conversación del mensaje.
 *
 * Sin el permiso de notificaciones (Android 13 o superior) o con ellas desactivadas, no se muestra y solo se anota. No se registra el
 * remitente ni el texto.
 */
class AndroidMessageNotifier(private val context: Context) : IncomingMessageNotifier {

    override fun notify(messageId: Long, message: IncomingSms) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            Log.i(TAG, "Aviso de mensaje no mostrado: las notificaciones están desactivadas")
            return
        }
        createChannel()
        val open = PendingIntent.getActivity(
            context,
            messageId.toInt(),
            MainActivity.conversationIntent(context, messageId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.message_notification_public))
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(message.sender)
            .setContentText(message.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.body))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(TAG_NOTIFICATION, messageId.toInt(), notification)
        } catch (e: SecurityException) {
            Log.i(TAG, "Aviso de mensaje no mostrado: falta el permiso de notificaciones")
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.message_channel_name), NotificationManager.IMPORTANCE_HIGH)
            .apply { description = context.getString(R.string.message_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "messages"
        private const val TAG = "MessageNotifier"

        /** La etiqueta de estas notificaciones: así no chocan con las alertas de riesgo, que usan solo el id del mensaje. */
        const val TAG_NOTIFICATION = "message"

        /**
         * Descarta las notificaciones de los mensajes leídos (#50): el aviso del mensaje y, si era de riesgo alto, su alerta de
         * riesgo. Lo que no exista no hace nada.
         */
        fun dismiss(context: Context, messageIds: List<Long>) {
            val manager = NotificationManagerCompat.from(context)
            for (id in messageIds) {
                manager.cancel(TAG_NOTIFICATION, id.toInt())
                manager.cancel(id.toInt())
            }
        }
    }
}
