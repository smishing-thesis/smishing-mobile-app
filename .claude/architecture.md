# Arquitectura de la app (según la memoria)

Resumen de lo definido en la memoria de la tesis. Si una decisión de implementación contradice este archivo,
actualizarlo en el mismo PR. Lo que la memoria no fija está marcado como por decidir.

## Componentes principales

| Componente | Responsabilidad |
|---|---|
| Receptor de SMS y MMS | entrega cada mensaje entrante (interfaz `IReceptor`) |
| Registro del mensaje entrante | lo guarda en el buzón del dispositivo (`IBuzón`) y dispara el análisis |
| Análisis | verifica con `IRol` que la app sigue siendo la predeterminada y luego clasifica con el modelo local |
| Consulta de conversaciones / Envío | pantallas de conversaciones y redacción; el envío usa el Emisor de SMS (`IEmisor`) |
| Mitigación | bloquea al remitente o retira el mensaje según el modo elegido; consulta las preferencias antes de actuar |
| Compartición | verifica el consentimiento (`IPreferenciasRepositorio`), anonimiza y entrega al Servicio de corpus (`ICorpus`) |
| Anonimización | reglas de desidentificación en el teléfono, alineadas con `sms-anonymizer` |
| Servicio de corpus | único componente que habla con el backend, por HTTPS, y solo con mensajes ya anonimizados |
| Limitador de envíos | acota cuántos aportes se admiten en una ventana de tiempo |
| Gestor de rol y permisos | rol de app predeterminada y permisos; emite las solicitudes al sistema |
| Preferencias del usuario | modo de mitigación y estado del consentimiento |

Flujo de lectura y análisis, de mensajería y de compartición son independientes entre sí. La compartición nunca
participa en la clasificación.

## Almacenamiento local

| Información | Mecanismo |
|---|---|
| Mensajes analizados, resultados de análisis, señales detectadas, acciones de mitigación | Room sobre SQLite |
| Modo de mitigación y registro de consentimiento | DataStore de Jetpack |
| Modelo | archivo `.tflite` empaquetado en la app (inmutable; la firma de la plataforma es su integridad) |

Entidades locales:

- **Mensaje analizado** (1:1) **Resultado de análisis** (probabilidad, nivel de riesgo, seguro o sospechoso, versión del modelo, fecha).
- **Resultado de análisis** (1:N) **Señal detectada** (tipo, como urgencia, suplantación, enlace externo o solicitud de datos, y el fragmento).
- **Mensaje analizado** (1:N) **Acción de mitigación** (bloqueo o retiro, aplicada o revertida, fecha).
- Configuración y consentimiento no se relacionan con las demás entidades.

Reglas:

- Borrar un mensaje analizado borra en cascada sus resultados, señales y acciones.
- Una acción de mitigación solo se registra sobre un mensaje cuyo resultado vigente es sospechoso.
- Revertir una acción cambia su estado a revertida y no borra el registro: así se distinguen los bloqueos de la app
  de los que hizo la persona, y solo los de la app se revierten al marcar un mensaje como seguro.
- El resultado guarda la versión del modelo como referencia lógica, no como clave foránea.
- El contenido del mensaje se conserva en la base local aunque se retire del buzón, para que el historial siga
  mostrando qué se detectó y por qué.
- Si no hay consentimiento activo, no se envía nada. Esa comprobación es de la app.

## Por decidir

Inyección de dependencias, módulos de Gradle y cómo se asignan los niveles de riesgo a partir del puntaje del modelo y las
señales (#9).

## Decidido al implementar

- **Paquetes por capas** bajo `com.app.smishing`: `domain` (lógica en Kotlin puro, sin dependencias de Android) y
  `data` (lectura de archivos, assets, Room, DataStore). Un solo módulo de Gradle (`app`) por ahora.
- **JSON:** `org.json` (viene en Android); en pruebas de la JVM se añade como dependencia de prueba.
- **Clasificación:** el contrato `MessageClassifier` vive en `domain`; `OnDeviceClassifier` (en `data`) lo implementa con
  LiteRT. El modelo se carga una vez y las llamadas se serializan. Cómo se comparte la instancia en la app sigue
  por decidir con la inyección de dependencias. Detalle en [docs/clasificacion-local.md](../docs/clasificacion-local.md).
- **UI:** Jetpack Compose con Material 3. La librería de componentes del diseño vive en el paquete
  `com.app.smishing.ui.designsystem` (dentro de `app`; se puede extraer a un módulo después). Los componentes
  reciben sus textos por parámetro: las pantallas los toman de recursos de cadenas. Solo tema claro, como el diseño.
  Detalle en [docs/design-system.md](../docs/design-system.md).
- **App de SMS predeterminada:** el estado del rol (`SmsRoleState`) está en `domain.sms`; `AndroidSmsRoleRepository` y los
  receptores y el servicio que Android exige declarar están en `data.sms`; las pantallas y las actividades, en `ui`.
  El receptor de SMS guarda los mensajes desde #4; el de MMS y el servicio siguen vacíos y no hay aviso de mensajes
  nuevos, así que solo debe usarse como gestor de SMS en emulador. Detalle en
  [docs/gestor-sms.md](../docs/gestor-sms.md).
- **Recepción de SMS:** `SmsDeliverReceiver` arma el mensaje (`assembleIncoming`, une las partes) y `ReceiveIncomingSms` lo
  guarda una sola vez en el buzón del sistema (`Telephony.Sms.Inbox`, vía `SmsInbox`/`TelephonySmsInbox`); el sistema le
  asigna la conversación por remitente. Un mensaje es el mismo si coinciden remitente, texto y `date_sent`. El análisis aún
  no se dispara desde aquí: falta dónde guardar resultados (#8 a #12). Detalle en
  [docs/recepcion-sms.md](../docs/recepcion-sms.md).
- **Señales (#8):** el modelo solo devuelve un puntaje, así que las señales (urgencia, suplantación, enlace y solicitud de
  datos) se identifican con reglas escritas a mano en `signal_rules.json` (assets, versionado), que aplica
  `RuleBasedSignalDetector` (`domain.signals`). Cada señal lleva el id de su regla y el análisis, la versión de las reglas,
  para que el nivel de riesgo (#9) sea trazable. Aún no se muestran (#11) ni se calculan sobre los mensajes recibidos (#12).
  Detalle y límites en [docs/senales.md](../docs/senales.md).
- **Conversaciones:** la lista de mensajes y la conversación (`ui.messages`) leen el buzón del sistema con
  `TelephonyConversationRepository` (`data.messaging`), agrupando por el `thread_id` que asigna Android; `domain.messaging`
  tiene los datos y la fecha de la lista. Se vuelve a leer cuando el buzón cambia. Hoy se muestra el número y no el
  nombre del contacto (exigiría `READ_CONTACTS`, aún no decidido). Detalle en
  [docs/conversaciones.md](../docs/conversaciones.md).
- **Envío de SMS:** `SendSms` (`domain.messaging`) valida el número (`normalizeRecipient`) y el texto; `AndroidSmsSender`
  (`data.sms`) guarda el mensaje como saliente en el buzón (la app predeterminada debe hacerlo ella), lo entrega a
  `SmsManager` y `SmsSentReceiver` lo pasa a enviado o fallido según la respuesta de la radio. Pantallas: Escribir, Nuevo
  mensaje y la barra de la conversación. `SEND_SMS` no está en la compuerta del análisis. Detalle en
  [docs/enviar-sms.md](../docs/enviar-sms.md).
- **Permisos y suspensión del análisis:** la app pide solo `READ_SMS` y `RECEIVE_SMS`, después del rol de SMS, que ya los
  concede. `AnalysisGate` (en `domain.sms`) dice si el análisis está activo, que requiere el rol y todos los permisos, y
  `GatedMessageClassifier` suspende la clasificación si falta algo. Quien reciba los SMS (#4) debe usar el clasificador con
  compuerta. Android mata el proceso al quitar un permiso, así que las pruebas instrumentadas no pueden revocar: se
  prueba en dos fases. Detalle en [docs/permisos-sms.md](../docs/permisos-sms.md).
- **Pruebas compartidas:** `app/src/sharedTest` se compila en `test` (JVM) y en `androidTest` (dispositivo), para
  correr los mismos vectores en ambos. Detalle en [docs/preprocesamiento.md](../docs/preprocesamiento.md).
