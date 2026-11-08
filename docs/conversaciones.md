# Conversaciones

Registro de #5 (RF-16): la persona consulta sus conversaciones, con remitente, fecha y los mensajes recibidos y
enviados, sin salir de la app. Última verificación: 2026-11-08.

> **ATENCIÓN.** La app sigue sin ser apta para un teléfono con mensajes reales: no avisa de los mensajes nuevos, no
> permite responder (#6) y los MMS se pierden. Úsala como gestor de SMS solo en un emulador.

Las pantallas siguen el diseño "Mensajería" (Figma TP1): la lista de **Mensajes** y la **conversación**.

## Qué hace

1. Con el rol de SMS y los permisos concedidos (#2 y #3), la app abre la **lista de mensajes**: una fila por conversación,
   con el remitente, la fecha y el inicio del último mensaje. La más reciente va primero.
2. Al tocar una fila se abre la **conversación**: arriba el remitente con "SMS" debajo, y los mensajes del más antiguo al
   más reciente, los **recibidos a la izquierda** (gris) y los **enviados a la derecha** (azul claro). Abre en el último.
3. "Atrás" (el botón de la barra o el gesto del sistema) vuelve a la lista.
4. Si llega un SMS con la lista abierta, **aparece solo**: la app observa el buzón del sistema y vuelve a leerlo.

**La fecha** sigue el diseño: la hora si es de hoy ("14:20"), "Ayer", el mes y el día si es de este año ("Marzo 12") y la
fecha completa si es de antes ("12/03/2025"). El día se decide en la zona horaria del teléfono.

**Qué es "recibido" y qué es "enviado".** Recibido es solo el tipo "buzón de entrada" de Android. Todo lo demás (enviado,
en cola, saliente, fallido) se muestra a la derecha, como del usuario. Los borradores no se muestran.

## Código

| Pieza | Capa | Qué hace |
|---|---|---|
| `Conversation`, `ConversationMessage`, `MessageDirection`, `latestPerConversation`, `ConversationRepository` | `domain.messaging` | los datos y la regla que deja el último mensaje de cada conversación |
| `formatConversationDate` | `domain.messaging` | la fecha de la lista (recibe el texto de "Ayer" y la zona horaria, no los fija) |
| `TelephonyConversationRepository` | `data.messaging` | lee `Telephony.Sms` (necesita `READ_SMS`); las conversaciones son las del sistema (`thread_id`) |
| `ConversationListScreen`, `ConversationScreen` | `ui.messages` | las dos pantallas, con `ConversationRow`, `MessageBubble` y las barras de [design-system.md](design-system.md) |
| `MessagesRoute` | `ui.messages` | une las pantallas con el buzón: lee fuera del hilo principal, observa el buzón y guarda la conversación abierta al girar la pantalla |
| `MainActivity` | `ui` | con el rol y los permisos, muestra `MessagesRoute` |

**La conversación la define el sistema**, no la app: se agrupa por el `thread_id` que Android asigna (ver
[recepcion-sms.md](recepcion-sms.md)).

**Privacidad.** `Conversation` y `ConversationMessage` no muestran el remitente ni el texto en `toString`. Nada de esto
se registra en logs.

**Accesibilidad.** Cada burbuja tiene la descripción de estado "Recibido" o "Enviado" para el lector de pantalla,
porque el color y el lado no lo transmiten. El botón de volver tiene su descripción. No se probó con TalkBack.

## Decisiones que conviene revisar

- **Se muestra el número, no el nombre del contacto.** El diseño muestra nombres ("Mamá", "Carlos"), pero eso exige el
  permiso `READ_CONTACTS`, que esta historia no justifica: pedirlo es una decisión aparte. Hoy se ve el número tal como lo
  guarda el sistema, sin darle el formato del diseño ("987 654 321").
- **Texto fuera del diseño:** el de la lista vacía ("Todavía no tiene conversaciones."). Revisarlo con quien diseñó.
- **Lo que el diseño muestra y #5 no incluye**, porque es de otras historias: el chip y el aviso de riesgo ("Cuidado:
  Posible estafa", "Ver riesgo": #10 y #12), el botón "Escribir", la barra para redactar y "Nuevo mensaje" (#6), la
  búsqueda y el menú de la conversación, y la navegación inferior (sus otras pestañas todavía no existen). Hoy, tras el
  onboarding, la lista de mensajes es la pantalla principal.
- **Abrir una conversación no la marca como leída**: no está en la historia. Los mensajes recibidos quedan sin leer.

## Cómo verificar

Con el emulador de API 29 abierto y `JAVA_HOME` en un JDK de 17 a 23 (ver [entorno.md](entorno.md)):

```powershell
.\gradlew.bat testDebugUnitTest --rerun
.\gradlew.bat connectedDebugAndroidTest --rerun
```

- `ConversationsTest` y `ConversationDateTest` (JVM, 10 pruebas): una conversación por hilo con su último mensaje, la más
  reciente primero, y la regla de la fecha (hoy, ayer, este año, otro año, año nuevo y zona horaria).
- `MessagesScreensTest` (dispositivo, 6): las dos pantallas con datos sintéticos: remitente, fecha y fragmento; tocar abre; la
  lista vacía y la carga; recibidos a la izquierda y enviados a la derecha, en orden; el botón de volver.
- `SmsRoleInstrumentedTest.thenTheConversationsShowReceivedAndSentMessages` (dispositivo): **la prueba funcional**. Con el
  rol, inserta mensajes enviados en el buzón del sistema, entrega uno recibido al receptor, abre la app y comprueba: la
  lista muestra remitente, fecha y último mensaje; la conversación muestra recibidos y enviados en orden; y un SMS que
  llega con la lista abierta aparece sin reabrir. Borra sus datos.
- `SmsRoleInstrumentedTest.thenALargeInboxStillListsOneConversationPerSender` (dispositivo): con 3000 mensajes de 150
  remitentes, una conversación por remitente con su último mensaje. Registra los tiempos en `conversations-large-inbox.json`.

## Resultado (2026-11-08)

Emulador Pixel 4, Android 10 (API 29), x86_64, con Temurin 17 y `--rerun`.

| Ejecución | Pruebas | Fallos |
|---|---|---|
| `testDebugUnitTest` (JVM) | 61 (10 nuevas) | 0 |
| `connectedDebugAndroidTest` | 46 (8 nuevas): 45 ejecutadas y 1 omitida a propósito | 0 |

**La prueba no es vacía.** Se marcaron a propósito todos los mensajes como recibidos y la prueba funcional falló. Se revirtió.

**Buzón grande** (3000 mensajes sintéticos, 150 conversaciones; emulador x86_64 en un PC, no un teléfono de gama baja):
leer la lista tardó **39 ms** y abrir una conversación de 20 mensajes, **10 ms**.

**Lo que enseñó el primer intento.** La prueba del buzón grande falló al principio: salían 146 conversaciones en vez de
150. No era un defecto de la app sino de los datos: los números sintéticos eran casi todos ceros, y el proveedor de SMS de
Android compara los números de forma laxa y fusionó algunos en la misma conversación. Se cambiaron por números bien
distintos. Es útil saberlo: **dos remitentes con números parecidos pueden terminar en la misma conversación**, porque
así lo decide el sistema.

## Lo que no cubre

- **No hay nombres de contacto** (ver arriba), ni el formato de número del diseño.
- **Los MMS no se leen**: solo `Telephony.Sms`. Una conversación que tuvo MMS no los muestra.
- **Toda la lista se lee del buzón entero** (se agrupa en la app por `thread_id`), no con una consulta por conversación.
  Con 3000 mensajes tardó 39 ms en el emulador, pero **no se midió en un teléfono de gama baja ni con decenas de miles
  de mensajes**; con buzones muy grandes puede crecer. Cada vez que llega o cambia un mensaje la lista se vuelve a leer
  completa.
- **No se probó** con letra grande, TalkBack, girar la pantalla con una conversación abierta, texto muy largo, ni
  caracteres de derecha a izquierda. La barra de estado y la de navegación del sistema no se revisaron en otras versiones
  (hoy no se usa pantalla completa).
- Las fechas de la lista usan siempre español, como los textos del diseño; no siguen el idioma del teléfono.
- Solo API 29, x86_64: no se probó en API 35, ARM ni en un teléfono físico.
- **Revisión visual a ojo, no píxel a píxel.** Se capturó la app en el emulador con SMS sintéticos enviados desde la consola:
  la lista (remitente, hora, fragmento, divisores) y la conversación (remitente con "SMS", flecha de volver, burbujas
  recibidas en gris a la izquierda) coinciden con el diseño. **No se vio en pantalla una burbuja enviada** (azul, a la
  derecha): desde `adb` no se pueden insertar mensajes enviados. Solo la comprueban las pruebas, por posición y descripción.
- Quedan en el buzón del emulador filas sintéticas de #4 y de esta revisión (remitentes `1000000000x`); las pruebas de #5
  borran las suyas.
