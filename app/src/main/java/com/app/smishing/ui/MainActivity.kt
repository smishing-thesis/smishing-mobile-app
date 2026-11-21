package com.app.smishing.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.app.smishing.data.history.HistoryDatabase
import com.app.smishing.data.history.RoomAnalysisHistory
import com.app.smishing.data.sharing.ContributionScheduler
import com.app.smishing.data.settings.AndroidPermissionStatusReader
import com.app.smishing.data.settings.DataStoreOnboardingProgress
import com.app.smishing.domain.history.AnalysisHistory
import com.app.smishing.domain.settings.OnboardingProgress
import com.app.smishing.domain.settings.PermissionStatusReader
import com.app.smishing.ui.home.HomeRoute
import com.app.smishing.ui.onboarding.WelcomeScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.app.smishing.data.sms.AndroidSmsPermissionRepository
import com.app.smishing.data.sms.AndroidSmsRoleRepository
import com.app.smishing.domain.sms.SmsPermissionState
import com.app.smishing.domain.sms.SmsRoleState
import com.app.smishing.ui.designsystem.components.ProtectionCard
import com.app.smishing.ui.designsystem.components.SmishySplashScreen
import com.app.smishing.ui.designsystem.theme.SmishingTheme
import com.app.smishing.ui.designsystem.theme.Spacing
import com.app.smishing.ui.alerts.RequestNotificationPermission
import com.app.smishing.ui.messages.MessagesRoute
import com.app.smishing.ui.onboarding.DefaultSmsRoleScreen
import com.app.smishing.ui.onboarding.SmsPermissionScreen

/**
 * Pantalla de entrada. El onboarding de SMS: el paso 2 pide el rol de SMS (#2) y el paso 3, los permisos de lectura
 * (#3), en ese orden. Cuando se tienen los dos, muestra los mensajes (#5). Cada vez que se vuelve a la app se comprueba de
 * nuevo si se conservan. El paso 1 (bienvenida, una sola vez) y la pantalla de inicio son de #17.
 */
class MainActivity : ComponentActivity() {

    private lateinit var roles: AndroidSmsRoleRepository
    private lateinit var permissions: AndroidSmsPermissionRepository
    private lateinit var onboarding: OnboardingProgress
    private lateinit var permissionStatuses: PermissionStatusReader
    private lateinit var history: AnalysisHistory
    private var roleState by mutableStateOf(SmsRoleState.NotHeld)
    private var permissionState by mutableStateOf(SmsPermissionState.NotGranted)

    /** El mensaje cuyo detalle pidió abrir una alerta (#10), hasta que la pantalla de mensajes lo abre. */
    private var pendingMessageId by mutableStateOf<Long?>(null)

    /** El mensaje cuya conversación pidió abrir el aviso de un SMS (#49), hasta que la pantalla de mensajes la abre. */
    private var pendingConversationMessageId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Antes de todo: cambia al tema normal cuando la app está lista y deja Smishy quieto en la pantalla de carga del sistema.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        roles = AndroidSmsRoleRepository(this)
        permissions = AndroidSmsPermissionRepository(this)
        onboarding = DataStoreOnboardingProgress(this)
        permissionStatuses = AndroidPermissionStatusReader(this)
        history = RoomAnalysisHistory(HistoryDatabase.get(this))
        // Si quedaron aportes pendientes de antes (#71), se retoma su envío; el sistema lo ejecuta cuando haya conexión.
        ContributionScheduler.resume(this)
        refresh()
        pendingMessageId = messageIdOf(intent)
        pendingConversationMessageId = conversationMessageIdOf(intent)

        setContent {
            SmishingTheme {
                var blocked by rememberSaveable { mutableStateOf(false) }
                // Si la persona ya terminó el onboarding: aceptó el rol (paso 2) u omitió el paso 2. `null` mientras se lee lo guardado.
                // Mientras no lo haya terminado, la bienvenida (paso 1) vuelve a salir cada vez que abre la app.
                var onboardingDone by rememberSaveable { mutableStateOf<Boolean?>(null) }
                // Si ya pasó del paso 1 en esta apertura de la app. No se guarda: al cerrar y volver a abrir, el paso 1 sale de nuevo.
                var welcomeDismissed by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    if (onboardingDone == null) onboardingDone = withContext(Dispatchers.IO) { onboarding.onboardingDone() }
                }
                fun setOnboardingDone(done: Boolean) {
                    onboardingDone = done
                    lifecycleScope.launch(Dispatchers.IO) { onboarding.setOnboardingDone(done) }
                }

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
                // Con el rol y los permisos el onboarding está terminado: se guarda para que, si los quita después, la app abra Inicio.
                LaunchedEffect(ready) { if (ready && onboardingDone != true) setOnboardingDone(true) }
                val stage = when {
                    // Mientras se lee lo guardado: Smishy quieto (la animación está en el código pero no se usa, la carga es muy rápida).
                    !ready && onboardingDone == null -> Stage.Loading
                    // Paso 1: sale cada vez que se abre la app, hasta que se acepta u omite el paso 2.
                    !ready && onboardingDone == false && !welcomeDismissed -> Stage.Welcome
                    // Con el onboarding terminado (se omitió el paso 2), la app abre Inicio con la protección apagada (#17).
                    !ready && onboardingDone == true -> Stage.Home
                    roleState != SmsRoleState.Held -> Stage.Role
                    // Los permisos se piden después del rol, nunca antes.
                    permissionState != SmsPermissionState.Granted -> Stage.Permission
                    // Con el rol y los permisos, la app muestra los mensajes (#5).
                    else -> Stage.Messages
                }
                // Atrás en el paso 2 vuelve al paso 1, como tocar el primer punto.
                BackHandler(enabled = stage == Stage.Role && onboardingDone == false) { welcomeDismissed = false }

                // Del paso 1 al 2 la pantalla se desliza hacia la izquierda, y del 2 al 1, hacia la derecha; lo demás cambia con un fundido.
                AnimatedContent(
                    targetState = stage,
                    transitionSpec = {
                        val slide = tween<IntOffset>(durationMillis = 350, easing = FastOutSlowInEasing)
                        when {
                            initialState == Stage.Welcome && targetState == Stage.Role ->
                                slideInHorizontally(slide) { it } togetherWith slideOutHorizontally(slide) { -it }
                            initialState == Stage.Role && targetState == Stage.Welcome ->
                                slideInHorizontally(slide) { -it } togetherWith slideOutHorizontally(slide) { it }
                            else -> fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                        }
                    },
                    label = "stage",
                ) { current ->
                    when (current) {
                        Stage.Loading -> SmishySplashScreen()
                        Stage.Welcome -> WelcomeScreen(
                            onNext = { welcomeDismissed = true },
                        )
                        // "Activar protección" vuelve al onboarding, directo al paso 2; si se cierra a medias, el paso 1 vuelve a salir.
                        Stage.Home -> HomeRoute(
                            permissions = permissionStatuses,
                            history = history,
                            onActivate = {
                                welcomeDismissed = true
                                setOnboardingDone(false)
                            },
                        )
                        Stage.Role -> DefaultSmsRoleScreen(
                            state = roleState,
                            onRequest = { roles.requestIntent()?.let(requestRole::launch) },
                            onSkip = { setOnboardingDone(true) },
                            onBack = { welcomeDismissed = false },
                        )
                        Stage.Permission -> SmsPermissionScreen(
                            state = permissionState,
                            blocked = blocked,
                            onRequest = { requestPermissions.launch(permissions.missing().toTypedArray()) },
                            onOpenSettings = ::openAppSettings,
                            onSkip = { setOnboardingDone(true) },
                        )
                        Stage.Messages -> {
                            RequestNotificationPermission()
                            MessagesRoute(
                                openMessageId = pendingMessageId,
                                onMessageOpened = { pendingMessageId = null },
                                openConversationOfMessageId = pendingConversationMessageId,
                                onConversationOpened = { pendingConversationMessageId = null },
                            )
                        }
                    }
                }
            }
        }
    }

    // Con la app abierta, tocar una alerta llega aquí (singleTop) en lugar de abrir otra copia de la pantalla.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        messageIdOf(intent)?.let { pendingMessageId = it }
        conversationMessageIdOf(intent)?.let { pendingConversationMessageId = it }
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

    private fun messageIdOf(intent: Intent?): Long? =
        intent?.getLongExtra(EXTRA_MESSAGE_ID, NO_MESSAGE)?.takeIf { it != NO_MESSAGE }

    private fun conversationMessageIdOf(intent: Intent?): Long? =
        intent?.getLongExtra(EXTRA_CONVERSATION_MESSAGE_ID, NO_MESSAGE)?.takeIf { it != NO_MESSAGE }

    companion object {
        private const val EXTRA_MESSAGE_ID = "com.app.smishing.extra.MESSAGE_ID"
        private const val EXTRA_CONVERSATION_MESSAGE_ID = "com.app.smishing.extra.CONVERSATION_MESSAGE_ID"
        private const val ACTION_OPEN_CONVERSATION = "com.app.smishing.action.OPEN_CONVERSATION"
        private const val NO_MESSAGE = -1L

        /**
         * La intención que abre la conversación de un mensaje: la de tocar el aviso de un SMS (#49). Solo lleva el id del mensaje,
         * nunca el texto. Lleva su propia acción para que no se confunda con la del detalle de una alerta.
         */
        fun conversationIntent(context: Context, messageId: Long): Intent =
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_OPEN_CONVERSATION)
                .putExtra(EXTRA_CONVERSATION_MESSAGE_ID, messageId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        /** La intención que abre el detalle de un mensaje: la de tocar una alerta. Solo lleva el id, nunca el texto. */
        fun detailIntent(context: Context, messageId: Long): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_MESSAGE_ID, messageId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.fromParts("package", packageName, null) },
        )
    }
}

/** Qué pantalla toca mostrar: el estado de la app, para poder animar el paso de una a otra. */
private enum class Stage { Loading, Welcome, Home, Role, Permission, Messages }
