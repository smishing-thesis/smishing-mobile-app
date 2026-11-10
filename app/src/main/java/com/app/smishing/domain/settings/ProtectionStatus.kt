package com.app.smishing.domain.settings

/**
 * Si la protección está activa (#17): solo cuando se cumplen **todos** los requisitos. Un requisito que no aparece en la lista de
 * estados cuenta como faltante: lo que no se pudo comprobar no se da por cumplido.
 *
 * Los requisitos son el rol de mensajería principal, el permiso de leer y recibir SMS y las notificaciones (sin ellas el análisis
 * corre pero no se puede avisar). **Enviar mensajes no cuenta**: no afecta a la detección.
 */
data class ProtectionStatus(val missing: List<AppPermission>) {
    val active: Boolean get() = missing.isEmpty()

    companion object {
        val REQUIRED: List<AppPermission> = listOf(AppPermission.DefaultSmsApp, AppPermission.ReadSms, AppPermission.Notifications)

        fun of(statuses: List<PermissionStatus>): ProtectionStatus =
            ProtectionStatus(REQUIRED.filter { required -> statuses.none { it.permission == required && it.granted } })
    }
}

/** Qué pasos de la bienvenida ya se mostraron. Se guarda en el teléfono. */
interface OnboardingProgress {
    /** Si la persona ya vio la bienvenida (paso 1). Puede bloquear: no se llama desde el hilo principal. */
    fun welcomeSeen(): Boolean

    fun setWelcomeSeen(seen: Boolean)
}
