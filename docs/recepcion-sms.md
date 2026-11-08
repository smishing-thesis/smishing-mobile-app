# Recepción de SMS

Registro de #4 (RF-02, RF-16): un SMS entrante queda una sola vez en la conversación correcta. Última verificación:
2026-11-08.

> **ATENCIÓN.** Aunque la app ya guarda los SMS entrantes, **sigue sin ser apta para un teléfono con mensajes reales**:
> aunque muestra las conversaciones (#5), no avisa de que llegó un mensaje y los MMS se pierden
> (ver "Lo que no cubre").
> Úsala como gestor de SMS solo en un emulador.

## Qué hace

1. Android entrega `SMS_DELIVER` solo a la app predeterminada que tiene `RECEIVE_SMS` (ver [permisos-sms.md](permisos-sms.md)).
2. `SmsDeliverReceiver` arma los fragmentos del mensaje (un SMS largo llega en varios) en un solo `IncomingSms`.
3. Si el buzón ya tiene ese mensaje, lo descarta; si no, lo guarda en `Telephony.Sms.Inbox`.
4. **La conversación la asigna el sistema**: al insertar el mensaje, el proveedor de SMS le pone el `thread_id` de la
   conversación de su remitente (o crea una). La app no inventa conversaciones.

El mensaje se guarda sin leer (`read=0`, `seen=0`), con la hora de envío del operador (`date_sent`) y la de llegada (`date`).

## Código

| Pieza | Capa | Qué hace |
|---|---|---|
| `IncomingSms`, `SmsPart`, `assembleIncoming` | `domain.sms` | el mensaje completo y la regla que une los fragmentos |
| `SmsInbox`, `ReceiveIncomingSms`, `ReceiveOutcome` | `domain.sms` | el buzón como contrato y el caso de uso: guardar una sola vez (`Stored`, `Duplicate`, `Ignored`) |
| `TelephonySmsInbox` | `data.sms` | consulta y escribe en `Telephony.Sms.Inbox` |
| `SmsDeliverReceiver` | `data.sms` | recibe `SMS_DELIVER`; trabaja fuera del hilo principal con `goAsync()` |

**Qué cuenta como el mismo mensaje.** Remitente, texto y hora de envío del operador (`date_sent`, con precisión de
segundo). Dos mensajes idénticos del mismo remitente en el mismo segundo se tomarían por uno solo: es el precio de la
regla y es muy improbable; el texto igual en otro momento, o de otro remitente, es un mensaje nuevo.

**Privacidad.** `IncomingSms.toString()` no incluye remitente ni texto. El receptor solo registra el resultado
(`Stored`, `Duplicate`, `Ignored`) y el número de fragmentos; si falla, solo el tipo de excepción.

**El análisis todavía no se dispara aquí.** El diseño dice que el registro del mensaje dispara el análisis, pero hoy no hay
dónde guardar un resultado (Room, señales, alertas y el historial son #8 a #12): clasificar ahora descartaría el
resultado. Cuando esas historias existan, el análisis se conecta a este punto y **debe usar `GatedMessageClassifier`**
(ver [permisos-sms.md](permisos-sms.md)), no el clasificador directo.

## Cómo verificar

Con el emulador de API 29 abierto y `JAVA_HOME` en un JDK de 17 a 23 (ver [entorno.md](entorno.md)):

```powershell
.\gradlew.bat testDebugUnitTest --rerun
.\gradlew.bat connectedDebugAndroidTest --rerun
```

- `ReceiveIncomingSmsTest` (JVM, 8 pruebas): un mensaje se guarda una vez, las partes se unen, la entrega repetida es
  duplicado, el mismo texto en otra hora o de otro remitente es nuevo, sin remitente o sin texto no se guarda, y el
  mensaje no sale en su `toString`.
- `SmsRoleInstrumentedTest.thenReceivedSmsLandsOnceInItsConversation` (dispositivo): concede el rol, entrega al receptor
  `SMS_DELIVER` con PDUs reales (`SmsPdu` los arma) de tres remitentes ficticios y lee el buzón del sistema. Comprueba:
  el mensaje entregado dos veces queda una vez; dos mensajes del mismo remitente van al mismo `thread_id` y el de otro
  remitente a otro; un mensaje de dos PDUs queda como una fila; la hora de envío es la del PDU; quedan sin leer. Borra
  sus mensajes al terminar.
- Las pruebas de esa clase corren en orden (`@FixMethodOrder`): el flujo del rol va antes que la recepción, que necesita el rol.

### Entrega real con la consola del emulador

Las pruebas de arriba entregan el PDU al receptor directamente. Para ver al **sistema** entregarlo:

```powershell
adb shell cmd role add-role-holder --user 0 android.app.role.SMS com.app.smishing
adb emu sms send 10000000001 "Mensaje de prueba."
adb shell content query --uri content://sms/inbox --projection address:thread_id:read:body --where "address like '%10000000001%'"
adb shell cmd role remove-role-holder --user 0 android.app.role.SMS com.app.smishing
```

## Resultado (2026-11-08)

Emulador Pixel 4, Android 10 (API 29), x86_64, con Temurin 17 y `--rerun`.

| Ejecución | Pruebas | Fallos |
|---|---|---|
| `testDebugUnitTest` (JVM) | 51 (8 nuevas) | 0 |
| `connectedDebugAndroidTest` | 38 (1 nueva): 37 ejecutadas y 1 omitida a propósito | 0 |

**La prueba no es vacía.** Se desactivó a propósito la comprobación de duplicados (`contains` siempre `false`) y la
prueba falló (`expected:<2> but was:<3>`). Se revirtió.

**Entrega real del sistema** (`adb emu sms send`, remitentes ficticios, con el rol concedido):

| Envío | Resultado en el buzón |
|---|---|
| 2 mensajes del remitente A | 2 filas, mismo `thread_id` (9), sin leer |
| 1 mensaje del remitente B | 1 fila, otro `thread_id` (10) |
| 1 mensaje de 268 caracteres (el sistema lo entregó en 2 fragmentos) | 1 sola fila, `thread_id` propio (11) |

**`RECEIVE_SMS` de punta a punta** (deuda de #3): con el rol y el permiso quitado con `pm revoke`, un SMS real **no llegó**
(0 filas); devuelto con `pm grant`, el siguiente sí (1 fila). Al terminar, el rol volvió a Google Messages.

## Lo que no cubre

- **Los MMS se pierden.** `MmsDeliverReceiver` sigue vacío: como app predeterminada, los MMS entrantes no se guardan. No
  hay historia de MMS en el backlog; si se quiere cubrirlos, falta una.
- **No hay notificación** de mensaje nuevo: una persona solo vería un SMS si abre la app (la lista está en [conversaciones.md](conversaciones.md)).
- **El análisis no está conectado** (ver arriba): hoy ningún mensaje recibido se clasifica.
- **Mismo mensaje, mismo segundo**: se toma por duplicado (ver arriba).
- **Entrega real solo desde el emulador.** El mensaje largo llegó en dos fragmentos, pero no se probó con un operador, con
  SMS de clase 0 (flash), con doble SIM ni con otras codificaciones (UCS-2: acentos y emojis) en el PDU de la prueba.
  Los mensajes enviados con la consola del emulador solo se probaron con texto sin acentos.
- **Solo API 29, x86_64**: no se probó en API 35, en ARM ni en un teléfono físico; el proveedor de SMS puede comportarse
  distinto en capas de fabricante.
- **Quedan 5 filas sintéticas** (remitentes `1000000000x`) en el buzón del emulador: solo la app predeterminada puede
  borrar mensajes y las envié desde la consola. No afectan a las pruebas.
- No se midió la demora entre la llegada y el guardado.
