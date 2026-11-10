package com.app.smishing.ui.onboarding

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.app.smishing.PlainLanguage
import com.app.smishing.R
import com.app.smishing.ui.designsystem.theme.SmishingTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Paso 1 del onboarding (#17): la bienvenida. */
@RunWith(AndroidJUnit4::class)
class WelcomeScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)

    @Test
    fun showsTheStepTheTitleAndWhatTheAppDoes() {
        compose.setContent { SmishingTheme { WelcomeScreen(onNext = {}) } }
        compose.onNodeWithText(text(R.string.onboarding_step_one_of_three)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.welcome_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.welcome_body_one)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.welcome_body_two)).assertIsDisplayed()
    }

    @Test
    fun nextIsTheOnlyActionAndMeetsTheTouchTarget() {
        var next = 0
        compose.setContent { SmishingTheme { WelcomeScreen(onNext = { next++ }) } }
        compose.onNodeWithText(text(R.string.welcome_next)).assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText(text(R.string.welcome_next)).performClick()
        assertEquals(1, next)
    }

    @Test
    fun theTextsDoNotPromiseTotalProtectionAndKeepTheLanguageRules() {
        for (id in listOf(R.string.welcome_title, R.string.welcome_body_one, R.string.welcome_body_two, R.string.welcome_next)) {
            assertEquals(text(id), emptyList<String>(), PlainLanguage.violations(text(id)))
        }
        val body = text(R.string.welcome_body_one) + text(R.string.welcome_body_two)
        for (promise in listOf("proteg", "inmediatamente", "garantiz")) {
            assertEquals("No debe prometer: $promise", false, body.contains(promise, ignoreCase = true))
        }
    }
}
