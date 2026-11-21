package com.app.smishing.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.app.smishing.demo.DemoLoader
import com.app.smishing.demo.DemoMessage
import com.app.smishing.ui.designsystem.components.PrimaryButton
import com.app.smishing.ui.designsystem.components.SectionLabel
import com.app.smishing.ui.designsystem.components.SecondaryButton
import com.app.smishing.ui.designsystem.theme.SmishingPalette
import com.app.smishing.ui.designsystem.theme.SmishingTypography
import com.app.smishing.ui.designsystem.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Demo" (#65), solo en la variante `debug`: cada vez que se toca "Recibir un mensaje de estafa" (o "legítimo") llega **un** mensaje del
 * archivo de demostración, por el flujo real, para ver su llegada y su aviso; "Borrar mensajes de demo" los quita. En `qa` y `release` esta función no hace nada.
 */
@Composable
fun DemoSection() {
    val context = LocalContext.current
    val loader = remember { DemoLoader(context) }
    val scope = rememberCoroutineScope()
    var file by remember { mutableStateOf<Result<List<DemoMessage>>?>(null) }
    var loaded by remember { mutableIntStateOf(0) }
    var hasContacts by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    // Solo para errores: cuando todo sale bien, la llegada del mensaje y su aviso son la respuesta.
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            file = loader.read()
            loaded = loader.loaded()
            hasContacts = loader.hasSampleContacts()
        }
    }

    fun run(action: () -> Unit) {
        busy = true
        error = null
        scope.launch(Dispatchers.IO) {
            try {
                action()
            } catch (e: Exception) {
                // Solo el tipo y el motivo propio: nunca el texto de un mensaje.
                error = if (e is IllegalStateException) e.message.orEmpty() else "No se pudo: ${e.javaClass.simpleName}"
            }
            loaded = loader.loaded()
            hasContacts = loader.hasSampleContacts()
            busy = false
        }
    }

    // Agregar contactos necesita WRITE_CONTACTS (solo debug): se pide al tocar el botón y, si se concede, se agregan.
    val requestWriteContacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) run { loader.addSampleContacts() } else error = "Sin permiso para escribir contactos"
    }

    HorizontalDivider(color = SmishingPalette.Border, modifier = Modifier.padding(vertical = Spacing.Xs))
    SectionLabel("Demo")
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
        val read = file
        val messages = read?.getOrNull()
        // Solo se explica cuando algo falta: con el archivo listo no hay nada que decir.
        if (read != null && messages == null) {
            Text(
                "${read.exceptionOrNull()?.message}. Cópielo a ${loader.file().path} con adb push.",
                style = SmishingTypography.Note,
                color = SmishingPalette.TextSecondary,
            )
        }
        if (messages != null) {
            PrimaryButton(
                "Recibir un mensaje de estafa",
                onClick = { run { loader.deliverNext(messages, "smishing") } },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
            SecondaryButton(
                "Recibir un mensaje legítimo",
                onClick = { run { loader.deliverNext(messages, "legitimo") } },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SecondaryButton(
            "Agregar contactos de ejemplo",
            onClick = { requestWriteContacts.launch(Manifest.permission.WRITE_CONTACTS) },
            enabled = !busy && !hasContacts,
            modifier = Modifier.fillMaxWidth(),
        )
        SecondaryButton(
            "Borrar mensajes de demo ($loaded)",
            onClick = { run { loader.deleteAll() } },
            enabled = !busy && loaded > 0,
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, style = SmishingTypography.Note, color = SmishingPalette.TextPrimary) }
    }
}
