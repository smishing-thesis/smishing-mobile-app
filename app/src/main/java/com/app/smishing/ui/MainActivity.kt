package com.app.smishing.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.app.smishing.R
import com.app.smishing.data.sms.AndroidSmsPermissionRepository
import com.app.smishing.data.sms.AndroidSmsRoleRepository
import com.app.smishing.domain.sms.SmsPermissionState
import com.app.smishing.domain.sms.SmsRoleState
import com.app.smishing.ui.designsystem.components.ProtectionCard
import com.app.smishing.ui.designsystem.theme.SmishingPalette
import com.app.smishing.ui.designsystem.theme.SmishingTheme
import com.app.smishing.ui.designsystem.theme.Spacing
import com.app.smishing.ui.messages.MessagesRoute
import com.app.smishing.ui.onboarding.DefaultSmsRoleScreen
import com.app.smishing.ui.onboarding.SmsPermissionScreen

/**
 * Pantalla de entrada. El onboarding de SMS: el paso 2 pide el rol de SMS (#2) y el paso 3, los permisos de lectura
 * (#3), en ese orden. Cuando se tienen los dos, muestra los mensajes (#5). Cada vez que se vuelve a la app se comprueba de
 * nuevo si se conservan. El paso 1 y la pantalla de inicio llegan con #17.
 */
class MainActivity : ComponentActivity() {

    private lateinit var roles: AndroidSmsRoleRepository
    private lateinit var permissions: AndroidSmsPermissionRepository
    private var roleState by mutableStateOf(SmsRoleState.NotHeld)
    private var permissionState by mutableStateOf(SmsPermissionState.NotGranted)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        roles = AndroidSmsRoleRepository(this)
        permissions = AndroidSmsPermissionRepository(this)
        refresh()

        setContent {
            SmishingTheme {
                var skipped by rememberSaveable { mutableStateOf(false) }
                var blocked by rememberSaveable { mutableStateOf(false) }

                val requestRole = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                    refresh()
                }
                val requestPermissions =
                    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                        refresh()
                        // Si sigue faltando alguno y Android ya no muestra el diálogo, solo queda ir a los ajustes.
                        blocked = permissions.missing().any { !shouldShowRequestPermissionRationale(it) }
                    }

                val ready = roleState == SmsRoleState.Held && permissionState == SmsPermissionState.Granted
                when {
                    skipped && !ready -> ProtectionOffPlaceholder(onActivate = { skipped = false })
                    roleState != SmsRoleState.Held -> DefaultSmsRoleScreen(
                        state = roleState,
                        onRequest = { roles.requestIntent()?.let(requestRole::launch) },
                        onSkip = { skipped = true },
                    )
                    // Los permisos se piden después del rol, nunca antes.
                    permissionState != SmsPermissionState.Granted -> SmsPermissionScreen(
                        state = permissionState,
                        blocked = blocked,
                        onRequest = { requestPermissions.launch(permissions.missing().toTypedArray()) },
                        onOpenSettings = ::openAppSettings,
                        onSkip = { skipped = true },
                    )
                    // Con el rol y los permisos, la app muestra los mensajes (#5).
                    else -> MessagesRoute()
                }
            }
        }
    }

    // La persona puede quitar el rol o un permiso en los ajustes del sistema: se vuelve a comprobar al volver a la app.
    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        roleState = roles.state()
        permissionState = permissions.state()
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.fromParts("package", packageName, null) },
        )
    }
}

/** Provisional: destino de "Omitir" hasta que exista la pantalla de inicio (#17). */
@Composable
private fun ProtectionOffPlaceholder(onActivate: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(SmishingPalette.Background).padding(Spacing.Md),
        contentAlignment = Alignment.Center,
    ) {
        ProtectionCard(
            active = false,
            title = stringResource(R.string.protection_off_title),
            message = stringResource(R.string.protection_off_message),
            actionLabel = stringResource(R.string.protection_activate),
            onAction = onActivate,
        )
    }
}
