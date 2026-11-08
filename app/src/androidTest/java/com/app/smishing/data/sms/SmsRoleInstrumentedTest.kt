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
import com.app.smishing.domain.sms.SmsRoleState
import com.app.smishing.ui.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * #2: la app es candidata a gestor de SMS (el sistema acepta darle el rol) y comprueba si lo conserva. El rol se
 * concede y se quita con comandos de shell, igual que lo haría la persona en los ajustes. El diálogo real del sistema
 * se revisó a mano en el emulador (ver docs/gestor-sms.md).
 */
@RunWith(AndroidJUnit4::class)
class SmsRoleInstrumentedTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val roles = AndroidSmsRoleRepository(context)

    @Before
    fun startWithoutTheRole() = revoke()

    @After
    fun leaveWithoutTheRole() = revoke()

    @Test
    fun theSystemAcceptsTheAppAsDefaultSmsApp() {
        grant()
        assertEquals("El sistema no aceptó a la app como gestor de SMS", SmsRoleState.Held, roles.state())
    }

    @Test
    fun stateIsNotHeldAfterTheRoleIsRemoved() {
        grant()
        revoke()
        assertEquals(SmsRoleState.NotHeld, roles.state())
    }

    @Test
    fun requestIntentIsOfferedWhileTheRoleIsNotHeld() {
        assertNotNull(roles.requestIntent())
    }

    @Test
    fun mainActivityChecksTheRoleAgainWhenReturningToTheApp() {
        val explanation = context.getString(R.string.default_sms_explanation_replace)
        val confirmation = context.getString(R.string.default_sms_held_message)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.onNodeWithText(explanation).assertIsDisplayed()

            // La persona acepta el rol y vuelve a la app.
            grant()
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            compose.waitUntil(TIMEOUT_MS) { exists(confirmation) }
            compose.onNodeWithText(confirmation).assertIsDisplayed()

            // Después lo quita en los ajustes del sistema y vuelve: la app lo nota.
            revoke()
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            compose.waitUntil(TIMEOUT_MS) { exists(explanation) }
            compose.onNodeWithText(explanation).assertIsDisplayed()
        }
    }

    private fun exists(text: String): Boolean =
        compose.onAllNodesWithText(text).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

    private fun grant() {
        Shell.run("cmd role add-role-holder --user 0 $ROLE ${context.packageName}")
        waitForState(SmsRoleState.Held)
    }

    private fun revoke() {
        Shell.run("cmd role remove-role-holder --user 0 $ROLE ${context.packageName}")
        waitForState(SmsRoleState.NotHeld)
    }

    private fun waitForState(expected: SmsRoleState) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (roles.state() != expected && System.currentTimeMillis() < deadline) Thread.sleep(100)
    }

    private companion object {
        const val ROLE = "android.app.role.SMS"
        const val TIMEOUT_MS = 10_000L
    }
}
