package com.app.smishing.ui.onboarding

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.app.smishing.R
import com.app.smishing.domain.sms.SmsPermissionState
import com.app.smishing.ui.designsystem.theme.SmishingTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Paso 3 del onboarding: pide el permiso de lectura, y guía a los ajustes si Android ya no muestra la solicitud. */
@RunWith(AndroidJUnit4::class)
class SmsPermissionScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)

    private fun show(
        state: SmsPermissionState,
        blocked: Boolean = false,
        onRequest: () -> Unit = {},
        onOpenSettings: () -> Unit = {},
        onSkip: () -> Unit = {},
    ) {
        compose.setContent { SmishingTheme { SmsPermissionScreen(state, blocked, onRequest, onOpenSettings, onSkip) } }
    }

    private fun count(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun explainsWhyItNeedsToReadMessagesBeforeAsking() {
        show(SmsPermissionState.NotGranted)

        compose.onNodeWithText(text(R.string.sms_permission_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.onboarding_step_three_of_three)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.sms_permission_explanation_read)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.sms_permission_explanation_privacy)).assertIsDisplayed()
    }

    @Test
    fun allowButtonRequestsThePermissionAndMeetsTheTouchTarget() {
        var requested = 0
        show(SmsPermissionState.NotGranted, onRequest = { requested++ })

        compose.onNodeWithText(text(R.string.sms_permission_action)).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, requested)
    }

    @Test
    fun skipLinkContinuesWithoutThePermission() {
        var skipped = false
        show(SmsPermissionState.NotGranted, onSkip = { skipped = true })

        compose.onNodeWithText(text(R.string.onboarding_skip_short)).assertHeightIsAtLeast(48.dp).performClick()
        assertTrue(skipped)
    }

    @Test
    fun sendsThePersonToTheSettingsWhenAndroidNoLongerAsks() {
        var settings = 0
        show(SmsPermissionState.NotGranted, blocked = true, onOpenSettings = { settings++ })

        compose.onNodeWithText(text(R.string.sms_permission_blocked_message)).assertIsDisplayed()
        assertEquals("No debe ofrecer una solicitud que Android ya no muestra", 0, count(text(R.string.sms_permission_action)))
        compose.onNodeWithText(text(R.string.sms_permission_open_settings)).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, settings)
    }

    @Test
    fun withThePermissionGrantedItOffersNoRequestAndKeepsTheMockupText() {
        show(SmsPermissionState.Granted)

        compose.onNodeWithText(text(R.string.sms_permission_explanation_read)).assertIsDisplayed()
        assertEquals(0, count(text(R.string.sms_permission_action)))
        assertEquals(0, count(text(R.string.onboarding_skip_short)))
    }
}
