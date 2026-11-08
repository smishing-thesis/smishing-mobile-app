package com.app.smishing.domain.sms

import com.app.smishing.domain.sms.SuspensionReason.PermissionsNotGranted
import com.app.smishing.domain.sms.SuspensionReason.RoleNotHeld
import org.junit.Assert.assertEquals
import org.junit.Test

/** #3: el análisis solo está activo con el rol de SMS y todos los permisos; si falta algo, se suspende. */
class AnalysisGateTest {

    private class FakeRoles(var state: SmsRoleState) : SmsRoleRepository {
        override fun state() = state
    }

    private class FakePermissions(var state: SmsPermissionState) : SmsPermissionRepository {
        override fun state() = state
    }

    @Test
    fun activeWithTheRoleAndAllThePermissions() {
        assertEquals(AnalysisStatus.Active, analysisStatus(SmsRoleState.Held, SmsPermissionState.Granted))
    }

    @Test
    fun suspendedWhenAPermissionIsMissingEvenWithTheRole() {
        val status = analysisStatus(SmsRoleState.Held, SmsPermissionState.NotGranted)
        assertEquals(AnalysisStatus.Suspended(setOf(PermissionsNotGranted)), status)
    }

    @Test
    fun suspendedWhenTheRoleIsMissingEvenWithThePermissions() {
        val status = analysisStatus(SmsRoleState.NotHeld, SmsPermissionState.Granted)
        assertEquals(AnalysisStatus.Suspended(setOf(RoleNotHeld)), status)
    }

    @Test
    fun suspendedForBothReasonsWhenNothingIsGranted() {
        val status = analysisStatus(SmsRoleState.NotHeld, SmsPermissionState.NotGranted)
        assertEquals(AnalysisStatus.Suspended(setOf(RoleNotHeld, PermissionsNotGranted)), status)
    }

    @Test
    fun suspendedWhenThePhoneDoesNotOfferTheRole() {
        val status = analysisStatus(SmsRoleState.Unavailable, SmsPermissionState.Granted)
        assertEquals(AnalysisStatus.Suspended(setOf(RoleNotHeld)), status)
    }

    @Test
    fun theGateChecksTheCurrentStateEveryTime() {
        val roles = FakeRoles(SmsRoleState.Held)
        val permissions = FakePermissions(SmsPermissionState.Granted)
        val gate = RoleAndPermissionsGate(roles, permissions)

        assertEquals(AnalysisStatus.Active, gate.status())

        // La persona quita un permiso en los ajustes: el análisis se suspende sin reiniciar nada.
        permissions.state = SmsPermissionState.NotGranted
        assertEquals(AnalysisStatus.Suspended(setOf(PermissionsNotGranted)), gate.status())

        // Y lo vuelve a conceder: el análisis se reanuda.
        permissions.state = SmsPermissionState.Granted
        assertEquals(AnalysisStatus.Active, gate.status())

        // Quita el rol.
        roles.state = SmsRoleState.NotHeld
        assertEquals(AnalysisStatus.Suspended(setOf(RoleNotHeld)), gate.status())
    }
}
