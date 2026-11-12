package com.app.smishing.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.app.smishing.PlainLanguage
import com.app.smishing.R
import com.app.smishing.domain.history.WeeklySummary
import com.app.smishing.domain.settings.AppPermission
import com.app.smishing.domain.settings.PermissionStatus
import com.app.smishing.ui.designsystem.theme.SmishingTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** "Inicio" (#17): no dice que la protección está activa si falta algún requisito, y muestra el estado de cada uno. */
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)
    private fun count(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun all(granted: Boolean) = AppPermission.entries.map { PermissionStatus(it, granted) }
    private fun allBut(vararg denied: AppPermission) = AppPermission.entries.map { PermissionStatus(it, it !in denied) }

    private var statuses by mutableStateOf<List<PermissionStatus>?>(all(true))
    private var summary by mutableStateOf<WeeklySummary?>(WeeklySummary(47, 2))
    private var activated = 0

    private fun show() = compose.setContent {
        SmishingTheme { HomeScreen(statuses = statuses, summary = summary, onActivate = { activated++ }) }
    }

    @Test
    fun withEveryRequirementMetItSaysTheProtectionIsActive() {
        show()
        compose.onNodeWithText(text(R.string.home_active_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.home_active_message)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.home_status_active)).assertIsDisplayed()
        assertEquals(0, count(text(R.string.protection_off_title)))
        assertEquals(0, count(text(R.string.protection_activate)))
        // El mockup no lista los requisitos en Inicio: se ven en Ajustes.
        assertEquals(0, count(text(R.string.settings_permission_default_sms)))
        assertEquals(0, count(text(R.string.settings_permission_read_sms)))
    }

    private fun assertNotActiveWithout(missing: AppPermission) {
        statuses = allBut(missing)
        show()
        compose.waitForIdle()
        assertEquals("Sin $missing no debe decir activa", 0, count(text(R.string.home_active_title)))
        assertEquals(0, count(text(R.string.home_status_active)))
        compose.onNodeWithText(text(R.string.protection_off_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.home_status_inactive)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.protection_activate)).assertIsDisplayed()
    }

    @Test
    fun withoutTheDefaultSmsAppItNeverSaysActive() = assertNotActiveWithout(AppPermission.DefaultSmsApp)

    @Test
    fun withoutTheReadPermissionItNeverSaysActive() = assertNotActiveWithout(AppPermission.ReadSms)

    @Test
    fun withoutNotificationsItNeverSaysActive() = assertNotActiveWithout(AppPermission.Notifications)

    @Test
    fun withoutTheSendPermissionItIsStillActiveBecauseSendingIsNotARequirement() {
        statuses = allBut(AppPermission.SendSms)
        show()
        compose.onNodeWithText(text(R.string.home_active_title)).assertIsDisplayed()
    }

    @Test
    fun whenSomethingIsMissingItUsesTheMockupMessageAndListsNothingElse() {
        statuses = allBut(AppPermission.Notifications)
        show()
        compose.onNodeWithText(text(R.string.protection_off_message)).assertIsDisplayed()
        assertEquals("Lo que falta se ve en Ajustes, no en Inicio", 0, count(text(R.string.settings_permission_notifications)))
    }

    @Test
    fun tappingActivateReportsIt() {
        statuses = all(false)
        show()
        compose.onNodeWithText(text(R.string.protection_activate)).performClick()
        assertEquals(1, activated)
    }

    @Test
    fun theStateFollowsThePermissionsWhenTheyChange() {
        statuses = allBut(AppPermission.DefaultSmsApp)
        show()
        assertEquals(1, count(text(R.string.protection_off_title)))

        statuses = all(true)
        compose.waitForIdle()
        assertEquals(0, count(text(R.string.protection_off_title)))
        compose.onNodeWithText(text(R.string.home_active_title)).assertIsDisplayed()
    }

    @Test
    fun whileTheStateIsBeingReadNothingIsClaimed() {
        statuses = null
        summary = null
        show()
        assertEquals(0, count(text(R.string.home_active_title)))
        assertEquals(0, count(text(R.string.protection_off_title)))
        assertEquals(0, count(text(R.string.home_status_active)))
        assertEquals(0, count(text(R.string.home_status_inactive)))
    }

    @Test
    fun theWeeklySummaryShowsTheCounts() {
        show()
        compose.onNodeWithText(text(R.string.home_summary_title)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("${text(R.string.home_summary_reviewed)} 47").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("${text(R.string.home_summary_scams)} ${context.getString(R.string.home_summary_scams_value, 2)}")
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun whileTheSummaryIsBeingReadItShowsDashes() {
        summary = null
        show()
        compose.onNodeWithText("${text(R.string.home_summary_reviewed)} --").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun everyTextOfTheHomeKeepsTheLanguageRules() {
        val strings = listOf(
            R.string.home_status_active, R.string.home_status_inactive, R.string.home_active_title, R.string.home_active_message,
            R.string.home_summary_title, R.string.home_summary_reviewed, R.string.home_summary_scams,
            R.string.home_summary_scams_value, R.string.protection_off_title, R.string.protection_off_message, R.string.protection_activate,
        )
        for (id in strings) assertEquals(text(id), emptyList<String>(), PlainLanguage.violations(text(id)))
    }
}
