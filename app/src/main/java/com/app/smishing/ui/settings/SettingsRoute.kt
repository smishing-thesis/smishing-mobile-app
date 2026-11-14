package com.app.smishing.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import android.Manifest
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.res.stringResource
import com.app.smishing.R
import com.app.smishing.domain.history.AnalysisHistory
import com.app.smishing.domain.mitigation.MitigationMode
import com.app.smishing.ui.designsystem.components.ConfirmDialog
import com.app.smishing.domain.mitigation.MitigationSettings
import com.app.smishing.domain.settings.AppPermission
import com.app.smishing.domain.settings.PermissionStatus
import com.app.smishing.domain.settings.PermissionStatusReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Une la pantalla de Ajustes con las preferencias y el estado de los permisos. Lee fuera del hilo principal, guarda el modo al
 * elegirlo y **vuelve a leer los permisos cada vez que la persona regresa a la app** (por ejemplo, después de cambiarlos en los
 * ajustes del sistema), así la pantalla no muestra un estado viejo.
 */
@Composable
fun SettingsRoute(
    history: AnalysisHistory,
    settings: MitigationSettings,
    permissions: PermissionStatusReader,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf<MitigationMode?>(null) }
    var statuses by remember { mutableStateOf<List<PermissionStatus>>(emptyList()) }
    var refresh by remember { mutableIntStateOf(0) }
    var confirmDeleteHistory by remember { mutableStateOf(false) }
    // Si se negó el permiso de contactos en esta pantalla, el botón pasa a abrir los ajustes de la app (Android deja de preguntar).
    var contactsDenied by rememberSaveable { mutableStateOf(false) }
    val requestContacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        contactsDenied = !granted
        refresh++
    }

    val activity = LocalActivity.current as? ComponentActivity
    DisposableEffect(activity) {
        val lifecycle = activity?.lifecycle
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    DisposableEffect(settings) {
        val subscription = settings.onChange { refresh++ }
        onDispose { subscription.close() }
    }
    LaunchedEffect(refresh) {
        withContext(Dispatchers.IO) {
            mode = settings.mode()
            statuses = permissions.statuses()
        }
    }

    SettingsScreen(
        mode = mode,
        onModeChange = { chosen ->
            mode = chosen
            scope.launch(Dispatchers.IO) { settings.setMode(chosen) }
        },
        permissions = statuses,
        onOpenSystemSettings = { permission -> context.startActivity(SystemSettingsIntents.forPermission(context, permission)) },
        modifier = modifier,
        onContacts = {
            // La primera vez se pide; si ya se negó y Android no vuelve a preguntar, se abren los ajustes de la app.
            if (contactsDenied) context.startActivity(SystemSettingsIntents.forPermission(context, AppPermission.Contacts))
            else requestContacts.launch(Manifest.permission.READ_CONTACTS)
        },
        onDeleteHistory = { confirmDeleteHistory = true },
        extra = { DemoSection() },
    )
    if (confirmDeleteHistory) {
        ConfirmDialog(
            title = stringResource(R.string.delete_history_title),
            message = stringResource(R.string.delete_history_text),
            confirmLabel = stringResource(R.string.delete_history_confirm),
            onConfirm = {
                confirmDeleteHistory = false
                scope.launch(Dispatchers.IO) { history.deleteAll() }
            },
            dismissLabel = stringResource(R.string.delete_cancel),
            onDismiss = { confirmDeleteHistory = false },
        )
    }
}

/** A dónde lleva "Abrir ajustes" para cada permiso. */
object SystemSettingsIntents {
    fun forPermission(context: Context, permission: AppPermission): Intent = when (permission) {
        // El rol de SMS se cambia en "Aplicaciones predeterminadas".
        AppPermission.DefaultSmsApp -> Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        AppPermission.Notifications ->
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        AppPermission.ReadSms, AppPermission.SendSms, AppPermission.Contacts ->
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    }
}
