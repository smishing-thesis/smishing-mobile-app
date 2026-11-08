package com.app.smishing.ui

import android.os.Bundle
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
import com.app.smishing.data.sms.AndroidSmsRoleRepository
import com.app.smishing.domain.sms.SmsRoleState
import com.app.smishing.ui.designsystem.components.ProtectionCard
import com.app.smishing.ui.designsystem.theme.SmishingPalette
import com.app.smishing.ui.designsystem.theme.SmishingTheme
import com.app.smishing.ui.designsystem.theme.Spacing
import com.app.smishing.ui.onboarding.DefaultSmsRoleScreen

/**
 * Pantalla de entrada. Por ahora solo el paso 2 del onboarding (#2): pide el rol de SMS y, cada vez que se vuelve a
 * la app, comprueba si se conserva. Los demás pasos y la pantalla de inicio llegan con #3 y #17.
 */
class MainActivity : ComponentActivity() {

    private lateinit var roles: AndroidSmsRoleRepository
    private var roleState by mutableStateOf(SmsRoleState.NotHeld)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        roles = AndroidSmsRoleRepository(this)
        roleState = roles.state()

        setContent {
            SmishingTheme {
                var skipped by rememberSaveable { mutableStateOf(false) }
                val requestRole = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                    roleState = roles.state()
                }

                if (skipped && roleState != SmsRoleState.Held) {
                    ProtectionOffPlaceholder(onActivate = { skipped = false })
                } else {
                    DefaultSmsRoleScreen(
                        state = roleState,
                        onRequest = { roles.requestIntent()?.let(requestRole::launch) },
                        onSkip = { skipped = true },
                    )
                }
            }
        }
    }

    // La persona puede quitar el rol en los ajustes del sistema: se vuelve a comprobar al volver a la app.
    override fun onResume() {
        super.onResume()
        roleState = roles.state()
    }
}

/** Provisional: destino de "Omitir por ahora" hasta que exista la pantalla de inicio (#17). */
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
