package com.app.smishing.ui.messages

import android.Manifest
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.app.smishing.data.analysis.AnalysisPipeline
import com.app.smishing.ui.settings.SettingsRoute
import com.app.smishing.domain.settings.PermissionStatusReader
import com.app.smishing.domain.mitigation.MitigationSettings
import com.app.smishing.data.settings.DataStoreMitigationSettings
import com.app.smishing.data.settings.AndroidPermissionStatusReader
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.Icons
import com.app.smishing.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import com.app.smishing.ui.designsystem.icons.SmishingIcons
import com.app.smishing.ui.designsystem.components.SmishingBottomBar
import com.app.smishing.ui.designsystem.components.NavItem
import com.app.smishing.ui.history.HistoryScreen
import com.app.smishing.domain.risk.RiskResult
import com.app.smishing.domain.history.AnalysisRecord
import com.app.smishing.domain.history.AnalysisHistory
import com.app.smishing.data.history.RoomAnalysisHistory
import com.app.smishing.data.history.HistoryDatabase
import com.app.smishing.data.messaging.TelephonyConversationRepository
import com.app.smishing.domain.risk.RiskAssessor
import com.app.smishing.ui.alerts.MessageDetailScreen
import com.app.smishing.ui.alerts.MessageDetailState
import com.app.smishing.data.sms.AndroidSmsSender
import com.app.smishing.domain.messaging.Conversation
import com.app.smishing.domain.messaging.ConversationMessage
import com.app.smishing.domain.messaging.ConversationRepository
import com.app.smishing.domain.messaging.SendOutcome
import com.app.smishing.domain.messaging.SendSms
import com.app.smishing.domain.messaging.normalizeRecipient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Une las pantallas de mensajes con el buzón del sistema: la lista (#5), la conversación abierta (#5), y redactar y enviar
 * (#6). Lee y envía fuera del hilo principal y vuelve a leer cuando el buzón cambia (porque llega un SMS o cambia el
 * estado de uno enviado), así que no hace falta reabrir la app. "Atrás" vuelve a la lista.
 *
 * La barra inferior tiene tres pestañas: mensajes, historial (#12) y ajustes (#13, #25).
 *
 * Si no se puede enviar por falta del permiso de enviar, lo pide y la persona toca Enviar otra vez.
 */
@Composable
fun MessagesRoute(
    modifier: Modifier = Modifier,
    repository: ConversationRepository = rememberTelephonyRepository(),
    sendSms: SendSms = rememberSendSms(),
    assessor: RiskAssessor = rememberAssessor(),
    history: AnalysisHistory = rememberHistory(),
    mitigationSettings: MitigationSettings = rememberMitigationSettings(),
    permissionStatuses: PermissionStatusReader = rememberPermissionStatuses(),
    /** El mensaje cuyo detalle hay que abrir (por tocar una alerta), o `null`. */
    openMessageId: Long? = null,
    onMessageOpened: () -> Unit = {},
) {
    val context = LocalContext.current
    val resolver = context.contentResolver
    var version by remember { mutableIntStateOf(0) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                version++
            }
        }
        resolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }

    // Se guardan solo el id y el remitente de la conversación abierta y lo escrito, para que sobrevivan a girar la pantalla.
    var openThreadId by rememberSaveable { mutableStateOf<Long?>(null) }
    var openAddress by rememberSaveable { mutableStateOf("") }
    var composing by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    var recipient by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var sendError by remember { mutableStateOf<SendOutcome?>(null) }
    var sending by remember { mutableStateOf(false) }

    val requestSendPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val scope = rememberCoroutineScope()
    fun send(to: String, text: String, onSent: (SendOutcome.Sent) -> Unit) {
        if (sending) return
        sending = true
        scope.launch {
            val outcome = withContext(Dispatchers.IO) { sendSms.send(to, text) }
            sending = false
            sendError = outcome.takeUnless { it is SendOutcome.Sent }
            when (outcome) {
                is SendOutcome.Sent -> onSent(outcome)
                SendOutcome.NotAllowed ->
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                        requestSendPermission.launch(Manifest.permission.SEND_SMS)
                    }
                else -> Unit
            }
        }
    }

    // El historial (#12): la pestaña activa (0 = mensajes, 1 = historial) y el registro abierto. Se recarga cuando la base cambia.
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var historyRecordId by rememberSaveable { mutableStateOf<Long?>(null) }
    var historyVersion by remember { mutableIntStateOf(0) }
    DisposableEffect(history) {
        val subscription = history.onChange { historyVersion++ }
        onDispose { subscription.close() }
    }

    // El detalle de un mensaje (#10): se abre al tocar una alerta y se cierra con "atrás".
    var detailMessageId by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(openMessageId) {
        if (openMessageId != null) {
            detailMessageId = openMessageId
            historyRecordId = null
            composing = false
            onMessageOpened()
        }
    }

    BackHandler(enabled = composing || openThreadId != null || detailMessageId != null || historyRecordId != null) {
        sendError = null
        when {
            detailMessageId != null -> detailMessageId = null
            historyRecordId != null -> historyRecordId = null
            composing -> composing = false
            else -> openThreadId = null
        }
    }

    val threadId = openThreadId
    val detailId = detailMessageId
    val recordId = historyRecordId
    // La barra inferior solo está en las pantallas de primer nivel: no en un detalle, una conversación ni al redactar.
    val showBar = detailId == null && recordId == null && !composing && threadId == null
    Column(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            when {
                detailId != null -> {
                    var detail by remember(detailId) { mutableStateOf<MessageDetailState>(MessageDetailState.Loading) }
                    // Se muestra lo guardado al recibir el mensaje (#12): es lo que motivó la alerta. Si no hay registro (un mensaje
                    // anterior al historial), se evalúa el mensaje, que da lo mismo porque la evaluación es determinista.
                    LaunchedEffect(detailId) {
                        detail = withContext(Dispatchers.IO) {
                            val record = history.byMessageId(detailId)
                            val message = repository.message(detailId)
                            when {
                                // El registro solo vale si es de este mensaje: si el almacenamiento de SMS del sistema se borró,
                                // sus ids empiezan de nuevo y un mensaje nuevo podría tener el id de un registro viejo.
                                record != null && (message == null || message.body == record.body) ->
                                    MessageDetailState.Loaded(record.body, RiskResult.Assessed(record.assessment))
                                message != null -> MessageDetailState.Loaded(message.body, assessor.assess(message.body))
                                else -> MessageDetailState.Missing
                            }
                        }
                    }
                    MessageDetailScreen(state = detail, onBack = { detailMessageId = null }, modifier = Modifier)
                }
                recordId != null -> {
                    var detail by remember(recordId) { mutableStateOf<MessageDetailState>(MessageDetailState.Loading) }
                    LaunchedEffect(recordId) {
                        detail = withContext(Dispatchers.IO) {
                            val record = history.byId(recordId)
                            if (record == null) MessageDetailState.Missing
                            else MessageDetailState.Loaded(record.body, RiskResult.Assessed(record.assessment))
                        }
                    }
                    MessageDetailScreen(state = detail, onBack = { historyRecordId = null }, modifier = Modifier)
                }
                composing -> NewMessageScreen(
                    recipient = recipient,
                    onRecipientChange = { recipient = it },
                    body = body,
                    onBodyChange = { body = it },
                    onSend = {
                        send(recipient, body) { sent ->
                            openAddress = normalizeRecipient(recipient) ?: recipient
                            openThreadId = sent.threadId
                            composing = false
                            recipient = ""
                            body = ""
                        }
                    },
                    onBack = {
                        sendError = null
                        composing = false
                    },
                    error = sendError,
                    modifier = Modifier,
                )
                tab == 2 && threadId == null -> SettingsRoute(
            settings = mitigationSettings,
            permissions = permissionStatuses,
            modifier = Modifier,
        )
        tab == 1 && threadId == null -> {
                    var records by remember { mutableStateOf<List<AnalysisRecord>?>(null) }
                    LaunchedEffect(historyVersion) { records = withContext(Dispatchers.IO) { history.all() } }
                    HistoryScreen(records = records, onOpen = { historyRecordId = it.id }, modifier = Modifier)
                }
                threadId == null -> {
                    var conversations by remember { mutableStateOf<List<Conversation>?>(null) }
                    LaunchedEffect(version) { conversations = withContext(Dispatchers.IO) { repository.conversations() } }
                    ConversationListScreen(
                        conversations = conversations,
                        nowMillis = System.currentTimeMillis(),
                        onOpen = {
                            sendError = null
                            openThreadId = it.threadId
                            openAddress = it.address
                        },
                        onCompose = {
                            sendError = null
                            composing = true
                        },
                        modifier = Modifier,
                    )
                }
                else -> {
                    var messages by remember(threadId) { mutableStateOf<List<ConversationMessage>?>(null) }
                    LaunchedEffect(threadId, version) { messages = withContext(Dispatchers.IO) { repository.messages(threadId) } }
                    ConversationScreen(
                        address = openAddress,
                        messages = messages,
                        onBack = {
                            sendError = null
                            openThreadId = null
                        },
                        draft = draft,
                        onDraftChange = { draft = it },
                        onSend = { send(openAddress, draft) { draft = "" } },
                        error = sendError,
                        modifier = Modifier,
                    )
                }
            }
        }
        if (showBar) {
            SmishingBottomBar(
                items = listOf(
                    NavItem(stringResource(R.string.nav_messages), SmishingIcons.Chat),
                    NavItem(stringResource(R.string.nav_history), SmishingIcons.History),
                    NavItem(stringResource(R.string.nav_settings), Icons.Outlined.Settings),
                ),
                selectedIndex = tab,
                onSelect = { tab = it },
            )
        }
    }
}

@Composable
private fun rememberMitigationSettings(): MitigationSettings {
    val context = LocalContext.current.applicationContext
    return remember(context) { DataStoreMitigationSettings(context) }
}

@Composable
private fun rememberPermissionStatuses(): PermissionStatusReader {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidPermissionStatusReader(context) }
}

@Composable
private fun rememberHistory(): AnalysisHistory {
    val context = LocalContext.current.applicationContext
    return remember(context) { RoomAnalysisHistory(HistoryDatabase.get(context)) }
}

@Composable
private fun rememberAssessor(): RiskAssessor {
    val context = LocalContext.current.applicationContext
    return remember(context) { AnalysisPipeline.assessor(context) }
}

@Composable
private fun rememberTelephonyRepository(): ConversationRepository {
    val context = LocalContext.current.applicationContext
    return remember(context) { TelephonyConversationRepository(context) }
}

@Composable
private fun rememberSendSms(): SendSms {
    val context = LocalContext.current.applicationContext
    return remember(context) { SendSms(AndroidSmsSender(context)) }
}
