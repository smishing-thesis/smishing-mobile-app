package com.app.smishing.ui.designsystem

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.app.smishing.ui.designsystem.components.ActionDoneRow
import com.app.smishing.ui.designsystem.components.AlertBanner
import com.app.smishing.ui.designsystem.components.ConfirmDialog
import com.app.smishing.ui.designsystem.components.ConversationRow
import com.app.smishing.ui.designsystem.components.InfoPanel
import com.app.smishing.ui.designsystem.components.LinkButton
import com.app.smishing.ui.designsystem.components.MessageBubble
import com.app.smishing.ui.designsystem.components.MessageInputBar
import com.app.smishing.ui.designsystem.components.NavItem
import com.app.smishing.ui.designsystem.components.OnboardingStepLayout
import com.app.smishing.ui.designsystem.components.OriginalMessageBox
import com.app.smishing.ui.designsystem.components.PermissionRow
import com.app.smishing.ui.designsystem.components.PrimaryButton
import com.app.smishing.ui.designsystem.components.DayPart
import com.app.smishing.ui.designsystem.components.ProtectionCard
import com.app.smishing.ui.designsystem.components.RadioOption
import com.app.smishing.ui.designsystem.components.RiskReasonRow
import com.app.smishing.ui.designsystem.components.SearchAction
import com.app.smishing.ui.designsystem.components.SecondaryButton
import com.app.smishing.ui.designsystem.components.SectionLabel
import com.app.smishing.ui.designsystem.components.SmishingBottomBar
import com.app.smishing.ui.designsystem.components.SmishingDialog
import com.app.smishing.ui.designsystem.components.SmishingExtendedFab
import com.app.smishing.ui.designsystem.components.SmishingTextField
import com.app.smishing.ui.designsystem.components.SmishingTopBar
import com.app.smishing.ui.designsystem.components.StatusBanner
import com.app.smishing.ui.designsystem.components.StatusChip
import com.app.smishing.ui.designsystem.components.StepIndicator
import com.app.smishing.ui.designsystem.components.SummaryCard
import com.app.smishing.ui.designsystem.components.SummaryRow
import com.app.smishing.ui.designsystem.components.SwitchRow
import com.app.smishing.ui.designsystem.components.TopBarStyle
import com.app.smishing.ui.designsystem.icons.SmishingIcons
import com.app.smishing.ui.designsystem.theme.SmishingPalette
import com.app.smishing.ui.designsystem.theme.SmishingShapes
import com.app.smishing.ui.designsystem.theme.SmishingTheme
import com.app.smishing.ui.designsystem.theme.SmishingTypography
import com.app.smishing.ui.designsystem.theme.Spacing
import com.app.smishing.ui.designsystem.theme.Status

/**
 * Galería de los componentes (solo debug). Sin extras muestra todas las secciones; con `--ei section N` muestra
 * una sola, útil para capturarla y compararla con el diseño. Los textos son de ejemplo.
 */
class ComponentGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val only = intent.getIntExtra(EXTRA_SECTION, -1).takeIf { it >= 0 }
        setContent { SmishingTheme { ComponentGallery(only) } }
    }

    companion object {
        const val EXTRA_SECTION = "section"
    }
}

private class Section(val name: String, val content: @Composable () -> Unit)

@Composable
private fun sections(): List<Section> = listOf(
    Section("Colores y tipografía") { TokensSection() },
    Section("Botones y chips") { ButtonsSection() },
    Section("Inicio") { HomeSection() },
    Section("Mensajes") { MessagesSection() },
    Section("Conversación") { ConversationSection() },
    Section("Detalle del análisis") { AnalysisSection() },
    Section("Ajustes") { SettingsSection() },
    Section("Navegación y diálogos") { NavigationSection() },
    Section("Onboarding") { OnboardingSection() },
)

@Preview(showBackground = true, heightDp = 1600)
@Composable
fun ComponentGallery(onlySection: Int? = null) {
    val all = sections()
    val shown = onlySection?.let { listOf(all[it]) } ?: all
    Column(
        modifier = Modifier.fillMaxSize().background(SmishingPalette.Background).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.X2l),
    ) {
        shown.forEach { section ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
                Text(
                    section.name,
                    style = SmishingTypography.TitleSmall,
                    color = SmishingPalette.PrimaryDark,
                    modifier = Modifier.padding(horizontal = Spacing.Md, vertical = Spacing.Xs),
                )
                section.content()
            }
        }
    }
}

@Composable
private fun Padded(content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = Spacing.Md), verticalArrangement = Arrangement.spacedBy(Spacing.Sm)) { content() }
}

@Composable
private fun Swatch(name: String, color: Color) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
        Box(Modifier.size(32.dp).background(color, SmishingShapes.Md).border(1.dp, SmishingPalette.Border, SmishingShapes.Md))
        Text(name, style = SmishingTypography.Note, color = SmishingPalette.TextPrimary)
    }
}

@Composable
private fun TokensSection() = Padded {
    val colors = listOf(
        "Primario" to SmishingPalette.Primary, "Primario oscuro" to SmishingPalette.PrimaryDark,
        "Primario claro" to SmishingPalette.PrimaryLight, "Primario superficial" to SmishingPalette.PrimarySurface,
        "Fondo secundario" to SmishingPalette.BackgroundSecondary, "Texto principal" to SmishingPalette.TextPrimary,
        "Texto secundario" to SmishingPalette.TextSecondary, "Borde" to SmishingPalette.Border,
        "Seguro" to SmishingPalette.Safe, "Seguro fondo" to SmishingPalette.SafeBackground,
        "Sospechoso" to SmishingPalette.Suspicious, "Sospechoso fondo" to SmishingPalette.SuspiciousBackground,
        "Peligroso" to SmishingPalette.Danger, "Peligroso fondo" to SmishingPalette.DangerBackground,
    )
    colors.forEach { (name, color) -> Swatch(name, color) }
    val styles: List<Pair<String, TextStyle>> = listOf(
        "Título/Grande" to SmishingTypography.TitleLarge, "Título/Mediano" to SmishingTypography.TitleMedium,
        "Título/Pequeño" to SmishingTypography.TitleSmall, "Cuerpo/Grande" to SmishingTypography.BodyLarge,
        "Cuerpo/Grande Negrita" to SmishingTypography.BodyLargeBold, "Cuerpo/Mediano" to SmishingTypography.BodyMedium,
        "Cuerpo/Mediano Negrita" to SmishingTypography.BodyMediumBold, "Etiqueta/Grande" to SmishingTypography.LabelLarge,
        "Etiqueta/Mediana" to SmishingTypography.LabelMedium, "Etiqueta/Pequeña" to SmishingTypography.LabelSmall,
        "Nota/Regular" to SmishingTypography.Note, "Nota/Negrita" to SmishingTypography.NoteBold,
        "Micro" to SmishingTypography.Micro,
    )
    styles.forEach { (name, style) -> Text("$name: Su teléfono está protegido", style = style, color = SmishingPalette.TextPrimary) }
}

@Composable
private fun ButtonsSection() = Padded {
    PrimaryButton("Siguiente", {}, Modifier.fillMaxWidth())
    PrimaryButton("Desactivado", {}, Modifier.fillMaxWidth(), enabled = false)
    SecondaryButton("Este mensaje es seguro", {}, Modifier.fillMaxWidth())
    LinkButton("Omitir por ahora", {})
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
        StatusChip(Status.Danger, "Peligro")
        StatusChip(Status.Suspicious, "Sospechoso")
        StatusChip(Status.Safe, "Seguro")
    }
}

@Composable
private fun HomeSection() = Padded {
    // Una tarjeta activa por momento del día, para ver todos los fondos sin esperar a que cambie la hora.
    DayPart.entries.forEach { part ->
        ProtectionCard(true, "Protección activa", "La aplicación está protegiendo sus mensajes en este momento.", dayPart = part)
    }
    SummaryCard("Resumen de la semana") {
        SummaryRow(Icons.Outlined.Email, "Mensajes revisados:", "47")
        SummaryRow(SmishingIcons.ErrorOutline, "Posibles estafas:", "2 detectadas")
    }
    ProtectionCard(
        active = false,
        title = "Protección apagada",
        message = "Su teléfono está desprotegido contra mensajes maliciosos.",
        actionLabel = "Activar protección",
        onAction = {},
    )
    SummaryCard("Resumen de la semana", dimmed = true) {
        SummaryRow(Icons.Outlined.Email, "Mensajes revisados:", "0")
        SummaryRow(SmishingIcons.ErrorOutline, "Posibles estafas:", "--")
    }
}

@Composable
private fun MessagesSection() {
    SmishingTopBar("Mensajes", style = TopBarStyle.Plain, actions = { SearchAction("Buscar", {}) })
    Column {
        ConversationRow("987 654 321", "Ayer", "Su cuenta será bloqueada hoy. Actualice sus datos...", {}, warning = "Cuidado: Posible estafa")
        ConversationRow("Mamá", "14:20", "Ya llegué a casa, la cena está lista", {})
        ConversationRow("Carlos", "Marzo 12", "Nos vemos el lunes en la oficina", {})
    }
    Padded { SmishingExtendedFab("Escribir", {}) }
}

@Composable
private fun ConversationSection() {
    SmishingTopBar("987 654 321", style = TopBarStyle.Plain, subtitle = "SMS", onBack = {}, backDescription = "Volver")
    Padded {
        AlertBanner(
            title = "Este mensaje parece una estafa",
            message = "No abra el enlace ni comparta datos.",
            actionLabel = "Ver riesgo",
            onAction = {},
        )
        MessageBubble("Estimado cliente, su tarjeta ha sido suspendida. Ingrese a bancoseguro-pe.info para reactivarla.", outgoing = false)
        MessageBubble("¿Por qué me mandan esto?", outgoing = true)
    }
    var text by remember { mutableStateOf("") }
    MessageInputBar(text, { text = it }, {}, "Escribir mensaje...", "Enviar")
}

@Composable
private fun AnalysisSection() {
    SmishingTopBar("Detalle del análisis", onBack = {}, backDescription = "Volver")
    Padded {
        StatusBanner(Status.Danger, "Mensaje Peligroso")
        SectionLabel("Mensaje original")
        OriginalMessageBox("Estimado cliente, su tarjeta ha sido suspendida. Ingrese a bancoseguro-pe.info para reactivarla.")
        SectionLabel("¿Por qué es riesgoso?")
        RiskReasonRow(SmishingIcons.Schedule, "Le pide actuar con urgencia")
        RiskReasonRow(SmishingIcons.Institution, "Finge ser de una entidad")
        RiskReasonRow(SmishingIcons.Link, "Contiene un enlace")
        RiskReasonRow(SmishingIcons.Key, "Le pide sus datos o claves")
        SectionLabel("Acciones realizadas (automático)")
        ActionDoneRow("Bloqueamos este número")
        ActionDoneRow("Quitamos el mensaje de su bandeja")
        InfoPanel("Recomendación", "No abra el enlace. Si tiene dudas, contacte directamente a la entidad.")
        SecondaryButton("Este mensaje es seguro", {}, Modifier.fillMaxWidth())
    }
}

@Composable
private fun SettingsSection() {
    SmishingTopBar("Ajustes")
    var mode by remember { mutableIntStateOf(0) }
    var share by remember { mutableStateOf(true) }
    Padded {
        SectionLabel("Modo de protección")
        RadioOption("Protégeme automáticamente", "Bloqueamos el número y quitamos el mensaje de su buzón", mode == 0, { mode = 0 })
        RadioOption("Preguntarme antes de actuar", "Le mostraremos las opciones para que usted decida", mode == 1, { mode = 1 })
        SectionLabel("Ayudar a mejorar la aplicación")
        SwitchRow("Compartir reportes de estafas", "Enviar mensajes analizados de forma anónima, sin datos personales.", share, { share = it })
        SectionLabel("Permisos")
        PermissionRow("Aplicación de mensajería principal", granted = true)
        PermissionRow("Permiso para leer mensajes", granted = true)
        PermissionRow("Permiso sin conceder", granted = false)
    }
}

@Composable
private fun NavigationSection() {
    var selected by remember { mutableIntStateOf(0) }
    SmishingBottomBar(
        items = listOf(
            NavItem("Inicio", Icons.Outlined.Home),
            NavItem("Mensajes", SmishingIcons.Chat),
            NavItem("Historial", SmishingIcons.History),
            NavItem("Ajustes", Icons.Outlined.Settings),
        ),
        selectedIndex = selected,
        onSelect = { selected = it },
    )
    var showConfirm by remember { mutableStateOf(false) }
    var showCustom by remember { mutableStateOf(false) }
    Padded {
        SecondaryButton("Abrir «¿Está seguro?»", { showConfirm = true }, Modifier.fillMaxWidth())
        SecondaryButton("Abrir «Compartir datos»", { showCustom = true }, Modifier.fillMaxWidth())
        SmishingTextField("", {}, "Escriba su mensaje aquí...", Modifier.fillMaxWidth(), minLines = 4)
    }
    if (showConfirm) {
        ConfirmDialog(
            title = "¿Está seguro?",
            message = "Si marca este mensaje como seguro, desbloquearé al número y devolveré el mensaje a su buzón.",
            confirmLabel = "Sí, es seguro",
            onConfirm = { showConfirm = false },
            dismissLabel = "Cancelar",
            onDismiss = { showConfirm = false },
        )
    }
    if (showCustom) {
        SmishingDialog(
            title = "Compartir datos de forma anónima",
            confirmLabel = "Aceptar",
            onConfirm = { showCustom = false },
            dismissLabel = "Denegar",
            onDismiss = { showCustom = false },
        ) {
            Text("Cuando detectamos un mensaje sospechoso, guardamos solo el texto de ese mensaje.", style = SmishingTypography.Note)
            Text("Lo que nunca compartimos:", style = SmishingTypography.NoteBold)
            Text("• Su nombre\n• Su número de teléfono\n• Sus contactos", style = SmishingTypography.Note)
        }
    }
}

@Composable
private fun OnboardingSection() {
    var step by remember { mutableIntStateOf(1) }
    Padded {
        StepIndicator(stepIndex = step, stepCount = 3)
        Text("Pantalla completa del paso 2:", style = SmishingTypography.Note)
    }
    // El paso es de pantalla completa: dentro de una lista que se desplaza necesita una altura fija.
    Box(Modifier.fillMaxWidth().height(560.dp).padding(horizontal = Spacing.Md).border(1.dp, SmishingPalette.Border)) {
        OnboardingStepLayout(
            stepIndex = 1,
            stepCount = 2,
            stepLabel = "PASO 2 DE 2",
            title = "Aplicación principal",
            primaryLabel = "Usar como aplicación por defecto",
            onPrimary = {},
            skipLabel = "Omitir por ahora",
            onSkip = { step = (step + 1) % 3 },
        ) {
            Text("Para poder filtrar las estafas, esta aplicación necesita reemplazar a su aplicación de mensajería actual.", style = SmishingTypography.BodyLarge)
        }
    }
}
