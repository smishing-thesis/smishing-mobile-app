# Ajustes y modo de mitigación

Registro de #13 (modo de mitigación), #25 (revisar permisos) y la parte hecha de #31 (pantalla de ajustes). Última verificación:
2026-11-09, en el emulador de API 29.

## El modo

Tres modos, los del diseño de Ajustes, para un mensaje de **riesgo alto**:

| Modo | Texto en pantalla | Qué hace con un mensaje de riesgo alto |
|---|---|---|
| `Automatic` | Protegerme automáticamente | actúa sin preguntar (bloquear el número y retirar el mensaje) |
| `Ask` | Preguntarme antes de actuar | muestra las opciones para que la persona decida |
| `Disabled` | Solo avisarme | avisa y no toca el mensaje |

- **Por defecto es `Ask`.** El modo automático retira mensajes del buzón: no debe activarse sin que la persona lo elija.
- **Solo el nivel alto actúa** (`MitigationPolicy.ACTIONABLE_LEVELS`). Medio y bajo no disparan ninguna acción, sea cual sea el modo.
- **La alerta (#10) aparece con cualquier modo.** El modo solo decide qué se hace con el mensaje.
- Se guarda en un **DataStore de Jetpack** (archivo `settings`), como define la arquitectura, con un `id` estable (`automatic`,
  `ask`, `disabled`) y no el nombre del enumerado. Si falta o no se reconoce, vale el modo por defecto.

## Cuándo se consulta

`AnalyzeReceivedMessage` hace, en este orden: **guardar el registro → consultar el modo → actuar → avisar**. El modo se lee
**antes de cualquier acción** sobre el mensaje, y solo si el nivel es alto. Si no se puede leer, no se actúa (y se avisa igual);
si la acción falla, se avisa igual. Ambos errores se anotan sin el texto del mensaje.

Quien ejecuta la decisión es un `Mitigator`. Con `Act` bloquea al remitente y retira el mensaje (`ApplyMitigation`, #14 y #15; ver
[mitigacion.md](mitigacion.md)); con `AskUser` el detalle ofrece los botones y con `DoNothing` no se toca nada.

## La pantalla

Cuarta pestaña de la barra inferior (tras Inicio, Mensajes e Historial), "Ajustes" (solo cuando la app ya tiene el rol y los permisos de SMS):

- **Modo:** las tres opciones con su explicación; la actual aparece seleccionada y al elegir otra se guarda en el acto.
- **Permisos (#25):** la aplicación de mensajería principal y el permiso de lectura, como en el mockup, más **Notificaciones** (también
  son un requisito de la protección). Cada uno, concedido o no (también dicho a los lectores de pantalla). Si no está concedido, la
  pantalla dice **qué función queda limitada** y ofrece "Abrir ajustes", que lleva a donde se cambia. Enviar mensajes no se muestra (#61).
- El estado de los permisos se **vuelve a leer cada vez que la persona regresa a la app**, para no mostrar un estado viejo.

| Permiso | Si falta | "Abrir ajustes" lleva a |
|---|---|---|
| Aplicación de mensajería principal | no se reciben ni se guardan los mensajes, y no se analizan | aplicaciones predeterminadas |
| Leer y recibir mensajes | no se leen ni analizan los mensajes que llegan | detalles de la app |
| Enviar mensajes (ya no se muestra en Ajustes) | no se pueden enviar mensajes | detalles de la app |
| Notificaciones | no se avisa de un mensaje con posible estafa | ajustes de notificaciones de la app |

No se pide ningún permiso nuevo. **Falta "Compartir reportes de estafas"** (el aporte al corpus, fase 2: #18, #26, #27), por eso
#31 sigue abierto.

## Pruebas

- JVM: `MitigationPolicyTest` (tabla modo × nivel, modo por defecto, ids estables) y `AnalyzeReceivedMessageTest` (el modo se lee
  después de guardar y antes de actuar o avisar; con `Disabled` o con nivel medio/bajo no se llama al ejecutor; un modo ilegible o
  una acción que falla no impiden la alerta). 162 pruebas, 0 fallos (1 omitida por diseño).
- Dispositivo: `DataStoreMitigationSettingsInstrumentedTest` (por defecto `Ask`, se lee lo guardado, sobrevive a reabrir el
  almacén, valor desconocido → `Ask`, avisa de cambios), `SettingsScreenTest` (quitada: ver [pruebas.md](pruebas.md)) (modos, permisos, explicaciones, "Abrir ajustes") y,
  de punta a punta, `SmsRoleInstrumentedTest.thenTheChosenModeDecidesWhatHappensToARiskyMessage`: se elige el modo en la pestaña,
  se guarda en el DataStore real y el receptor, con un SMS real entregado, actúa o no según el modo.
- Suite completa en el emulador (API 29): 134 pruebas, 0 fallos, 2 omitidas (una por diseño y la del primer paso del rol, porque el
  rol ya estaba concedido tras la revisión a mano).
- A mano en el emulador: la pestaña muestra el modo por defecto y los permisos; con `pm revoke` de `SEND_SMS` aparece "no concedido"
  con su explicación, y "Abrir ajustes" abre los detalles de la app.
- Mutación: hacer que `Disabled` actúe hace fallar 3 pruebas JVM (revertido).

## Lo que no se verificó

- **Revocar un permiso en vivo** con la app abierta: Android mata el proceso al quitar un permiso restringido. El estado "no
  concedido" se probó con la pantalla alimentada con datos (`SettingsScreenTest` (quitada: ver [pruebas.md](pruebas.md))), no leyéndolo de un permiso realmente quitado.
- Android 13 o superior (permiso de notificaciones en tiempo de ejecución), TalkBack con esta pantalla y teléfono real.
- Que "Abrir ajustes" aterrice en la pantalla esperada en cada fabricante: solo se construyen las intenciones estándar.
- El DataStore no está cifrado ni se excluyó de copias de seguridad: guarda solo el modo, sin datos de mensajes.
