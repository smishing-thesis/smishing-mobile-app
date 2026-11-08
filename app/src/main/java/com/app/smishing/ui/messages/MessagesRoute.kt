package com.app.smishing.ui.messages

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.app.smishing.data.messaging.TelephonyConversationRepository
import com.app.smishing.domain.messaging.Conversation
import com.app.smishing.domain.messaging.ConversationMessage
import com.app.smishing.domain.messaging.ConversationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Une la lista de mensajes y la conversación abierta con el buzón del sistema. Lee fuera del hilo principal y vuelve a
 * leer cuando el buzón cambia (por ejemplo, cuando llega un SMS), así que lo recibido aparece sin tener que reabrir la app.
 * "Atrás" en una conversación vuelve a la lista.
 */
@Composable
fun MessagesRoute(
    modifier: Modifier = Modifier,
    repository: ConversationRepository = rememberTelephonyRepository(),
) {
    val resolver = LocalContext.current.contentResolver
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

    // Se guardan solo el id y el remitente de la conversación abierta, para que sobrevivan a girar la pantalla.
    var openThreadId by rememberSaveable { mutableStateOf<Long?>(null) }
    var openAddress by rememberSaveable { mutableStateOf("") }
    BackHandler(enabled = openThreadId != null) { openThreadId = null }

    val threadId = openThreadId
    if (threadId == null) {
        var conversations by remember { mutableStateOf<List<Conversation>?>(null) }
        LaunchedEffect(version) { conversations = withContext(Dispatchers.IO) { repository.conversations() } }
        ConversationListScreen(
            conversations = conversations,
            nowMillis = System.currentTimeMillis(),
            onOpen = {
                openThreadId = it.threadId
                openAddress = it.address
            },
            modifier = modifier,
        )
    } else {
        var messages by remember(threadId) { mutableStateOf<List<ConversationMessage>?>(null) }
        LaunchedEffect(threadId, version) { messages = withContext(Dispatchers.IO) { repository.messages(threadId) } }
        ConversationScreen(address = openAddress, messages = messages, onBack = { openThreadId = null }, modifier = modifier)
    }
}

@Composable
private fun rememberTelephonyRepository(): ConversationRepository {
    val context = LocalContext.current.applicationContext
    return remember(context) { TelephonyConversationRepository(context) }
}
