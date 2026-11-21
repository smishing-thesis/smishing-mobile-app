package com.app.smishing.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.app.smishing.R
import com.app.smishing.domain.mitigation.MitigationMode
import com.app.smishing.domain.settings.AppPermission
import com.app.smishing.domain.settings.PermissionStatus
import com.app.smishing.domain.settings.ProtectionStatus
import com.app.smishing.ui.designsystem.components.LinkButton
import com.app.smishing.ui.designsystem.components.PermissionRow
import com.app.smishing.ui.designsystem.components.RadioOption
import com.app.smishing.ui.designsystem.components.SectionLabel
import com.app.smishing.ui.designsystem.components.SwitchRow
import com.app.smishing.ui.designsystem.components.SecondaryButton
import com.app.smishing.ui.designsystem.components.SmishingTopBar
import com.app.smishing.ui.designsystem.components.TopBarStyle
import com.app.smishing.ui.designsystem.theme.SmishingPalette
import com.app.smishing.ui.designsystem.theme.SmishingTypography
import com.app.smishing.ui.designsystem.theme.Spacing

/**
 * "Ajustes" (#13, #25, #31), con el diseño: el modo de mitigación y el estado de cada permiso.
 *
 * - Cada permiso se muestra concedido o no; si no, dice **qué función queda limitada** y ofrece abrir los ajustes del sistema.
 * - [mode] es `null` mientras se lee.
 *
 * Todavía no tiene "Compartir reportes de estafas" (el aporte al corpus es de la fase 2: #18, #26 y #27), por eso #31 sigue abierto.
 */
@Composable
fun SettingsScreen(
    mode: MitigationMode?,
    onModeChange: (MitigationMode) -> Unit,
    permissions: List<PermissionStatus>,
    onOpenSystemSettings: (AppPermission) -> Unit,
    modifier: Modifier = Modifier,
    /** Si el aporte anónimo al corpus está activado (#18), o `null` mientras se lee, y el cambio que pide la persona. */
    contribution: Boolean? = null,
    onContributionChange: (Boolean) -> Unit = {},
    /** Pedir el permiso de contactos (#53). Si ya se negó, abre los ajustes de la app. */
    onContacts: (granted: Boolean) -> Unit = {},
    /** "Borrar todo el historial" (#52): pedir confirmación. */
    onDeleteHistory: () -> Unit = {},
    /** Al final de la pantalla: en depuración, las herramientas de demostración (#65); en `qa` y `release`, nada. */
    extra: @Composable () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize().background(SmishingPalette.Background)) {
        SmishingTopBar(title = stringResource(R.string.settings_title), style = TopBarStyle.Primary)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.Md),
            verticalArrangement = Arrangement.spacedBy(Spacing.Sm),
        ) {
            SectionLabel(stringResource(R.string.settings_mode_title))
            for (option in MitigationMode.entries) {
                RadioOption(
                    title = stringResource(titleOf(option)),
                    description = stringResource(descriptionOf(option)),
                    selected = mode == option,
                    onSelect = { onModeChange(option) },
                )
            }

            HorizontalDivider(color = SmishingPalette.Border, modifier = Modifier.padding(vertical = Spacing.Xs))
            SectionLabel(stringResource(R.string.settings_permissions_title))
            val granted = stringResource(R.string.settings_state_granted)
            val denied = stringResource(R.string.settings_state_denied)
            // El mockup muestra la aplicación de mensajería y el permiso de lectura; se suman las notificaciones porque también son un
            // requisito de la protección. Enviar mensajes no se muestra: no afecta a la detección.
            for (status in permissions.filter { it.permission in ProtectionStatus.REQUIRED }) {
                PermissionRow(
                    label = stringResource(labelOf(status.permission)),
                    granted = status.granted,
                    modifier = Modifier.semantics { stateDescription = if (status.granted) granted else denied },
                )
                if (!status.granted) {
                    Text(
                        stringResource(limitedOf(status.permission)),
                        style = SmishingTypography.Note,
                        color = SmishingPalette.TextSecondary,
                        modifier = Modifier.padding(start = 32.dp),
                    )
                    LinkButton(
                        text = stringResource(R.string.settings_open_system),
                        onClick = { onOpenSystemSettings(status.permission) },
                        modifier = Modifier.padding(start = 24.dp),
                    )
                }
            }

            // Los contactos son opcionales (#53): no cuentan para la protección, así que van aparte de los permisos obligatorios.
            permissions.firstOrNull { it.permission == AppPermission.Contacts }?.let { contacts ->
                PermissionRow(
                    label = stringResource(R.string.settings_permission_contacts),
                    granted = contacts.granted,
                    modifier = Modifier.semantics { stateDescription = if (contacts.granted) granted else denied },
                )
                if (!contacts.granted) {
                    Text(
                        stringResource(R.string.settings_limited_contacts),
                        style = SmishingTypography.Note,
                        color = SmishingPalette.TextSecondary,
                        modifier = Modifier.padding(start = 32.dp),
                    )
                    LinkButton(
                        text = stringResource(R.string.settings_contacts_allow),
                        onClick = { onContacts(false) },
                        modifier = Modifier.padding(start = 24.dp),
                    )
                }
            }

            HorizontalDivider(color = SmishingPalette.Border, modifier = Modifier.padding(vertical = Spacing.Xs))
            SectionLabel(stringResource(R.string.settings_privacy_title))
            Text(
                stringResource(R.string.settings_privacy_text),
                style = SmishingTypography.BodyMedium,
                color = SmishingPalette.TextSecondary,
            )
            SecondaryButton(stringResource(R.string.settings_delete_history), onClick = onDeleteHistory, modifier = Modifier.fillMaxWidth())
            if (contribution != null) {
                SwitchRow(
                    title = stringResource(R.string.settings_contribution_title),
                    description = null,
                    checked = contribution,
                    onCheckedChange = onContributionChange,
                )
            }
            extra()
        }
    }
}

private fun titleOf(mode: MitigationMode) = when (mode) {
    MitigationMode.Automatic -> R.string.settings_mode_automatic_title
    MitigationMode.Ask -> R.string.settings_mode_ask_title
}

private fun descriptionOf(mode: MitigationMode) = when (mode) {
    MitigationMode.Automatic -> R.string.settings_mode_automatic_description
    MitigationMode.Ask -> R.string.settings_mode_ask_description
}

private fun labelOf(permission: AppPermission) = when (permission) {
    AppPermission.DefaultSmsApp -> R.string.settings_permission_default_sms
    AppPermission.ReadSms -> R.string.settings_permission_read_sms
    // Enviar mensajes no se muestra (no es un requisito de la protección ni está en el mockup).
    AppPermission.SendSms -> R.string.settings_permission_default_sms
    AppPermission.Notifications -> R.string.settings_permission_notifications
    AppPermission.Contacts -> R.string.settings_permission_contacts
}

private fun limitedOf(permission: AppPermission) = when (permission) {
    AppPermission.DefaultSmsApp -> R.string.settings_limited_default_sms
    AppPermission.ReadSms -> R.string.settings_limited_read_sms
    AppPermission.SendSms -> R.string.settings_limited_default_sms
    AppPermission.Notifications -> R.string.settings_limited_notifications
    AppPermission.Contacts -> R.string.settings_limited_contacts
}
