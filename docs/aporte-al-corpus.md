# Aporte opcional al corpus: consentimiento y desidentificación

Registro de #18 (RF-13), #19 (RF-14), #71 (envío pendiente), #72 (envío al backend) y #76 (ajustes al contrato 1.1.0 y riesgo medio).
Última verificación: 2026-11-23.

> **Estado.** Con el aporte activado, los mensajes **peligrosos y sospechosos** (nivel de riesgo alto y medio) se anonimizan en el teléfono
> y se envían por lotes al backend (#72, #76). Solo sale algo si hay consentimiento, dirección y clave del backend en la compilación, y
> conexión; si no, los mensajes quedan en la cola. El reporte de un SMS sospechoso a un destino externo (#27) quedó fuera de alcance.

## Consentimiento (#18)

- **Ajustes → Privacidad → "Compartir datos de forma anónima".** Un interruptor, **desactivado por defecto**, sin subtítulo (el
  propio interruptor muestra si está activado). La decisión se guarda en el teléfono (DataStore, `contribution_consent`) y sobrevive a
  cerrar la app.
- **Activarlo abre un aviso** («Compartir datos de forma anónima», con el texto del diseño): solo se activa con **Aceptar**; **Denegar** o tocar
  fuera lo deja apagado. Apagarlo es inmediato y no pregunta. El enlace «Ver términos y condiciones» **todavía no lleva a ninguna parte**
  (no hay documento). El aviso dice que nunca se comparte «cualquier dato personal», lo cual es más de lo que se puede garantizar: ver
  los límites conocidos más abajo.
- **Revocarlo detiene los envíos futuros** porque el consentimiento se **lee cada vez** que se prepara un aporte (`PrepareContribution`),
  no una sola vez. El backend no guarda nada que identifique al dispositivo, así que el teléfono es quien lo verifica antes de enviar.
- Sin consentimiento, `PrepareContribution` **no toca el texto**: ni siquiera arma el desidentificador.

## Envío pendiente (#71)

- **Qué se encola:** con el aporte activado, cada mensaje de nivel **alto** (peligroso) o **medio** (sospechoso) que se analiza, al llegar o
  a pedido (#76; antes solo el alto). Los de nivel bajo no, y **nunca se envían mensajes seguros**. Solo se guarda una referencia al registro del historial (tabla `pending_contribution`, base versión 3): el texto se
  desidentifica justo antes de enviarlo, y **borrar el mensaje del historial lo saca de la cola**.
- **Cuándo se envía:** WorkManager lo ejecuta **cuando hay conexión**, aunque la app esté cerrada, y lo reintenta con espera creciente si
  falla. Al abrir la app se retoma lo que haya quedado. Si se pierde la conexión, lo pendiente sale en cuanto vuelve.
- **Por lotes:** de 20 en 20 (`SendPendingContributions.BATCH_SIZE`, igual al máximo del backend). Si un lote falla se detiene y lo no
  enviado se conserva.
- **Antes de cada texto** se vuelve a leer el consentimiento y se desidentifica; lo que no quede anónimo se **descarta** de la cola. Si se
  revoca el consentimiento, se vacía la cola y se cancela el envío programado.
- **El destino es una interfaz** (`ContributionSender`). Sin dirección del backend o sin clave en la compilación es
  `NotConfiguredContributionSender`: no envía y todo se conserva. Con ellas es `HttpContributionSender` (#72, abajo).
- **Permisos que agrega WorkManager** (se documentan porque la regla es pedir solo los que se justifican): `ACCESS_NETWORK_STATE` (saber si
  hay conexión), `RECEIVE_BOOT_COMPLETED` (retomar el envío tras reiniciar) y `WAKE_LOCK`. Son permisos normales: no se piden en pantalla.
  Los de servicio en primer plano que también trae se **quitan** en el manifiesto.

## Envío al backend (#72)

Contrato: [contrato-con-el-backend.md](contrato-con-el-backend.md) y el OpenAPI de `smishing-backend` (1.1.0).

- **Una solicitud por lote:** `POST /api/v1/contributions/batch`, por HTTPS y con `X-API-Key`. Cuerpo
  `{"contributions": [{"text", "label", "model_version"}]}`, de 1 a 20 aportes. **No lleva nada del dispositivo** ni la versión de las
  reglas de desidentificación (el backend no la acepta). `label` es siempre `smishing`: el backend solo conoce `safe` y `smishing`, y
  solo se comparten mensajes de nivel alto y medio (#76). Se decide por el **nivel de riesgo**, no por la marca del modelo: un mensaje de
  nivel medio por señales (`risk.signals_only`) no lo marcó el modelo y aun así viaja como `smishing`. `model_version` es la versión del modelo
  que lo analizó.
- **Un resultado por aporte**, en el mismo orden (`BackendContract`): `accepted` y `duplicate` (reenviar es seguro) y `rejected` con un
  código genérico (`not_anonymized`, `unknown_model_version`, `invalid_contribution`). **Los tres salen de la cola**: lo rechazado no se
  reintenta sin fin. Si el número de resultados o su orden no coinciden con el lote, no se confía en la respuesta y se conserva todo.
- **Texto largo (#76):** el backend limita `text` a **2048 caracteres** (`ContributionInput.text.maxLength`, OpenAPI 1.1.0); si un aporte lo
  supera rechaza el **lote entero** con `422`, y un solo mensaje largo detendría el envío de todos los demás. Por eso, tras desidentificar,
  `PrepareContribution` descarta de la cola el texto de más de 2048 caracteres (`MAX_TEXT_LENGTH`, `ContributionRejection.TooLong`). Se cuentan
  caracteres Unicode, como hace el backend, no unidades de UTF-16: un emoji cuenta como uno. Es una constante tomada del OpenAPI; si el
  backend la cambia, hay que actualizarla a mano.
- **Reacción a cada respuesta:** `401` (la clave no sirve) y `400`, `413` o `422` (el lote no cumple el contrato) **detienen** el envío y
  conservan la cola, sin reintentar sin fin; se retoma al llegar otro aporte o al abrir la app. `5xx` (también sin conexión o sin red de
  confianza) **se reintentan** con la espera creciente de WorkManager. El `429` se reintenta **tras la espera que pide el backend**
  (`Retry-After`, entre 1 y 3 600 segundos): el trabajo termina y `ContributionScheduler.scheduleAfter` programa el siguiente con ese
  retraso inicial, encadenado a continuación (#76). Lo que llegue mientras tanto se encadena detrás y no adelanta la espera. Sin
  `Retry-After` en el `429`, se usa la espera creciente.
- **Dónde está la dirección y la clave:** en `local.properties` (que no va en Git), con `smishing.backendUrl` y `smishing.backendApiKey`;
  se compilan en `BuildConfig`. **Sin ellas la app compila igual y no envía nada.** Solo se acepta `https://`. Una clave embebida en una
  app se puede extraer: el backend lo dice y por eso tiene cuotas, reanonimización y cuarentena; la clave no se escribe en ningún log.
- **Servidor local de pruebas (solo depuración):** el backend usa un certificado de prueba que el emulador no conoce. Con
  `adb push cert.pem /storage/emulated/0/Android/data/com.app.smishing/files/dev-backend-cert.pem` (solo la parte **pública**) y
  `adb reverse tcp:8443 tcp:8443`, la versión `debug` confía en ese certificado (`BackendTls`); `qa` y `release` no tienen esa excepción.
- **Permiso `INTERNET`:** se declara por esto; motivo y prueba en [permisos-sms.md](permisos-sms.md).

### Prueba contra el servidor local (2026-11-21)

En el emulador de **API 35** (Android 15), con la variante `debug`, el backend local de `smishing-backend` (rama `feat/48-batch-contributions`,
OpenAPI 1.1.0) por HTTPS en el puerto 8443 con `adb reverse`, y su certificado de prueba subido con `adb push`. Mensajes **sintéticos**.

| Paso | Resultado |
|---|---|
| Aporte activado desde Ajustes; llega por el receptor un SMS sintético de riesgo alto | se clasifica, se alerta y queda en la cola |
| El servidor corría **sin** la clave de pruebas | la app abre la conexión HTTPS y confía en el certificado de prueba; el backend contesta `401` y la app registra `Unauthorized`: **se detiene y conserva el mensaje en la cola**, sin reintentar sin fin. Un `curl` con la misma clave dio también `401`: no era la app |
| El servidor reiniciado con la clave de pruebas | al abrir la app se retoma lo pendiente: `Done(sent=1, rejected=0)` y la cola queda vacía |
| Llega otra vez el mismo SMS | `Done(sent=1, rejected=0)`. El registro de la app no distingue aceptado de duplicado (ambos cuentan en `sent`); con `curl`, el mismo lote dio primero `accepted` y luego `duplicate`, que es lo que el backend dice de reenviar |

**Lo que esto no probó:** `429` con `Retry-After` (tampoco la reprogramación de #76: solo se verificó que compila; no se midió que el
siguiente envío salga después de la espera pedida), `5xx`, un rechazo (`rejected`) y un lote de más de un aporte contra el servidor real; esos casos
solo están en las pruebas JVM con un transporte falso. Tampoco `qa`/`release` (no traen dirección ni clave por defecto), ni un teléfono real,
ni una dirección pública con certificado válido: el backend aún no está desplegado (#19 y #20). La versión `1.0.0` del modelo tiene que estar
registrada en el catálogo del backend; si no, cada aporte se rechaza con `unknown_model_version` y la app lo descarta.

## Desidentificación (#19)

`Anonymizer` quita de un SMS, en el teléfono, antes de que pueda compartirse:

| Qué | Marcador | Cómo |
|---|---|---|
| enlaces, con o sin `https://` | `<URL>` | regla `url` |
| correos | `<EMAIL_ADDRESS>` | regla `email` |
| teléfonos de Perú (9XXXXXXXX, con o sin +51) | `<PHONE_NUMBER>` | regla `phone_pe` |
| RUC (11 dígitos) o DNI (8) escritos tras la palabra | `<ID_NUMBER>` (la palabra se queda) | regla `id_number_pe` |
| nombres de los contactos | `<NAMED_ENTITY>` | lista de nombres (ver abajo) |

Cuando dos reglas se pisan gana la de mayor prioridad, igual que en la referencia.

**Las reglas son las de `sms-anonymizer` (`rules.py`, versión 1.3.0)**, el repo del corpus: es el contrato que la app debe cumplir. Se
comprueba con `anonymization_vectors.json` (29 casos sintéticos), **generado con el anonimizador de referencia** por
`tools/generate_anonymization_vectors.py RUTA/A/sms-anonymizer`. Si cambia una regla allá, se sube la versión, se regeneran los vectores
y se ajusta `AnonymizationRules`.

**Se rechaza lo no desidentificado.** Tras desidentificar, `PrepareContribution` vuelve a pasar las reglas sobre el resultado: si todavía
reconocen algo, el aporte se **rechaza** (`NotAnonymized`), no se arregla. También se rechaza el texto que queda vacío (`Empty`).

### Lo que no hace (límites conocidos)

- **No detecta nombres que no estén en los contactos.** La referencia usa un modelo de lenguaje (spaCy, NER) para los nombres de persona;
  **no cabe en un teléfono de gama baja** y no está en la app. En su lugar se quitan los **nombres de los contactos** (cada palabra de al
  menos 3 letras, `Anonymizer.fromContactNames`). Un nombre de alguien que no está en los contactos **pasa**, y ninguna comprobación puede
  saberlo. Quita de más antes que de menos: un contacto llamado "Casa" borra también la palabra "casa". Por esto el texto del interruptor
  dice "de forma anónima" y no promete que no quede ningún dato.
- **No quita otros datos personales** que las reglas no cubren (direcciones, números de cuenta o de tarjeta, fechas de nacimiento, un DNI
  escrito sin la palabra DNI).
- **La ñ no coincide:** la comparación de nombres sigue a la referencia, que ignora mayúsculas y acentos pero quita la tilde de la ñ al armar
  el patrón. Un contacto "Muñoz" se busca como "munoz": no reconoce "Muñoz" en un texto y sí "Munoz". Está en los vectores.

## Una diferencia de Android que apareció al probar

La prueba en el emulador falló donde la de la JVM pasaba: Android **no admite** el indicador `UNICODE_CHARACTER_CLASS` de las expresiones
regulares (lanza «Unsupported flags: 256»), que en la JVM hace falta para que `\w`, `\d` y `\b` sean Unicode como en Python. Android ya es
Unicode por defecto. Se compila con el indicador y, si no existe, sin él (`AnonymizationRules.compileLikePython`). Con eso los 29 casos
dan lo mismo que la referencia **en la JVM y en el dispositivo**.

## Dónde está

| Pieza | Archivo |
|---|---|
| Reglas, desidentificador | `domain/sharing/Anonymizer.kt` |
| Consentimiento y preparación del aporte | `domain/sharing/Contribution.kt` |
| Consentimiento guardado | `data/settings/DataStoreContributionConsent.kt` |
| Interruptor | `ui/settings/SettingsScreen.kt`, `SettingsRoute.kt` |
| Vectores y generador | `app/src/sharedTest/assets/anonymization_vectors.json`, `tools/generate_anonymization_vectors.py` |

## Pruebas

- JVM: `AnonymizationVectorsTest` (2: los 29 vectores y la versión), `PrepareContributionTest` (12: sin elección no se prepara nada, con
  consentimiento sale desidentificado, revocar detiene el siguiente porque se lee cada vez, sin consentimiento no se arma el
  desidentificador, un ejemplo aún no anónimo se rechaza, vacío se rechaza, `toString` no muestra el texto, los nombres de contactos se
  quitan palabra por palabra, un nombre que no está en la lista no se detecta, el texto crudo no es anónimo y el resultado sí, un texto
  de más de 2048 caracteres se rechaza y el límite cuenta caracteres Unicode, #76).
- Envío pendiente: `SendPendingContributionsTest` (13: lotes, fallo a mitad, sin destino, revocar a mitad, lo no anónimo se descarta, un
  teléfono nunca sale en claro, etiqueta y versión del modelo, duplicados y rechazados, resultados que no coinciden, clave inválida, lote
  rechazado y un texto demasiado largo que se descarta sin bloquear a los demás) y `QueueingAnalysisHistoryTest` (5: el nivel alto y el
  medio con consentimiento se encolan, el bajo o sin consentimiento no; alto y medio viajan como `smishing`; un fallo al encolar no rompe
  el análisis). Dispositivo: `RoomPendingContributionsInstrumentedTest` (orden y límite, etiqueta y versión del modelo, un mensaje de
  nivel medio también viaja como `smishing`, quitar y vaciar, borrar el historial).
- Envío al backend (#72): `BackendContractTest` (el cuerpo y cada respuesta) y `HttpContributionSenderTest` (la ruta, las cabeceras, sin
  destino no se pide nada a la red, fallos de red y claves que no aparecen en ningún texto), con un transporte falso: sin red.
- Dispositivo (API 29): `AnonymizationVectorsInstrumentedTest` (los mismos 29 vectores con el motor de Android) y
  `DataStoreContributionConsentInstrumentedTest` (4: desactivado por defecto, activar y revocar, sobrevive a reabrir el almacén, y
  revocar detiene el aporte siguiente con el almacén real).
- A mano en el emulador: el interruptor sale desactivado, al activarlo dice "Activado" y se conserva al cerrar y abrir la app.

## Lo que no cubre

- **Vista previa:** no hay una pantalla que muestre lo que se compartiría. Entregar el texto a un destino con reporte es #27; el modo de reporte, #26.
- **#76:** las pruebas de dispositivo de la cola (nivel medio) se corrieron en el emulador API 36 (2026-11-23) y pasan. Sigue sin verse en el
  emulador que un mensaje de nivel medio llegue al backend ni que el aviso de consentimiento se lea bien con el texto nuevo.
- **Backend real:** ver la prueba contra el servidor local, en "Envío al backend (#72)"; el despliegue público del backend (#19 y #20) no
  existe, así que `release` no trae dirección. No se midió el tiempo de un lote.
- Solo API 29 (emulador) para lo anterior a #72; no se probó en otras versiones de Android ni en un teléfono real.
- No se midió el tiempo de desidentificar ni el de leer todos los contactos para la lista de nombres.
