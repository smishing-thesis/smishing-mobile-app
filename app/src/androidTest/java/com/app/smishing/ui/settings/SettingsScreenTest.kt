package com.app.smishing.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.app.smishing.R
import com.app.smishing.domain.mitigation.MitigationMode
import com.app.smishing.domain.settings.AppPermission
import com.app.smishing.domain.settings.PermissionStatus
import com.app.smishing.ui.designsystem.theme.SmishingTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** "Ajustes" (#13, #25): el modo de mitigación y el estado de cada permiso con lo que queda limitado. */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)
    private fun count(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun all(granted: Boolean) = AppPermission.entries.map { PermissionStatus(it, granted) }
    private fun allBut(denied: AppPermission) = AppPermission.entries.map { PermissionStatus(it, it != denied) }

    private var mode by mutableStateOf<MitigationMode?>(MitigationMode.Ask)
    private var permissions by mutableStateOf(all(true))
    private val opened = mutableListOf<AppPermission>()

    private fun show() = compose.setContent {
        SmishingTheme {
            SettingsScreen(
                mode = mode,
                onModeChange = { mode = it },
                permissions = permissions,
                onOpenSystemSettings = { opened += it },
            )
        }
    }

    private fun stateOf(label: String) =
        compose.onNodeWithText(label).fetchSemanticsNode().let { node ->
            // El estado está en la fila (el texto está dentro de ella).
            generateSequence(node) { it.parent }.firstNotNullOfOrNull { it.config.getOrNull(SemanticsProperties.StateDescription) }
        }

    @Test
    fun showsTheThreeModesWithTheirDescriptionsAndTheCurrentOneSelected() {
        show()
        compose.onNodeWithText(text(R.string.settings_title)).assertIsDisplayed()
        // Las etiquetas de sección se muestran en mayúsculas (SectionLabel).
        compose.onNodeWithText(text(R.string.settings_mode_title).uppercase()).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.settings_permissions_title).uppercase()).assertIsDisplayed()
        for ((title, description) in listOf(
            R.string.settings_mode_automatic_title to R.string.settings_mode_automatic_description,
            R.string.settings_mode_ask_title to R.string.settings_mode_ask_description,
            R.string.settings_mode_disabled_title to R.string.settings_mode_disabled_description,
        )) {
            compose.onNodeWithText(text(title)).assertIsDisplayed()
            compose.onNodeWithText(text(description)).assertIsDisplayed()
        }
        compose.onNodeWithText(text(R.string.settings_mode_ask_title)).assertIsSelected()
    }

    @Test
    fun choosingAModeReportsItAndMovesTheSelection() {
        show()
        compose.onNodeWithText(text(R.string.settings_mode_automatic_title)).performClick()
        compose.waitForIdle()
        assertEquals(MitigationMode.Automatic, mode)
        compose.onNodeWithText(text(R.string.settings_mode_automatic_title)).assertIsSelected()

        compose.onNodeWithText(text(R.string.settings_mode_disabled_title)).performClick()
        compose.waitForIdle()
        assertEquals(MitigationMode.Disabled, mode)
    }

    @Test
    fun whileTheModeIsBeingReadNoOptionLooksSelected() {
        mode = null
        show()
        val selected = MitigationMode.entries.count { option ->
            val title = text(when (option) {
                MitigationMode.Automatic -> R.string.settings_mode_automatic_title
                MitigationMode.Ask -> R.string.settings_mode_ask_title
                MitigationMode.Disabled -> R.string.settings_mode_disabled_title
            })
            compose.onNodeWithText(title).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected) == true
        }
        assertEquals(0, selected)
    }

    @Test
    fun withEverythingGrantedItShowsTheStatesAndNoExplanation() {
        show()
        val labels = listOf(
            R.string.settings_permission_default_sms, R.string.settings_permission_read_sms, R.string.settings_permission_notifications,
        )
        for (label in labels) assertEquals(text(R.string.settings_state_granted), stateOf(text(label)))
        assertEquals(0, count(text(R.string.settings_open_system)))
    }

    @Test
    fun itShowsTheMockupPermissionsAndTheNotificationsButNotSending() {
        show()
        compose.onNodeWithText(text(R.string.settings_permission_default_sms)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.settings_permission_read_sms)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.settings_permission_notifications)).assertIsDisplayed()
        // Enviar mensajes no es un requisito de la protección y no está en el mockup.
        assertEquals(0, compose.onAllNodesWithText("enviar mensajes", substring = true, ignoreCase = true).fetchSemanticsNodes().size)
    }

    @Test
    fun aDeniedPermissionSaysWhatStopsWorkingAndOffersTheSystemSettings() {
        permissions = allBut(AppPermission.ReadSms)
        show()

        assertEquals(text(R.string.settings_state_denied), stateOf(text(R.string.settings_permission_read_sms)))
        assertEquals(text(R.string.settings_state_granted), stateOf(text(R.string.settings_permission_notifications)))
        compose.onNodeWithText(text(R.string.settings_limited_read_sms)).assertIsDisplayed()
        assertEquals(1, count(text(R.string.settings_open_system)))

        compose.onNodeWithText(text(R.string.settings_open_system)).performClick()
        assertEquals(listOf(AppPermission.ReadSms), opened)
    }

    @Test
    fun eachPermissionHasItsOwnExplanation() {
        val expected = mapOf(
            AppPermission.DefaultSmsApp to R.string.settings_limited_default_sms,
            AppPermission.ReadSms to R.string.settings_limited_read_sms,
            AppPermission.Notifications to R.string.settings_limited_notifications,
        )
        permissions = all(false)
        show()
        for ((_, explanation) in expected) assertEquals(1, count(text(explanation)))
        assertEquals(3, count(text(R.string.settings_open_system)))
        // Las tres explicaciones son distintas.
        assertEquals(3, expected.values.map { text(it) }.toSet().size)
    }

    @Test
    fun theStateFollowsThePermissionsWhenTheyChange() {
        permissions = allBut(AppPermission.Notifications)
        show()
        assertEquals(1, count(text(R.string.settings_limited_notifications)))

        permissions = all(true)
        compose.waitForIdle()
        assertEquals(0, count(text(R.string.settings_limited_notifications)))
        assertTrue(opened.isEmpty())
    }
}
