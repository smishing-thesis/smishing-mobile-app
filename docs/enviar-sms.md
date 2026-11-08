# Redactar y enviar SMS

Registro de #6 (RF-17): la persona redacta un SMS, se envía al destinatario elegido y aparece en su conversación. Última
verificación: 2026-11-08.

> **ATENCIÓN.** Enviar un SMS desde un teléfono real puede costar dinero según el plan. Y la app sigue sin ser apta para un
> teléfono con mensajes reales: no avisa de los mensajes nuevos y los MMS se pierden. Úsala como gestor de SMS solo en un
> emulador.

Las pantallas siguen el diseño "Mensajería" (Figma TP1): el botón **Escribir** de la lista, **Nuevo mensaje** y la barra para
escribir dentro de la conversación.

## Qué hace

1. En la lista de mensajes, **Escribir** abre **Nuevo mensaje**: el número del destinatario, el texto y **Enviar mensaje**.
   El botón no se activa hasta tener las dos cosas.
2. En una conversación abierta, la barra de abajo envía una respuesta al mismo número; no envía con el texto vacío.
3. Al enviar, el mensaje se guarda **primero en el buzón como saliente**, así aparece al instante en su conversación a la
   derecha, y se entrega a `SmsManager` (que lo divide en partes si es largo).
4. Cuando la radio responde, el mensaje pasa a **enviado**; si falla, a **fallido** y la conversación muestra "No se pudo
   enviar" debajo de él.
5. Desde "Nuevo mensaje", al enviar la app abre la conversación del destinatario, con el mensaje ya dentro.

**Quién guarda el mensaje enviado.** Android no guarda por la app predeterminada los SMS que envía: tiene que hacerlo ella,
igual que con los recibidos (#4). Por eso se inserta en `Telephony.Sms.Outbox` antes de enviar; el sistema le asigna la
conversación del destinatario.

**El número.** Se acepta como se escribe ("987 654 321") y se envía normalizado (`987654321`; con `+` inicial opcional).
Se rechaza lo que no es un número: letras, un `+` fuera del inicio, menos de 3 o más de 15 dígitos (el máximo de un número
internacional). Los códigos cortos de 3 dígitos o más pasan.

## Código

| Pieza | Capa | Qué hace |
|---|---|---|
| `normalizeRecipient`, `SendSms`, `SendOutcome`, `SmsSender` | `domain.messaging` | valida el número y el texto y le pasa el mensaje al enviador; el resultado es `Sent`, `InvalidRecipient`, `EmptyMessage`, `NotAllowed` o `Failed` |
| `AndroidSmsSender` | `data.sms` | guarda el saliente, lo entrega a `SmsManager` y pide el resultado por `PendingIntent` |
| `SmsSentReceiver` | `data.sms` | recibe el resultado y pasa el mensaje de saliente a enviado, o a fallido |
| `NewMessageScreen`, `SendErrorText` | `ui.messages` | la pantalla "Nuevo mensaje" y los textos de error |
| `ConversationScreen`, `ConversationListScreen`, `MessagesRoute` | `ui.messages` | la barra para escribir, "No se pudo enviar", el botón Escribir y el envío fuera del hilo principal |

**Privacidad.** El texto y el número no se registran en logs: el receptor del resultado solo anota si salió bien o el código.

## Permiso

`SEND_SMS`, documentado con su motivo en [permisos-sms.md](permisos-sms.md). Con el rol de SMS Android lo otorga solo. Si la
persona lo quitó, al enviar la app lo pide y debe tocar **Enviar** otra vez. **No forma parte de la compuerta del análisis**:
analizar mensajes no necesita enviarlos, así que quitar este permiso no suspende el análisis.

## Decisiones que conviene revisar

- **Solo número, sin contactos.** El diseño tiene un "+" para elegir un contacto y el campo dice "Número o nombre de
  contacto". Eso exige `READ_CONTACTS`, que ninguna historia hecha justifica: hoy el campo dice "Número de teléfono" y no
  hay "+".
- **Textos fuera del diseño:** "No se pudo enviar" (bajo un mensaje fallido), "Escriba un número de teléfono válido.", "No se
  pudo enviar el mensaje. Inténtelo de nuevo." y el aviso de falta de permiso. Revisarlos con quien diseñó.
- **Un mensaje largo pasa a "enviado" con la primera parte que sale bien** y a "fallido" si alguna falla; no espera a todas.
- **No hay reintento** de un mensaje fallido ni confirmación de entrega al destinatario (solo que la radio lo aceptó).

## Cómo verificar

Con el emulador de API 29 abierto y `JAVA_HOME` en un JDK de 17 a 23 (ver [entorno.md](entorno.md)):

```powershell
.\gradlew.bat testDebugUnitTest --rerun
.\gradlew.bat connectedDebugAndroidTest --rerun
```

- `SendSmsTest` (JVM, 10 pruebas): el número se normaliza y lo que no es número se rechaza; el texto vacío o en blanco no
  llega al enviador; el texto se envía como se escribió; los fallos del enviador se pasan.
- `NewMessageScreenTest` (dispositivo, 5) y `MessagesScreensTest` (dispositivo, 4 nuevas): la pantalla "Nuevo mensaje", el
  botón que solo se activa con número y texto, el botón Escribir, la barra para escribir, "No se pudo enviar" y los errores.
- `SmsRoleInstrumentedTest.thenAMessageIsSentToTheChosenRecipientAndAppearsInItsConversation` (dispositivo): **la prueba
  funcional**. Con el rol, por la interfaz: Escribir, número `5554` (el propio emulador), texto con acento, Enviar. Comprueba
  que el mensaje aparece en su conversación como enviado, que el sistema lo marca **enviado** (no fallido) y que el
  destinatario lo **recibió una sola vez** (el emulador entrega a sí mismo lo que se envía a su número). Borra sus datos.

## Resultado (2026-11-08)

Emulador Pixel 4, Android 10 (API 29), x86_64, con Temurin 17 y `--rerun`.

| Ejecución | Pruebas | Fallos |
|---|---|---|
| `testDebugUnitTest` (JVM) | 71 (10 nuevas) | 0 |
| `connectedDebugAndroidTest` | 56 (10 nuevas): 55 ejecutadas y 1 omitida a propósito | 0 |

**La prueba no es vacía.** Se quitó a propósito la entrega del mensaje a `SmsManager` y la prueba funcional falló: el mensaje
se quedó como saliente (tipo 4) y nunca llegó a enviado (tipo 2). Se revirtió.

**Lo que enseñó el primer intento.** La prueba funcional falló al principio porque eligió la conversación equivocada:
el emulador **entregó el mensaje a sí mismo**, así que apareció una copia recibida con el mismo texto. Es la evidencia de que
el mensaje salió hacia el destinatario. La copia recibida queda en una conversación distinta de la del número `5554`,
porque llega con el número completo del emulador (`+15555215554`).

**Revisión manual en el emulador** (capturas):
- La lista con el botón **Escribir**, "Nuevo mensaje" (barra azul, "Para:", área de texto y el botón gris hasta tener
  número y texto) y la conversación con la barra para escribir coinciden con el diseño. Esta vez **sí se vio una burbuja enviada**
  (azul claro, a la derecha).
- **Sin el permiso:** con `SEND_SMS` quitado (`pm revoke`), al tocar Enviar la app mostró el aviso de falta de permiso y lo
  pidió. Android lo **devolvió en silencio**, sin diálogo (`granted=true`, `GRANTED_BY_ROLE`), porque la app tiene el rol. Al
  tocar Enviar otra vez, el mensaje salió. Esa revisión mostró que el texto "Acepte la solicitud" era engañoso, y se cambió.
- Al quitar el permiso Android mató la app, y al reabrirla volvió a "Nuevo mensaje": el estado se conservó.

## Lo que no cubre

- **No se probó con una operadora ni en un teléfono real**: solo el emulador enviándose a sí mismo. No se sabe cómo
  responde una red real, ni los costos, ni la doble SIM.
- **No hay confirmación de entrega** al destinatario: el "enviado" significa que la radio aceptó el mensaje.
- **No se probó un mensaje largo en varias partes**: el código usa `divideMessage` y `sendMultipartTextMessage`, pero la
  prueba envía un mensaje corto (con acento, que Android codifica distinto).
- **Un mensaje puede quedar como saliente** si la radio nunca responde: no hay tiempo límite ni reintento.
- **Pedir `SEND_SMS`** solo se vio con el rol concedido, donde Android lo devuelve en silencio. No se vio el diálogo que
  aparecería si la app no tuviera el rol (en una instalación de desarrollo, sin rol).
- Quedan en el buzón del emulador mensajes sintéticos de la revisión manual (destino `5554`, "Hola de prueba").
- **No hay nombres de contacto** ni el "+" del diseño (ver arriba).
- Solo API 29, x86_64: no se probó en API 35, ARM ni en un teléfono físico. No se probó con TalkBack ni con letra grande.
- La barra para escribir es de una línea; un texto largo no se muestra en varias líneas mientras se escribe.
