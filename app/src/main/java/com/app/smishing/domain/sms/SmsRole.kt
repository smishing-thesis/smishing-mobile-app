package com.app.smishing.domain.sms

/** Si esta app es hoy la aplicación de SMS predeterminada del teléfono. */
enum class SmsRoleState {
    /** La app tiene el rol. */
    Held,

    /** La app no lo tiene: no lo pidió, la persona lo rechazó o lo quitó después. */
    NotHeld,

    /** El teléfono no ofrece el rol de SMS (por ejemplo, un dispositivo sin telefonía). */
    Unavailable,
}

fun smsRoleState(roleAvailable: Boolean, roleHeld: Boolean): SmsRoleState = when {
    !roleAvailable -> SmsRoleState.Unavailable
    roleHeld -> SmsRoleState.Held
    else -> SmsRoleState.NotHeld
}

/** Consulta el estado del rol. Se debe volver a consultar al volver a la app: la persona puede quitarlo en Ajustes. */
interface SmsRoleRepository {
    fun state(): SmsRoleState
}
