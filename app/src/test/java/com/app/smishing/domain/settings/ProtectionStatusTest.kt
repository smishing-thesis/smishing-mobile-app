package com.app.smishing.domain.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cuándo se puede decir que la protección está activa (#17): solo si se cumplen todos los requisitos. */
class ProtectionStatusTest {

    private fun all(granted: Boolean) = AppPermission.entries.map { PermissionStatus(it, granted) }
    private fun allBut(vararg denied: AppPermission) = AppPermission.entries.map { PermissionStatus(it, it !in denied) }

    @Test
    fun withEveryRequirementMetItIsActive() {
        val status = ProtectionStatus.of(all(true))
        assertTrue(status.active)
        assertEquals(emptyList<AppPermission>(), status.missing)
    }

    @Test
    fun withNothingGrantedItIsNotActiveAndEveryRequirementIsMissing() {
        val status = ProtectionStatus.of(all(false))
        assertFalse(status.active)
        assertEquals(ProtectionStatus.REQUIRED, status.missing)
    }

    @Test
    fun missingAnySingleRequirementMakesItNotActiveAndNamesIt() {
        for (required in ProtectionStatus.REQUIRED) {
            val status = ProtectionStatus.of(allBut(required))
            assertFalse("Sin $required no debe estar activa", status.active)
            assertEquals(listOf(required), status.missing)
        }
    }

    @Test
    fun theRequirementsAreTheRoleTheReadPermissionAndTheNotifications() {
        assertEquals(setOf(AppPermission.DefaultSmsApp, AppPermission.ReadSms, AppPermission.Notifications), ProtectionStatus.REQUIRED.toSet())
    }

    @Test
    fun sendingMessagesIsNotARequirementBecauseItDoesNotAffectDetection() {
        val status = ProtectionStatus.of(allBut(AppPermission.SendSms))
        assertTrue(status.active)
    }

    @Test
    fun aRequirementThatCouldNotBeCheckedCountsAsMissing() {
        // Sin información de las notificaciones no se da por cumplida.
        val partial = listOf(PermissionStatus(AppPermission.DefaultSmsApp, true), PermissionStatus(AppPermission.ReadSms, true))
        assertEquals(listOf(AppPermission.Notifications), ProtectionStatus.of(partial).missing)
        assertFalse(ProtectionStatus.of(emptyList()).active)
    }
}
