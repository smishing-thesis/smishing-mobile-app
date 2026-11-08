package com.app.smishing.domain.sms

/** Si la app tiene los permisos de SMS que necesita para analizar mensajes. */
enum class SmsPermissionState {
    /** Tiene todos. */
    Granted,

    /** Falta al menos uno: nunca se pidieron, se rechazaron o la persona los quitó después. */
    NotGranted,
}

/** Consulta los permisos de SMS. Se debe volver a consultar al volver a la app: la persona puede quitarlos en Ajustes. */
interface SmsPermissionRepository {
    fun state(): SmsPermissionState
}
