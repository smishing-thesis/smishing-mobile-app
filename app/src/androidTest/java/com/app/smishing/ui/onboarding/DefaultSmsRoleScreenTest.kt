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
import com.app.smishing.domain.sms.SmsRoleState
import com.app.smishing.ui.designsystem.theme.SmishingTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Paso 2 del onboarding: explica el rol antes de pedirlo y muestra el resultado. */
@RunWith(AndroidJUnit4::class)
class DefaultSmsRoleScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)

    private fun show(state: SmsRoleState, onRequest: () -> Unit = {}, onSkip: () -> Unit = {}) {
        compose.setContent { SmishingTheme { DefaultSmsRoleScreen(state, onRequest, onSkip) } }
    }

    private fun count(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun explainsTheRoleBeforeAskingForIt() {
        show(SmsRoleState.NotHeld)

        compose.onNodeWithText(text(R.string.default_sms_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.onboarding_step_two_of_three)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.default_sms_explanation_replace)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.default_sms_explanation_safe)).assertIsDisplayed()
    }

    @Test
    fun requestButtonAsksForTheRoleAndMeetsTheTouchTarget() {
        var requested = 0
        show(SmsRoleState.NotHeld, onRequest = { requested++ })

        compose.onNodeWithText(text(R.string.default_sms_action)).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, requested)
    }

    @Test
    fun skipLinkContinuesWithoutTheRole() {
        var skipped = false
        show(SmsRoleState.NotHeld, onSkip = { skipped = true })

        compose.onNodeWithText(text(R.string.onboarding_skip)).assertHeightIsAtLeast(48.dp).performClick()
        assertTrue(skipped)
    }

    @Test
    fun confirmsWhenTheAppHoldsTheRoleAndOffersNoRequest() {
        show(SmsRoleState.Held)

        compose.onNodeWithText(text(R.string.default_sms_held_message)).assertIsDisplayed()
        assertEquals(0, count(text(R.string.default_sms_action)))
        assertEquals(0, count(text(R.string.onboarding_skip)))
    }

    @Test
    fun saysSoWhenThePhoneDoesNotOfferTheRole() {
        show(SmsRoleState.Unavailable)

        compose.onNodeWithText(text(R.string.default_sms_unavailable)).assertIsDisplayed()
        assertEquals(0, count(text(R.string.default_sms_action)))
        compose.onNodeWithText(text(R.string.onboarding_skip)).assertIsDisplayed()
    }
}
