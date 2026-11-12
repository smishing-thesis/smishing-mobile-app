# Avisos de cada SMS y mensajes no leídos

Registro de #49 (avisar de cada SMS recibido) y #50 (marcar como leídos y ver los no leídos). Surgen de usar la app como mensajería
principal: hasta aquí solo avisaban los mensajes de riesgo alto, y los recibidos quedaban sin leer para siempre. Última verificación:
2026-11-10, en emuladores de API 29 y de API 35 (Android 15).

## #49: un aviso por cada SMS

| Mensaje | Qué se ve |
|---|---|
| Riesgo alto | **solo** la alerta de riesgo (#10); no se suma un segundo aviso |
| Riesgo medio o bajo | un aviso de mensaje |
| El modelo no lo evaluó (muy corto, o análisis suspendido) | un aviso de mensaje |
| El análisis falló (por ejemplo, la biblioteca nativa del modelo) | un aviso de mensaje, y el fallo no se oculta: sigue llegando a quien llamó |

- **El aviso** va en su propio canal, "Mensajes", con importancia normal (la alerta de riesgo tiene el suyo, alto). Muestra el remitente
  y el texto del mensaje. **En la pantalla de bloqueo solo dice "Mensaje nuevo"**: ni remitente ni texto.
- **Tocarlo abre la conversación** del mensaje (no el detalle del análisis). Si el mensaje ya no está, se queda en la lista.
- **Un aviso por mensaje**, con la etiqueta `message` y el id del mensaje, para que no choque con la alerta de riesgo (que usa solo el id).
- Se avisa **después** de guardar el análisis, y un fallo al avisar no cambia el resultado del análisis.
- **Respeta el permiso de notificaciones**: sin él (Android 13 o superior) o con las notificaciones apagadas, no se muestra nada y solo se
  anota. No se registra el remitente ni el texto.
- La regla "riesgo alto → solo la alerta" vive en `AnalyzeReceivedMessage` (`domain.alerts`), no en la capa de Android.

## #50: leídos y no leídos

- Los mensajes **llegan sin leer** (ya era así) y **abrir su conversación los marca como leídos** en el buzón del sistema (`READ` y
  `SEEN`). Solo se marcan los **recibidos**; lo que envió la persona nunca cuenta como sin leer.
- **La lista distingue** las conversaciones con mensajes sin leer: el inicio del mensaje va en negrita y aparece un contador azul con
  cuántos recibidos siguen sin leer **en toda la conversación** (no solo el último). Un lector de pantalla lo lee como "3 sin leer".
- **Al leer se descartan los avisos**: el del mensaje y, si era de riesgo alto, su alerta de riesgo. También funciona con un mensaje que
  llega con la conversación abierta.
- Abrir el **detalle** de una alerta o del historial **no** marca el mensaje como leído: solo abrir su conversación.

### Un fallo que encontró la prueba de punta a punta

El primer intento no descartaba los avisos aunque el mensaje quedaba leído. **Marcar como leídos cambia el buzón, eso reinicia el efecto
que lo hacía, y el reinicio cancelaba la parte que descartaba los avisos antes de ejecutarla.** Ahora marcar y descartar van en el mismo
bloque. La prueba lo detecta: sin esa corrección falla esperando que el aviso desaparezca.

## Código

| Pieza | Capa |
|---|---|
| `IncomingMessageNotifier`, `AnalyzeReceivedMessage` | `domain.alerts` |
| `Conversation.unreadCount`, `SmsRow.unread`, `ConversationRepository.markRead` | `domain.messaging` |
| `AndroidMessageNotifier` (con `dismiss`), `TelephonyConversationRepository.markRead` | `data.alerts`, `data.messaging` |
| `ConversationRow` con contador, `MessagesRoute`, `MainActivity.conversationIntent` | `ui` |

No se agregó ningún permiso.

## Pruebas

- JVM (214 en total): `AnalyzeReceivedMessageTest` (el de riesgo alto no suma aviso; medio y bajo avisan; lo no evaluado avisa; si el
  análisis falla igual avisa y el fallo no se oculta; un fallo al avisar no cambia el resultado; el aviso va después de guardar) y
  `ConversationsTest` (el contador cubre toda la conversación y no solo el último mensaje).
- Dispositivo: `MessagesScreensTest` (quitada: ver [pruebas.md](pruebas.md)) (la lista con y sin contador) y, de punta a punta con SMS reales entregados,
  `SmsRoleInstrumentedTest`: `thenEachMessageIsNotifiedAndOpeningItsConversationReadsItAndClearsItsNotification` (un aviso para el que no es
  de riesgo alto y solo la alerta para el que sí; contenido del aviso y versión de pantalla de bloqueo; llegan sin leer; la lista lo dice;
  tocar el aviso abre la conversación, lee el mensaje y descarta su aviso; abrir la del de riesgo alto descarta su alerta),
  `thenAShortMessageIsAlsoNotifiedAndASentOneIsNeverUnread` y `roleThenNotificationsFollowTheAndroid13Permission`.
- **Android 13 o superior, comprobado en un emulador de API 35:** sin el permiso de notificaciones no se muestra ningún aviso ni alerta; al
  concederlo, se muestran. Esa prueba necesita el permiso sin conceder, y con Gradle ya estaba concedido al empezar (la prueba se omite),
  así que se corre en dos fases: instalar, `adb shell pm revoke com.app.smishing android.permission.POST_NOTIFICATIONS` y
  `adb shell am instrument -w -e class com.app.smishing.data.sms.SmsRoleInstrumentedTest#roleThenNotificationsFollowTheAndroid13Permission com.app.smishing.test/androidx.test.runner.AndroidJUnitRunner`.
  Pasó tres veces seguidas.
- A mano en API 35: llegó un SMS, apareció el aviso con el remitente y el texto, y al tocarlo el mensaje quedó leído.

## Lo que no se verificó

- **Un teléfono real**, y los sonidos y la vibración del canal "Mensajes" (el emulador no los distingue).
- **La versión de pantalla de bloqueo, vista de verdad:** se comprobó el contenido de la versión pública, no cómo la muestra cada fabricante.
- **Agrupar los avisos** de varios mensajes del mismo remitente: hoy hay un aviso por mensaje, como pide #49.
- **El nombre del contacto** (#53): el aviso y la lista muestran el número.
- **Responder desde el aviso**, o marcar como leído desde el aviso sin abrir la app: no están.
- Una vez, en la primera prueba de la negativa en API 35, aparecieron tres notificaciones con el permiso sin conceder; no se pudo
  reproducir en tres corridas posteriores (con el permiso revocado, ninguna). No se encontró la causa.
- **La prueba de envío de #6** (`thenAMessageIsSentToTheChosenRecipient...`) se omite fuera del primer emulador (5554): en el de API 35 falló
  esperando que el mensaje enviado a su propio número le llegara de vuelta, y no se investigó por qué. El envío **no se verificó en Android 15**.
- Los mensajes sin leer que dejaron versiones anteriores de la app en el teléfono **siguen sin leer** hasta que se abra su conversación.
