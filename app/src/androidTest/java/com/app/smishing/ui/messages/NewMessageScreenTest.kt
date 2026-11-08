package com.app.smishing.ui.messages

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.app.smishing.R
import com.app.smishing.domain.messaging.SendOutcome
import com.app.smishing.ui.designsystem.theme.SmishingTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** "Nuevo mensaje" (#6): el destinatario, el texto y el botón de enviar. */
@RunWith(AndroidJUnit4::class)
class NewMessageScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)

    private var recipient by mutableStateOf("")
    private var body by mutableStateOf("")
    private var sent = 0
    private var back = 0

    private fun show(error: SendOutcome? = null) {
        compose.setContent {
            SmishingTheme {
                NewMessageScreen(
                    recipient = recipient,
                    onRecipientChange = { recipient = it },
                    body = body,
                    onBodyChange = { body = it },
                    onSend = { sent++ },
                    onBack = { back++ },
                    error = error,
                )
            }
        }
    }

    @Test
    fun showsTheTitleTheRecipientAndTheTextAreaAndTheSendButton() {
        show()

        compose.onNodeWithText(text(R.string.new_message_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.new_message_to)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.new_message_recipient_hint)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.new_message_body_hint)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.new_message_send)).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun theSendButtonOnlyWorksWithARecipientAndATextAndSendsOnTap() {
        show()
        val send = compose.onNodeWithText(text(R.string.new_message_send))
        send.assertIsNotEnabled()

        compose.onNodeWithText(text(R.string.new_message_recipient_hint)).performTextInput("987 654 321")
        send.assertIsNotEnabled() // falta el texto

        compose.onNodeWithText(text(R.string.new_message_body_hint)).performTextInput("Hola")
        assertEquals("987 654 321", recipient)
        assertEquals("Hola", body)
        send.assertIsEnabled().performClick()
        assertEquals(1, sent)
    }

    @Test
    fun theBackArrowGoesBack() {
        show()
        compose.onNodeWithContentDescription(text(R.string.conversation_back)).performClick()
        assertEquals(1, back)
    }

    @Test
    fun anInvalidRecipientErrorIsShown() {
        show(error = SendOutcome.InvalidRecipient)
        compose.onNodeWithText(text(R.string.send_error_invalid_recipient)).assertIsDisplayed()
    }

    @Test
    fun aMissingPermissionErrorExplainsWhatToDo() {
        show(error = SendOutcome.NotAllowed)
        compose.onNodeWithText(text(R.string.send_error_not_allowed)).assertIsDisplayed()
    }
}
