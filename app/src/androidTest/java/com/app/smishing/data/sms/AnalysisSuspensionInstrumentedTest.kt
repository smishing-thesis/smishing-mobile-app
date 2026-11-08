package com.app.smishing.data.sms

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.app.smishing.data.classification.OnDeviceClassifier
import com.app.smishing.domain.classification.ClassificationResult
import com.app.smishing.domain.classification.GatedMessageClassifier
import com.app.smishing.domain.sms.AnalysisStatus
import com.app.smishing.domain.sms.RoleAndPermissionsGate
import com.app.smishing.domain.sms.SmsPermissionState
import com.app.smishing.domain.sms.SmsRoleState
import com.app.smishing.domain.sms.SuspensionReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * #3: con algún permiso de SMS sin conceder, el análisis está suspendido y el modelo no se usa.
 *
 * Esta prueba mide el estado en que encuentra a la app; no puede crearlo, porque quitar un permiso mata el proceso de
 * la prueba. Se ejecuta de verdad en la segunda fase de la verificación de la revocación: primero se concede el rol (la
 * prueba `roleFirstThenPermissionsThenAnalysis`), después se quita un permiso desde fuera con `pm revoke` y entonces
 * se corre esta (ver docs/permisos-sms.md). Si todos los permisos están concedidos, se omite.
 */
@RunWith(AndroidJUnit4::class)
class AnalysisSuspensionInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val roles = AndroidSmsRoleRepository(context)
    private val permissions = AndroidSmsPermissionRepository(context)

    @Test
    fun analysisIsSuspendedWhileAnSmsPermissionIsMissing() {
        assumeTrue("Todos los permisos están concedidos: no hay nada que suspender", permissions.state() == SmsPermissionState.NotGranted)

        val status = RoleAndPermissionsGate(roles, permissions).status()
        assertTrue("Debía estar suspendido: $status", status is AnalysisStatus.Suspended)
        status as AnalysisStatus.Suspended
        assertTrue(SuspensionReason.PermissionsNotGranted in status.reasons)
        assertEquals(
            "El rol y el motivo de suspensión deben coincidir",
            roles.state() != SmsRoleState.Held,
            SuspensionReason.RoleNotHeld in status.reasons,
        )

        OnDeviceClassifier.create(context).use { classifier ->
            val gated = GatedMessageClassifier(classifier, RoleAndPermissionsGate(roles, permissions))
            assertEquals(
                ClassificationResult.NotClassified(ClassificationResult.NotClassified.Reason.ANALYSIS_SUSPENDED),
                gated.classify("Tu codigo de verificacion es 458920. No lo compartas con nadie."),
            )
        }
    }

    /** Tercera fase: después de devolver el permiso con `pm grant` (conceder no mata el proceso), el análisis se reanuda. */
    @Test
    fun analysisResumesWhenThePermissionsAreGrantedAgain() {
        assumeTrue(
            "Se necesita el rol y todos los permisos concedidos",
            roles.state() == SmsRoleState.Held && permissions.state() == SmsPermissionState.Granted,
        )

        val gate = RoleAndPermissionsGate(roles, permissions)
        assertEquals(AnalysisStatus.Active, gate.status())
        OnDeviceClassifier.create(context).use { classifier ->
            val result = GatedMessageClassifier(classifier, gate)
                .classify("Tu codigo de verificacion es 458920. No lo compartas con nadie.")
            assertTrue("Con rol y permisos debía analizar: $result", result is ClassificationResult.Classified)
        }
    }
}
