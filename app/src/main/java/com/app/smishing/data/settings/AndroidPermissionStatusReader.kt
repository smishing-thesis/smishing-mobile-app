package com.app.smishing.data.settings

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.app.smishing.data.sms.AndroidSmsPermissionRepository
import com.app.smishing.data.sms.AndroidSmsRoleRepository
import com.app.smishing.domain.settings.AppPermission
import com.app.smishing.domain.settings.PermissionStatus
import com.app.smishing.domain.settings.PermissionStatusReader
import com.app.smishing.domain.sms.SmsPermissionState
import com.app.smishing.domain.sms.SmsRoleState

/**
 * El estado actual de los permisos y del rol de SMS (#25), leído del sistema cada vez que se llama.
 *
 * - **Notificaciones** vale lo que dice Android: desde Android 13 es el permiso, y antes de eso, que la persona no las haya
 *   desactivado en los ajustes de la app.
 * - Leer y recibir SMS cuentan como uno solo: la app necesita los dos.
 */
class AndroidPermissionStatusReader(private val context: Context) : PermissionStatusReader {

    private val roles = AndroidSmsRoleRepository(context)
    private val smsPermissions = AndroidSmsPermissionRepository(context)

    override fun statuses(): List<PermissionStatus> = listOf(
        PermissionStatus(AppPermission.DefaultSmsApp, roles.state() == SmsRoleState.Held),
        PermissionStatus(AppPermission.ReadSms, smsPermissions.state() == SmsPermissionState.Granted),
        PermissionStatus(
            AppPermission.SendSms,
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED,
        ),
        PermissionStatus(AppPermission.Notifications, NotificationManagerCompat.from(context).areNotificationsEnabled()),
        PermissionStatus(
            AppPermission.Contacts,
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED,
        ),
    )
}
