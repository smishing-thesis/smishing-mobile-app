package com.app.smishing.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.app.smishing.R
import com.app.smishing.domain.history.WeeklySummary
import com.app.smishing.domain.settings.AppPermission
import com.app.smishing.domain.settings.PermissionStatus
import com.app.smishing.domain.settings.ProtectionStatus
import com.app.smishing.ui.designsystem.components.PermissionRow
import com.app.smishing.ui.designsystem.components.ProtectionCard
import com.app.smishing.ui.designsystem.components.SectionLabel
import com.app.smishing.ui.designsystem.components.SummaryCard
import com.app.smishing.ui.designsystem.components.SummaryRow
import com.app.smishing.ui.designsystem.icons.SmishingIcons
import com.app.smishing.ui.designsystem.theme.SmishingPalette
import com.app.smishing.ui.designsystem.theme.SmishingTypography
import com.app.smishing.ui.designsystem.theme.Spacing

/**
 * "Inicio" (#17), con el diseño: si la protección está activa, lo que necesita (rol, permisos y notificaciones, cada uno con su
 * estado) y el resumen de la semana.
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
    Column(
        modifier = modifier.fillMaxSize().background(SmishingPalette.Background).verticalScroll(rememberScrollState()).padding(Spacing.Md),
        verticalArrangement = Arrangement.spacedBy(Spacing.Md),
    ) {
        Header(protection)
        when {
            protection == null -> Unit
            protection.active -> ProtectionCard(
                active = true,
                title = stringResource(R.string.home_active_title),
                message = stringResource(R.string.home_active_message),
            )
            else -> ProtectionCard(
                active = false,
                title = stringResource(R.string.protection_off_title),
                message = stringResource(R.string.protection_off_message),
                actionLabel = stringResource(R.string.protection_activate),
                onAction = onActivate,
            )
        }
        if (statuses != null) Requirements(statuses)
        Summary(summary, dimmed = protection?.active != true)
    }
}

@Composable
private fun Header(protection: ProtectionStatus?) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.app_name), style = SmishingTypography.TitleMedium, color = SmishingPalette.TextPrimary)
        if (protection != null) {
            val active = protection.active
            val tint = if (active) SmishingPalette.TextPrimary else SmishingPalette.TextSecondary
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.X2s)) {
                Icon(
                    if (active) SmishingIcons.Shield else Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    stringResource(if (active) R.string.home_status_active else R.string.home_status_inactive),
                    style = SmishingTypography.BodyMediumBold,
                    color = tint,
                )
            }
        }
    }
}

/** Los requisitos de la protección, cada uno con su estado: lo que se ve aquí es lo que se comprueba para decir "activa". */
@Composable
private fun Requirements(statuses: List<PermissionStatus>) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.X2s)) {
        SectionLabel(stringResource(R.string.home_requirements_title))
        val granted = stringResource(R.string.settings_state_granted)
        val denied = stringResource(R.string.settings_state_denied)
        for (permission in ProtectionStatus.REQUIRED) {
            val ok = statuses.any { it.permission == permission && it.granted }
            PermissionRow(
                label = stringResource(labelOf(permission)),
                granted = ok,
                modifier = Modifier.semantics { stateDescription = if (ok) granted else denied },
            )
        }
    }
}

@Composable
private fun Summary(summary: WeeklySummary?, dimmed: Boolean) {
    SummaryCard(title = stringResource(R.string.home_summary_title), dimmed = dimmed) {
        SummaryRow(Icons.Outlined.Email, stringResource(R.string.home_summary_reviewed), summary?.reviewed?.toString() ?: "--")
        SummaryRow(SmishingIcons.ErrorOutline, stringResource(R.string.home_summary_scams), summary?.possibleScams?.toString() ?: "--")
        Text(stringResource(R.string.home_summary_note), style = SmishingTypography.Note, color = SmishingPalette.TextSecondary)
    }
}

private fun labelOf(permission: AppPermission) = when (permission) {
    AppPermission.DefaultSmsApp -> R.string.settings_permission_default_sms
    AppPermission.ReadSms -> R.string.settings_permission_read_sms
    AppPermission.SendSms -> R.string.settings_permission_send_sms
    AppPermission.Notifications -> R.string.settings_permission_notifications
}
