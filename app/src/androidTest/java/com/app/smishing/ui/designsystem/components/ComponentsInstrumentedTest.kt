package com.app.smishing.ui.designsystem.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.smishing.ui.designsystem.theme.SmishingTheme
import com.app.smishing.ui.designsystem.theme.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Los componentes de la librería: se muestran, responden al toque y respetan el área táctil de 48 dp. */
@RunWith(AndroidJUnit4::class)
class ComponentsInstrumentedTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun primaryButtonClicksAndMeetsTheTouchTarget() {
        var clicks = 0
        compose.setContent { SmishingTheme { PrimaryButton("Siguiente", { clicks++ }) } }

        compose.onNodeWithText("Siguiente").assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun disabledPrimaryButtonIsNotEnabled() {
        compose.setContent { SmishingTheme { PrimaryButton("Siguiente", {}, enabled = false) } }
        compose.onNodeWithText("Siguiente").assertIsNotEnabled()
    }

    @Test
    fun secondaryAndLinkButtonsMeetTheTouchTarget() {
        var skipped = false
        compose.setContent {
            SmishingTheme {
                SecondaryButton("Este mensaje es seguro", {})
                LinkButton("Omitir por ahora", { skipped = true })
            }
        }

        compose.onNodeWithText("Este mensaje es seguro").assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText("Omitir por ahora").assertHeightIsAtLeast(48.dp).performClick()
        assertTrue(skipped)
    }

    @Test
    fun statusChipsShowTheirLabel() {
        compose.setContent {
            SmishingTheme {
                StatusChip(Status.Danger, "Peligro")
                StatusChip(Status.Suspicious, "Sospechoso")
                StatusChip(Status.Safe, "Seguro")
            }
        }
        listOf("Peligro", "Sospechoso", "Seguro").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test
    fun radioOptionReportsSelectionAndMeetsTheTouchTarget() {
        var selected by mutableStateOf(false)
        compose.setContent {
            SmishingTheme {
                RadioOption("Solo avisarme", "No tomaremos ninguna acción", selected, { selected = true })
            }
        }

        compose.onNodeWithText("Solo avisarme").assertHeightIsAtLeast(48.dp)
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).assertIsEnabled()
        compose.onNodeWithText("Solo avisarme").performClick()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).assertIsSelected()
    }

    @Test
    fun switchRowTogglesFromAnywhereOnTheRow() {
        var checked by mutableStateOf(false)
        compose.setContent {
            SmishingTheme { SwitchRow("Compartir reportes", "De forma anónima", checked, { checked = it }) }
        }

        val switch = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        switch.assertIsOff()
        compose.onNodeWithText("Compartir reportes").performClick()
        switch.assertIsOn()
        assertTrue(checked)
    }

    @Test
    fun bottomBarSelectsTheTappedTab() {
        var selected by mutableStateOf(0)
        compose.setContent {
            SmishingTheme {
                SmishingBottomBar(
                    items = listOf(NavItem("Inicio", Icons.Outlined.Home), NavItem("Ajustes", Icons.Outlined.Settings)),
                    selectedIndex = selected,
                    onSelect = { selected = it },
                )
            }
        }

        compose.onNodeWithText("Ajustes").performClick()
        assertEquals(1, selected)
    }

    @Test
    fun messageInputBarSendsWhatWasTyped() {
        var text by mutableStateOf("")
        var sent = ""
        compose.setContent {
            SmishingTheme {
                MessageInputBar(text, { text = it }, { sent = text }, "Escribir mensaje...", "Enviar")
            }
        }

        compose.onNodeWithText("Escribir mensaje...").performTextInput("Hola")
        compose.onNode(hasContentDescription("Enviar")).assertWidthIsAtLeast(48.dp).performClick()
        assertEquals("Hola", sent)
    }

    @Test
    fun conversationRowIsClickableAndShowsItsWarning() {
        var opened = false
        compose.setContent {
            SmishingTheme {
                ConversationRow("987 654 321", "Ayer", "Su cuenta será bloqueada", { opened = true }, warning = "Cuidado: Posible estafa")
            }
        }

        compose.onNodeWithText("Cuidado: Posible estafa").assertIsDisplayed()
        compose.onNodeWithText("987 654 321").performClick()
        assertTrue(opened)
    }

    @Test
    fun alertBannerActionIsClickable() {
        var risk = false
        compose.setContent {
            SmishingTheme { AlertBanner("Parece una estafa", "No abra el enlace", "Ver riesgo", { risk = true }) }
        }

        compose.onNodeWithText("Ver riesgo").assertHeightIsAtLeast(48.dp).performClick()
        assertTrue(risk)
    }

    @Test
    fun confirmDialogCallsConfirmAndDismiss() {
        var confirmed = false
        var dismissed = false
        compose.setContent {
            SmishingTheme {
                ConfirmDialog("¿Está seguro?", "Esto se puede revertir", "Sí, es seguro", { confirmed = true }, "Cancelar", { dismissed = true })
            }
        }

        compose.onNodeWithText("¿Está seguro?").assertIsDisplayed()
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithText("Sí, es seguro").performClick()
        assertTrue(dismissed)
        assertTrue(confirmed)
    }

    @Test
    fun protectionCardOffersTheActionOnlyWhenGiven() {
        var activated = false
        compose.setContent {
            SmishingTheme {
                ProtectionCard(false, "Protección apagada", "Su teléfono está desprotegido", actionLabel = "Activar protección", onAction = { activated = true })
            }
        }

        compose.onNodeWithText("Activar protección").assertHeightIsAtLeast(48.dp).performClick()
        assertTrue(activated)
    }

    @Test
    fun permissionRowsShowTheirLabel() {
        compose.setContent {
            SmishingTheme {
                PermissionRow("Permiso para leer mensajes", granted = true)
                PermissionRow("Permiso sin conceder", granted = false)
            }
        }
        compose.onNodeWithText("Permiso para leer mensajes").assertIsDisplayed()
        compose.onNodeWithText("Permiso sin conceder").assertIsDisplayed()
    }
}
