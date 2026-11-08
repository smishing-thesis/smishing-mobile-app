package com.app.smishing.domain.sms

/** Por qué el análisis está suspendido. */
enum class SuspensionReason {
    /** La app no es la aplicación de SMS predeterminada, así que no recibe los mensajes. */
    RoleNotHeld,

    /** Falta algún permiso de SMS. */
    PermissionsNotGranted,
}

sealed interface AnalysisStatus {
    /** Hay rol y permisos: se pueden analizar los mensajes. */
    data object Active : AnalysisStatus

    /** No se analiza nada hasta que se corrija lo que falta. */
    data class Suspended(val reasons: Set<SuspensionReason>) : AnalysisStatus
}

/** El análisis solo está activo con el rol de SMS y todos los permisos. Si falta algo, se suspende. */
fun analysisStatus(role: SmsRoleState, permissions: SmsPermissionState): AnalysisStatus {
    val reasons = buildSet {
        if (role != SmsRoleState.Held) add(SuspensionReason.RoleNotHeld)
        if (permissions != SmsPermissionState.Granted) add(SuspensionReason.PermissionsNotGranted)
    }
    return if (reasons.isEmpty()) AnalysisStatus.Active else AnalysisStatus.Suspended(reasons)
}

/** Dice si se puede analizar ahora. Quien analiza mensajes lo consulta antes de cada uno. */
interface AnalysisGate {
    fun status(): AnalysisStatus
}

/** Consulta el estado actual del rol y de los permisos cada vez, porque la persona puede quitarlos en cualquier momento. */
class RoleAndPermissionsGate(
    private val roles: SmsRoleRepository,
    private val permissions: SmsPermissionRepository,
) : AnalysisGate {
    override fun status(): AnalysisStatus = analysisStatus(roles.state(), permissions.state())
}
