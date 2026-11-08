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
import com.app.smishing.data.messaging.TelephonyConversationRepository
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
 * Si no se puede enviar por falta del permiso de enviar, lo pide y la persona toca Enviar otra vez.
 */
@Composable
fun MessagesRoute(
    modifier: Modifier = Modifier,
    repository: ConversationRepository = rememberTelephonyRepository(),
    sendSms: SendSms = rememberSendSms(),
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

    BackHandler(enabled = composing || openThreadId != null) {
        sendError = null
        if (composing) composing = false else openThreadId = null
    }

    val threadId = openThreadId
    when {
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
            modifier = modifier,
        )
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
                modifier = modifier,
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
                modifier = modifier,
            )
        }
    }
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
