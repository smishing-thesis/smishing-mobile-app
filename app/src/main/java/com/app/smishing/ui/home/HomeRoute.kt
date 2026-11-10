package com.app.smishing.ui.home

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.app.smishing.domain.history.AnalysisHistory
import com.app.smishing.domain.history.WeeklySummary
import com.app.smishing.domain.history.weeklySummary
import com.app.smishing.domain.settings.PermissionStatus
import com.app.smishing.domain.settings.PermissionStatusReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Une "Inicio" con el estado real: **vuelve a leer el rol, los permisos y las notificaciones cada vez que la persona regresa a la
 * app** (por ejemplo, tras cambiarlos en los ajustes del sistema) y el resumen cuando el historial cambia. Lee fuera del hilo
 * principal.
 */
@Composable
fun HomeRoute(
    permissions: PermissionStatusReader,
    history: AnalysisHistory,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
    clock: () -> Long = System::currentTimeMillis,
) {
    var statuses by remember { mutableStateOf<List<PermissionStatus>?>(null) }
    var summary by remember { mutableStateOf<WeeklySummary?>(null) }
    var refresh by remember { mutableIntStateOf(0) }

    val activity = LocalActivity.current as? ComponentActivity
    DisposableEffect(activity) {
        val lifecycle = activity?.lifecycle
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    DisposableEffect(history) {
        val subscription = history.onChange { refresh++ }
        onDispose { subscription.close() }
    }
    LaunchedEffect(refresh) {
        withContext(Dispatchers.IO) {
            statuses = permissions.statuses()
            summary = weeklySummary(history.all(), clock())
        }
    }

    HomeScreen(statuses = statuses, summary = summary, onActivate = onActivate, modifier = modifier)
}
