package com.app.smishing.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.app.smishing.R
import com.app.smishing.domain.history.WeeklySummary
import com.app.smishing.domain.settings.PermissionStatus
import com.app.smishing.domain.settings.ProtectionStatus
import com.app.smishing.ui.designsystem.components.DayPart
import com.app.smishing.ui.designsystem.components.ProtectionCard
import com.app.smishing.ui.designsystem.components.SmishingTopBar
import com.app.smishing.ui.designsystem.components.SummaryCard
import com.app.smishing.ui.designsystem.components.SummaryRow
import com.app.smishing.ui.designsystem.components.TopBarStyle
import com.app.smishing.ui.designsystem.icons.SmishingIcons
import com.app.smishing.ui.designsystem.theme.SmishingPalette
import com.app.smishing.ui.designsystem.theme.Spacing
import java.time.LocalTime

/**
 * "Inicio" (#17), con el diseño: si la protección está activa y el resumen de la semana. Qué falta (rol, permisos, notificaciones)
 * se ve en Ajustes, en "Permisos".
 *
 * **Solo dice "Protección activa" si se cumplen todos los requisitos** ([ProtectionStatus.active]); si falta alguno, dice "Protección
 * apagada", cuál falta y ofrece [onActivate]. [statuses] es `null` mientras se leen; mientras tanto no se afirma nada. El resumen
 * ([summary], `null` mientras se lee) se atenúa si la protección está apagada.
 */
@Composable
fun HomeScreen(
    statuses: List<PermissionStatus>?,
    summary: WeeklySummary?,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val protection = statuses?.let { ProtectionStatus.of(it) }
    // El fondo de la tarjeta activa cambia con la hora del teléfono; se calcula al abrir la pantalla.
    val dayPart = remember { DayPart.at(LocalTime.now()) }
    Column(modifier = modifier.fillMaxSize().background(SmishingPalette.Background)) {
        SmishingTopBar(title = stringResource(R.string.app_name), style = TopBarStyle.Primary)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.Md),
            verticalArrangement = Arrangement.spacedBy(Spacing.Md),
        ) {
            when {
                protection == null -> Unit
                protection.active -> ProtectionCard(
                    active = true,
                    title = stringResource(R.string.home_active_title),
                    message = stringResource(R.string.home_active_message),
                    dayPart = dayPart,
                )
                else -> ProtectionCard(
                    active = false,
                    title = stringResource(R.string.protection_off_title),
                    message = stringResource(R.string.protection_off_message),
                    actionLabel = stringResource(R.string.protection_activate),
                    onAction = onActivate,
                )
            }
            Summary(summary, dimmed = protection?.active != true)
        }
    }
}

@Composable
private fun Summary(summary: WeeklySummary?, dimmed: Boolean) {
    SummaryCard(title = stringResource(R.string.home_summary_title), dimmed = dimmed) {
        SummaryRow(Icons.Outlined.Email, stringResource(R.string.home_summary_reviewed), summary?.reviewed?.toString() ?: "--")
        SummaryRow(
            SmishingIcons.ErrorOutline,
            stringResource(R.string.home_summary_scams),
            summary?.let { stringResource(R.string.home_summary_scams_value, it.possibleScams) } ?: "--",
        )
    }
}
