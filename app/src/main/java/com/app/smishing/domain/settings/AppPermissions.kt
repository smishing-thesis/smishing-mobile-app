package com.app.smishing.domain.settings

/** Los permisos y roles que la app usa, tal como se le muestran a la persona en Ajustes (#25). */
enum class AppPermission {
    /** Ser la aplicación de mensajería principal (el rol de SMS). */
    DefaultSmsApp,

    /** Leer y recibir los SMS. */
    ReadSms,

    /** Enviar SMS. */
    SendSms,

    /** Mostrar notificaciones (las alertas). */
    Notifications,
}

data class PermissionStatus(val permission: AppPermission, val granted: Boolean)

/** Lee el estado actual de cada permiso. Se vuelve a leer cada vez que la persona vuelve a la app. */
fun interface PermissionStatusReader {
    fun statuses(): List<PermissionStatus>
}
