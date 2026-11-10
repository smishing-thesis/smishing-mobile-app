package com.app.smishing.ui.messages

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.app.smishing.R
import com.app.smishing.domain.messaging.Conversation
import com.app.smishing.domain.messaging.ConversationMessage
import com.app.smishing.domain.messaging.MessageDirection
import com.app.smishing.domain.messaging.SendOutcome
import com.app.smishing.ui.designsystem.theme.SmishingTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.time.ZoneId

/** Las dos pantallas de #5 con datos sintéticos: la lista de mensajes y la conversación. */
@RunWith(AndroidJUnit4::class)
class MessagesScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone = ZoneId.of("America/Lima")
    private fun at(month: Int, day: Int, hour: Int = 12, minute: Int = 0) =
        LocalDateTime.of(2026, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private val now = at(11, 8, 18, 30)
    private val conversations = listOf(
        Conversation(1, "987 654 321", "Estimado cliente, su tarjeta ha sido suspendida.", at(11, 7, 9, 0)),
        Conversation(2, "Mamá", "Ya llegué a casa, la cena está lista", at(11, 8, 14, 20)),
        Conversation(3, "Carlos", "Nos vemos el lunes en la oficina", at(3, 12)),
    )

    private fun count(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun theListShowsSenderDateAndSnippetOfEachConversation() {
        compose.setContent {
            SmishingTheme { ConversationListScreen(conversations, now, onOpen = {}, onCompose = {}, zone = zone) }
        }

        compose.onNodeWithText(context.getString(R.string.messages_title)).assertIsDisplayed()
        for (c in conversations) {
            compose.onNodeWithText(c.address).assertIsDisplayed()
            compose.onNodeWithText(c.snippet).assertIsDisplayed()
        }
        // La fecha sigue la regla del diseño: "Ayer", la hora de hoy y mes y día.
        compose.onNodeWithText(context.getString(R.string.messages_yesterday)).assertIsDisplayed()
        compose.onNodeWithText("14:20").assertIsDisplayed()
        compose.onNodeWithText("Marzo 12").assertIsDisplayed()
    }

    @Test
    fun conversationsWithUnreadMessagesShowACounterAndTheOthersDoNot() {
        val withUnread = listOf(
            Conversation(1, "987 654 321", "Estimado cliente, su tarjeta ha sido suspendida.", at(11, 7, 9, 0), unreadCount = 3),
            Conversation(2, "Mamá", "Ya llegué a casa, la cena está lista", at(11, 8, 14, 20), unreadCount = 1),
            Conversation(3, "Carlos", "Nos vemos el lunes en la oficina", at(3, 12)),
        )
        compose.setContent {
            SmishingTheme { ConversationListScreen(withUnread, now, onOpen = {}, onCompose = {}, zone = zone) }
        }
        // El contador dice cuántos, también para un lector de pantalla.
        compose.onNodeWithContentDescription(context.getString(R.string.messages_unread_description, 3)).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.messages_unread_description, 1)).assertIsDisplayed()
        // La conversación leída no lleva contador: solo hay dos en toda la lista.
        val counters = (1..9).sumOf { n -> compose.onAllNodesWithContentDescription(context.getString(R.string.messages_unread_description, n)).fetchSemanticsNodes().size }
        assertEquals(2, counters)
        compose.onNodeWithText("Carlos").assertIsDisplayed()
    }

    @Test
    fun theCounterNeverAppearsWhenThereIsNothingUnread() {
        compose.setContent {
            SmishingTheme { ConversationListScreen(conversations, now, onOpen = {}, onCompose = {}, zone = zone) }
        }
        for (n in 1..9) {
            assertEquals(0, compose.onAllNodesWithContentDescription(context.getString(R.string.messages_unread_description, n)).fetchSemanticsNodes().size)
        }
    }

    @Test
    fun tappingAConversationOpensItAndRowsMeetTheTouchTarget() {
        var opened: Conversation? = null
        compose.setContent {
            SmishingTheme { ConversationListScreen(conversations, now, onOpen = { opened = it }, onCompose = {}, zone = zone) }
        }

        compose.onNodeWithText("Mamá").assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText("Mamá").performClick()
        assertEquals(conversations[1], opened)
    }

    @Test
    fun anEmptyListSaysThereAreNoConversationsYet() {
        compose.setContent { SmishingTheme { ConversationListScreen(emptyList(), now, onOpen = {}, onCompose = {}, zone = zone) } }
        compose.onNodeWithText(context.getString(R.string.messages_empty)).assertIsDisplayed()
    }

    @Test
    fun whileLoadingNeitherTheListNorTheEmptyMessageIsShown() {
        compose.setContent { SmishingTheme { ConversationListScreen(null, now, onOpen = {}, onCompose = {}, zone = zone) } }
        assertEquals(0, count(context.getString(R.string.messages_empty)))
        compose.onNodeWithText(context.getString(R.string.messages_title)).assertIsDisplayed()
    }

    private fun bubble(text: String, description: String) =
        hasText(text).and(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, description))

    @Test
    fun theConversationShowsReceivedOnTheLeftAndSentOnTheRightInOrder() {
        val messages = listOf(
            ConversationMessage(1, "Estimado cliente, su tarjeta ha sido suspendida.", at(11, 8, 9, 0), MessageDirection.Incoming),
            ConversationMessage(2, "¿Por qué me mandan esto?", at(11, 8, 9, 5), MessageDirection.Outgoing),
            ConversationMessage(3, "Segundo recibido", at(11, 8, 9, 6), MessageDirection.Incoming),
        )
        compose.setContent { SmishingTheme { ConversationScreen("987 654 321", messages, onBack = {}, draft = "", onDraftChange = {}, onSend = {}, error = null) } }

        compose.onNodeWithText("987 654 321").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.conversation_subtitle)).assertIsDisplayed()

        val received = context.getString(R.string.message_received)
        val sent = context.getString(R.string.message_sent)
        val first = compose.onNode(bubble(messages[0].body, received)).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val second = compose.onNode(bubble(messages[1].body, sent)).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val third = compose.onNode(bubble(messages[2].body, received)).assertIsDisplayed().fetchSemanticsNode().boundsInRoot

        assertTrue("Del más antiguo al más reciente", first.top < second.top && second.top < third.top)

        // La alineación se mide en el texto de la burbuja (el nodo fusionado de arriba ocupa todo el ancho de la fila).
        fun textLeft(text: String) = compose.onNode(hasText(text), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left
        assertTrue("Lo enviado va más a la derecha que lo recibido", textLeft(messages[1].body) > textLeft(messages[0].body))
        assertEquals("Lo recibido va alineado a la izquierda", textLeft(messages[0].body), textLeft(messages[2].body), 0.5f)
    }

    @Test
    fun theBackArrowReturnsToTheList() {
        var back = 0
        compose.setContent { SmishingTheme { ConversationScreen("987 654 321", emptyList(), onBack = { back++ }, draft = "", onDraftChange = {}, onSend = {}, error = null) } }

        compose.onNodeWithContentDescription(context.getString(R.string.conversation_back)).performClick()
        assertEquals(1, back)
    }

    @Test
    fun theComposeButtonOpensANewMessage() {
        var composeTaps = 0
        compose.setContent {
            SmishingTheme { ConversationListScreen(conversations, now, onOpen = {}, onCompose = { composeTaps++ }, zone = zone) }
        }

        compose.onNodeWithText(context.getString(R.string.compose_action)).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, composeTaps)
    }

    @Test
    fun theInputBarSendsWhatWasTypedAndIgnoresAnEmptyDraft() {
        var draft by mutableStateOf("")
        var sent = 0
        compose.setContent {
            SmishingTheme {
                ConversationScreen("987 654 321", emptyList(), onBack = {}, draft = draft, onDraftChange = { draft = it }, onSend = { sent++ }, error = null)
            }
        }
        val send = compose.onNodeWithContentDescription(context.getString(R.string.conversation_send))

        send.performClick()
        assertEquals("Con el texto vacío no se envía", 0, sent)

        compose.onNodeWithText(context.getString(R.string.conversation_input_hint)).performTextInput("Hola")
        assertEquals("Hola", draft)
        send.performClick()
        assertEquals(1, sent)
    }

    @Test
    fun aMessageThatCouldNotBeSentSaysSoAndOthersDoNot() {
        val messages = listOf(
            ConversationMessage(1, "Este salió", at(11, 8, 9, 0), MessageDirection.Outgoing),
            ConversationMessage(2, "Este no salió", at(11, 8, 9, 1), MessageDirection.Outgoing, failed = true),
        )
        compose.setContent {
            SmishingTheme { ConversationScreen("987 654 321", messages, onBack = {}, draft = "", onDraftChange = {}, onSend = {}, error = null) }
        }

        assertEquals(1, count(context.getString(R.string.message_failed)))
    }

    @Test
    fun theSendErrorIsShownInTheConversation() {
        compose.setContent {
            SmishingTheme {
                ConversationScreen("987 654 321", emptyList(), onBack = {}, draft = "", onDraftChange = {}, onSend = {}, error = SendOutcome.Failed)
            }
        }
        compose.onNodeWithText(context.getString(R.string.send_error_failed)).assertIsDisplayed()
    }
}
