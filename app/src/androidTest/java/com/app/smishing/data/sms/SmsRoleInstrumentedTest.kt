package com.app.smishing.data.sms

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.app.smishing.R
import com.app.smishing.Shell
import com.app.smishing.data.classification.OnDeviceClassifier
import com.app.smishing.domain.classification.ClassificationResult
import com.app.smishing.domain.classification.GatedMessageClassifier
import com.app.smishing.domain.sms.AnalysisStatus
import com.app.smishing.domain.sms.RoleAndPermissionsGate
import com.app.smishing.domain.sms.SmsPermissionState
import com.app.smishing.domain.sms.SmsRoleState
import com.app.smishing.domain.sms.SuspensionReason
import com.app.smishing.ui.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * #2 y #3: la app es candidata a gestor de SMS, los permisos se piden después del rol y el análisis solo se activa con
 * el rol y los permisos. El rol se concede con un comando de shell, igual que lo haría la persona en los ajustes.
 *
 * Ninguna prueba puede quitar el rol ni un permiso: Android mata el proceso de la app al revocarlos, y estas pruebas
 * corren dentro de ese proceso (se comprobó: "Instrumentation run failed due to Process crashed"). Por eso el flujo solo
 * avanza, de "sin rol" a "con rol", y termina con el rol concedido; Gradle desinstala la app al acabar. La revocación se
 * prueba en dos fases, quitando el permiso desde fuera entre una ejecución y otra (ver docs/permisos-sms.md).
 */
@RunWith(AndroidJUnit4::class)
class SmsRoleInstrumentedTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val roles = AndroidSmsRoleRepository(context)
    private val permissions = AndroidSmsPermissionRepository(context)

    @Test
    fun theSystemOffersTheRoleRequest() {
        assertNotNull(roles.requestIntent())
    }

    @Test
    fun roleFirstThenPermissionsThenAnalysis() {
        assumeTrue(
            "La app ya tenía el rol: desinstálala para repetir esta prueba (Gradle lo hace al terminar).",
            roles.state() == SmsRoleState.NotHeld,
        )
        val gate = RoleAndPermissionsGate(roles, permissions)
        val gated = { classifier: OnDeviceClassifier -> GatedMessageClassifier(classifier, gate) }
        val message = "Tu codigo de verificacion es 458920. No lo compartas con nadie."

        // 1. Sin rol ni permisos, el análisis está suspendido y el modelo no se usa.
        assertEquals(SmsPermissionState.NotGranted, permissions.state())
        assertEquals(
            AnalysisStatus.Suspended(setOf(SuspensionReason.RoleNotHeld, SuspensionReason.PermissionsNotGranted)),
            gate.status(),
        )
        OnDeviceClassifier.create(context).use { classifier ->
            assertEquals(
                ClassificationResult.NotClassified(ClassificationResult.NotClassified.Reason.ANALYSIS_SUSPENDED),
                gated(classifier).classify(message),
            )
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // 2. Sin rol, la app pide primero el rol: todavía no menciona los permisos.
            compose.onNodeWithText(context.getString(R.string.default_sms_explanation_replace)).assertIsDisplayed()
            assertEquals(0, count(context.getString(R.string.sms_permission_explanation_read)))

            // 3. La persona acepta el rol. Android concede con él los permisos de SMS.
            grantRole()
            assertEquals(SmsRoleState.Held, roles.state())
            assertEquals("Android debía conceder los permisos con el rol", SmsPermissionState.Granted, permissions.state())
            assertEquals(AnalysisStatus.Active, gate.status())

            // 4. Al volver a la app, ya tiene el rol y los permisos.
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            val granted = context.getString(R.string.sms_permission_held_message)
            compose.waitUntil(TIMEOUT_MS) { count(granted) > 0 }
            compose.onNodeWithText(granted).assertIsDisplayed()
        }

        // 5. Con el análisis activo, el clasificador con compuerta analiza el mensaje.
        OnDeviceClassifier.create(context).use { classifier ->
            val result = gated(classifier).classify(message)
            assertTrue("Con rol y permisos debía analizar: $result", result is ClassificationResult.Classified)
        }
    }

    private fun count(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes(atLeastOneRootRequired = false).size

    private fun grantRole() {
        Shell.run("cmd role add-role-holder --user 0 $ROLE ${context.packageName}")
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (roles.state() != SmsRoleState.Held && System.currentTimeMillis() < deadline) Thread.sleep(100)
    }

    private companion object {
        const val ROLE = "android.app.role.SMS"
        const val TIMEOUT_MS = 10_000L
    }
}
