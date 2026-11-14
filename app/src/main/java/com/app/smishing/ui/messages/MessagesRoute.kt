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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.app.smishing.domain.history.AnalyzeStoredMessage
import com.app.smishing.domain.messaging.DeleteMessages
import com.app.smishing.ui.designsystem.components.ConfirmDialog
import com.app.smishing.domain.history.MessageRisk
import com.app.smishing.domain.alerts.AnalysisOutcome
import com.app.smishing.domain.history.isAnalyzed
import kotlinx.coroutines.CancellationException
import com.app.smishing.domain.history.AnalysisHistory
import com.app.smishing.data.history.RoomAnalysisHistory
import com.app.smishing.data.history.HistoryDatabase
import com.app.smishing.data.messaging.TelephonyConversationRepository
import com.app.smishing.domain.risk.RiskAssessor
import com.app.smishing.ui.alerts.MessageDetailScreen
import androidx.compose.material.icons.outlined.Home
import com.app.smishing.domain.settings.AppPermission
import com.app.smishing.ui.home.HomeRoute
import com.app.smishing.ui.settings.SystemSettingsIntents
import com.app.smishing.ui.alerts.Protection
import com.app.smishing.ui.alerts.DetailActions
import com.app.smishing.domain.risk.RiskLevel
import com.app.smishing.domain.mitigation.latest
import com.app.smishing.domain.mitigation.MitigationMode
import com.app.smishing.domain.mitigation.MitigationLog
import com.app.smishing.domain.mitigation.MitigationExecutor
import com.app.smishing.domain.mitigation.MitigationAction
import com.app.smishing.domain.mitigation.ActionType
import com.app.smishing.data.alerts.AndroidMessageNotifier
import com.app.smishing.data.mitigation.AndroidMitigation
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
 * La barra inferior tiene cuatro pestañas: inicio (#17), mensajes, historial (#12) y ajustes (#13, #25). Se abre en Inicio.
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
    mitigation: MitigationExecutor = rememberMitigationExecutor(),
    actionLog: MitigationLog = rememberActionLog(),
    permissionStatuses: PermissionStatusReader = rememberPermissionStatuses(),
    /** Lo que hace "Activar protección" de Inicio cuando la app ya tiene el rol y los permisos (solo pueden faltar las notificaciones). */
    onActivateProtection: () -> Unit = rememberOpenNotificationSettings(),
    /** El mensaje cuyo detalle hay que abrir (por tocar una alerta), o `null`. */
    openMessageId: Long? = null,
    onMessageOpened: () -> Unit = {},
    /** El mensaje cuya conversación hay que abrir (por tocar el aviso de un SMS, #49), o `null`. */
    openConversationOfMessageId: Long? = null,
    onConversationOpened: () -> Unit = {},
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
    // Lo que se va a borrar (#51), mientras la persona confirma, y si el último intento de borrar falló.
    var deleteTarget by remember { mutableStateOf<DeleteTarget?>(null) }
    var deleteFailed by remember { mutableStateOf(false) }

    val requestSendPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val scope = rememberCoroutineScope()

    /** Borra lo confirmado (#51): el buzón y, si la persona lo pidió, el historial; descarta los avisos de lo borrado. */
    fun confirmDelete(target: DeleteTarget, alsoHistory: Boolean) {
        deleteTarget = null
        scope.launch(Dispatchers.IO) {
            try {
                val deleter = DeleteMessages(repository, history)
                val result = when (target) {
                    is DeleteTarget.Message -> deleter.deleteMessage(target.message, alsoHistory)
                    is DeleteTarget.Conversation -> deleter.deleteConversation(target.threadId, alsoHistory)
                }
                AndroidMessageNotifier.dismiss(context, result.messageIds)
                // Si la conversación abierta quedó sin mensajes, se vuelve a la lista.
                val open = openThreadId
                if (open != null && repository.messages(open).isEmpty()) openThreadId = null
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Sin registrar el motivo: la excepción podría traer el texto de un mensaje.
                deleteFailed = true
            }
        }
    }

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
    // El riesgo guardado de cada mensaje (#60), con una sola lectura; se vuelve a leer cuando el historial o las acciones cambian.
    // La lista de conversaciones y su desplazamiento viven aquí, no dentro de la lista: al abrir una conversación la lista sale de la
    // pantalla y, si guardara su propio estado, al volver empezaría de nuevo arriba y vacía.
    var conversations by remember { mutableStateOf<List<Conversation>?>(null) }
    val conversationListState = rememberLazyListState()
    // `null` mientras se lee: así un mensaje no aparece "sin analizar" por un instante.
    var loadedRisks by remember { mutableStateOf<Map<Long, MessageRisk>?>(null) }
    LaunchedEffect(historyVersion) { loadedRisks = withContext(Dispatchers.IO) { history.risks() } }
    val risks = loadedRisks.orEmpty()
    // Lo que pasó al tocar "Analizar" en cada mensaje, para responderle a la persona (#59): evita guardar dos veces el mismo análisis,
    // avisa si falló y dice que no hubo señales, porque un mensaje de riesgo bajo no lleva aviso.
    var attempts by remember { mutableStateOf<Map<Long, StoredAnalysis>>(emptyMap()) }
    val analyzer = remember(assessor, history) { AnalyzeStoredMessage(assessor, history) }
    DisposableEffect(history) {
        val subscription = history.onChange { historyVersion++ }
        onDispose { subscription.close() }
    }
    // Las acciones tomadas (#28) cambian aparte del historial: se vuelve a leer también cuando cambian.
    DisposableEffect(actionLog) {
        val subscription = actionLog.onChange { historyVersion++ }
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

    // La conversación de un mensaje (#49): se abre al tocar el aviso de un SMS. Si el mensaje ya no está, se queda en la lista de mensajes.
    LaunchedEffect(openConversationOfMessageId) {
        if (openConversationOfMessageId != null) {
            val message = withContext(Dispatchers.IO) { repository.message(openConversationOfMessageId) }
            detailMessageId = null
            historyRecordId = null
            composing = false
            tab = 1
            if (message != null) {
                openThreadId = message.threadId
                openAddress = message.address
            } else {
                openThreadId = null
            }
            onConversationOpened()
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
                detailId != null || recordId != null -> {
                    // Un solo detalle para los dos caminos: el de una alerta (por el id del mensaje) y el del historial (por el id del
                    // registro). Una vez encontrado el registro se sigue por su id, porque restaurar un mensaje le cambia el del buzón.
                    var detail by remember(detailId, recordId) { mutableStateOf<MessageDetailState>(MessageDetailState.Loading) }
                    var current by remember(detailId, recordId) { mutableStateOf<AnalysisRecord?>(null) }
                    var resolvedId by remember(detailId, recordId) { mutableStateOf(recordId) }
                    var busy by remember(detailId, recordId) { mutableStateOf(false) }
                    // Se muestra lo guardado al recibir el mensaje (#12): es lo que motivó la alerta. Si no hay registro (un mensaje
                    // anterior al historial), se evalúa el mensaje, que da lo mismo porque la evaluación es determinista.
                    LaunchedEffect(detailId, recordId, historyVersion) {
                        val (state, record) = withContext(Dispatchers.IO) {
                            loadDetail(detailId, resolvedId, history, repository, assessor, actionLog, mitigationSettings)
                        }
                        detail = state
                        current = record
                        if (record != null) resolvedId = record.id
                    }
                    fun act(work: (AnalysisRecord) -> Unit) {
                        val record = current
                        if (record == null || busy) return
                        busy = true
                        scope.launch {
                            withContext(Dispatchers.IO) { work(record) }
                            busy = false
                            historyVersion++
                        }
                    }
                    MessageDetailScreen(
                        state = detail,
                        onBack = {
                            detailMessageId = null
                            historyRecordId = null
                        },
                        modifier = Modifier,
                        actions = DetailActions(
                            onBlock = { act { mitigation.perform(it, ActionType.BlockSender) } },
                            onRemove = { act { mitigation.perform(it, ActionType.RemoveMessage) } },
                            onMarkSafe = { act { mitigation.markSafe(it) } },
                            onOpenSettings = {
                                detailMessageId = null
                                historyRecordId = null
                                tab = 3
                            },
                        ),
                    )
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
                tab == 3 && threadId == null -> SettingsRoute(
            settings = mitigationSettings,
            permissions = permissionStatuses,
            modifier = Modifier,
        )
        tab == 2 && threadId == null -> {
                    var records by remember { mutableStateOf<List<AnalysisRecord>?>(null) }
                    LaunchedEffect(historyVersion) { records = withContext(Dispatchers.IO) { history.all() } }
                    var actions by remember { mutableStateOf<Map<Long, List<MitigationAction>>>(emptyMap()) }
                    LaunchedEffect(historyVersion) { actions = withContext(Dispatchers.IO) { actionLog.all() } }
                    HistoryScreen(records = records, onOpen = { historyRecordId = it.id }, actions = actions, modifier = Modifier)
                }
                tab == 0 && threadId == null -> HomeRoute(
                    permissions = permissionStatuses,
                    history = history,
                    onActivate = onActivateProtection,
                    modifier = Modifier,
                )
                threadId == null -> {
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
                        risks = risks,
                        listState = conversationListState,
                        onDelete = { deleteTarget = DeleteTarget.Conversation(it.threadId, it.address) },
                    )
                }
                else -> {
                    var messages by remember(threadId) { mutableStateOf<List<ConversationMessage>?>(null) }
                    LaunchedEffect(threadId, version) {
                        messages = withContext(Dispatchers.IO) { repository.messages(threadId) }
                        // Abrir la conversación es leerla (#50): se marcan como leídos los recibidos y se descartan sus avisos. También
                        // los que llegan con la conversación abierta. Marcar cambia el buzón y vuelve a pasar por aquí, sin más que marcar.
                        // Marcar y descartar van juntos dentro del mismo bloque: marcar cambia el buzón, eso reinicia este efecto y, si
                        // el descarte estuviera afuera, el reinicio lo cancelaría antes de ejecutarse y el aviso quedaría ahí.
                        withContext(Dispatchers.IO) {
                            val read = repository.markRead(threadId)
                            if (read.isNotEmpty()) AndroidMessageNotifier.dismiss(context, read)
                        }
                    }
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
                        risks = risks,
                        onSeeRisk = { historyRecordId = it },
                        onDelete = { deleteTarget = DeleteTarget.Message(it) },
                        analysis = { m ->
                            val attempt = attempts[m.id]
                            when {
                                loadedRisks == null -> StoredAnalysis.None
                                attempt == StoredAnalysis.Analyzing -> StoredAnalysis.Analyzing
                                isAnalyzed(risks, m.id, m.body) ->
                                    // Analizado y sin señales: siempre lo dice. Uno marcado como seguro por la persona tuvo riesgo, así que no.
                                    if (risks[m.id]?.level == RiskLevel.Low) StoredAnalysis.NoRisk else StoredAnalysis.None
                                attempt == StoredAnalysis.Failed -> StoredAnalysis.Failed
                                assessor.canAssess(m.body) -> StoredAnalysis.CanAnalyze
                                else -> StoredAnalysis.None
                            }
                        },
                        onAnalyze = { m ->
                            attempts = attempts + (m.id to StoredAnalysis.Analyzing)
                            scope.launch(Dispatchers.IO) {
                                val recorded = try {
                                    analyzer.analyze(m.id, openAddress, m.body, m.atMillis) == AnalysisOutcome.Recorded
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    false // sin registrar el motivo: el mensaje es privado y la excepción podría traerlo
                                }
                                attempts = if (recorded) attempts - m.id else attempts + (m.id to StoredAnalysis.Failed)
                                if (recorded) historyVersion++
                            }
                        },
                    )
                }
            }
        }
        if (showBar) {
            SmishingBottomBar(
                items = listOf(
                    NavItem(stringResource(R.string.nav_home), Icons.Outlined.Home),
                    NavItem(stringResource(R.string.nav_messages), SmishingIcons.Chat),
                    NavItem(stringResource(R.string.nav_history), SmishingIcons.History),
                    NavItem(stringResource(R.string.nav_settings), Icons.Outlined.Settings),
                ),
                selectedIndex = tab,
                onSelect = { tab = it },
            )
        }
    }
    deleteTarget?.let { target ->
        DeleteDialog(target = target, onConfirm = { alsoHistory -> confirmDelete(target, alsoHistory) }, onDismiss = { deleteTarget = null })
    }
    if (deleteFailed) {
        ConfirmDialog(
            title = stringResource(R.string.delete_failed_title),
            message = stringResource(R.string.delete_failed_text),
            confirmLabel = stringResource(R.string.delete_failed_ok),
            onConfirm = { deleteFailed = false },
            dismissLabel = stringResource(R.string.delete_cancel),
            onDismiss = { deleteFailed = false },
        )
    }
}

/** Arma el detalle de un mensaje: lo guardado (o, si no hay registro, el mensaje evaluado) con las acciones tomadas. */
private fun loadDetail(
    messageId: Long?,
    recordId: Long?,
    history: AnalysisHistory,
    repository: ConversationRepository,
    assessor: RiskAssessor,
    actionLog: MitigationLog,
    settings: MitigationSettings,
): Pair<MessageDetailState, AnalysisRecord?> {
    val message = messageId?.let { repository.message(it) }
    // El registro solo vale si es de este mensaje: si el almacenamiento de SMS del sistema se borró, sus ids empiezan de nuevo y un
    // mensaje nuevo podría tener el id de un registro viejo.
    val record = recordId?.let { history.byId(it) }
        ?: messageId?.let { history.byMessageId(it) }?.takeIf { message == null || message.body == it.body }
    return when {
        record != null -> {
            val level = record.assessment.level
            val canAct = level == RiskLevel.High && runCatching { settings.mode() != MitigationMode.Disabled }.getOrDefault(false)
            val automatic = runCatching { settings.mode() == MitigationMode.Automatic }.getOrDefault(false)
            val protection = Protection(record.markedSafeAtMillis != null, actionLog.actionsOf(record.id).latest(), canAct, automatic)
            MessageDetailState.Loaded(record.body, RiskResult.Assessed(record.assessment), protection) to record
        }
        message != null -> MessageDetailState.Loaded(message.body, assessor.assess(message.body)) to null
        else -> MessageDetailState.Missing to null
    }
}

/** Abre los ajustes de notificaciones de la app: lo único que puede faltar cuando ya se tienen el rol y los permisos de SMS. */
@Composable
private fun rememberOpenNotificationSettings(): () -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { context.startActivity(SystemSettingsIntents.forPermission(context, AppPermission.Notifications)) }
    }
}

@Composable
private fun rememberMitigationExecutor(): MitigationExecutor {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidMitigation.executor(context) }
}

@Composable
private fun rememberActionLog(): MitigationLog {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidMitigation.log(context) }
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
