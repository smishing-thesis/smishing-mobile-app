# Aporte opcional al corpus: consentimiento y desidentificación

Registro de #18 (RF-13), #19 (RF-14) y #71 (envío pendiente). Última verificación: 2026-11-16.

> **ATENCIÓN.** Esta parte **todavía no envía nada**. Están el consentimiento, la desidentificación y la cola de pendientes (#71); falta el
> destino: el esquema y el endpoint del backend (#72, y #26/#27 para el modo de reporte). Hoy los mensajes peligrosos quedan en la cola y
> se conservan; no sale ningún dato del teléfono, y la app ni siquiera declara el permiso `INTERNET`.

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

- **Qué se encola:** con el aporte activado, cada mensaje de nivel **alto** (peligroso) que se analiza, al llegar o a pedido. Los de nivel
  medio no. Solo se guarda una referencia al registro del historial (tabla `pending_contribution`, base versión 3): el texto se
  desidentifica justo antes de enviarlo, y **borrar el mensaje del historial lo saca de la cola**.
- **Cuándo se envía:** WorkManager lo ejecuta **cuando hay conexión**, aunque la app esté cerrada, y lo reintenta con espera creciente si
  falla. Al abrir la app se retoma lo que haya quedado. Si se pierde la conexión, lo pendiente sale en cuanto vuelve.
- **Por lotes:** de 20 en 20 (`SendPendingContributions.BATCH_SIZE`). Si un lote falla se detiene y lo no enviado se conserva.
- **Antes de cada texto** se vuelve a leer el consentimiento y se desidentifica; lo que no quede anónimo se **descarta** de la cola. Si se
  revoca el consentimiento, se vacía la cola y se cancela el envío programado.
- **El destino es una interfaz** (`ContributionSender`). Hoy es `NotConfiguredContributionSender`: no envía y todo se conserva. Se
  reemplaza en #72, cuando el backend defina su esquema y endpoint; entonces hará falta `INTERNET`.
- **Permisos que agrega WorkManager** (se documentan porque la regla es pedir solo los que se justifican): `ACCESS_NETWORK_STATE` (saber si
  hay conexión), `RECEIVE_BOOT_COMPLETED` (retomar el envío tras reiniciar) y `WAKE_LOCK`. Son permisos normales: no se piden en pantalla.
  Los de servicio en primer plano que también trae se **quitan** en el manifiesto.

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

- JVM: `AnonymizationVectorsTest` (2: los 29 vectores y la versión), `PrepareContributionTest` (10: sin elección no se prepara nada, con
  consentimiento sale desidentificado, revocar detiene el siguiente porque se lee cada vez, sin consentimiento no se arma el
  desidentificador, un ejemplo aún no anónimo se rechaza, vacío se rechaza, `toString` no muestra el texto, los nombres de contactos se
  quitan palabra por palabra, un nombre que no está en la lista no se detecta, el texto crudo no es anónimo y el resultado sí).
- Envío pendiente: `SendPendingContributionsTest` (6: lotes, fallo a mitad, sin destino, revocar a mitad, lo no anónimo se descarta, un
  teléfono nunca sale en claro) y `QueueingAnalysisHistoryTest` (3: solo el nivel alto con consentimiento se encola; un fallo al encolar no
  rompe el análisis). Dispositivo: `RoomPendingContributionsInstrumentedTest` (orden y límite, quitar y vaciar, borrar el historial).
- Dispositivo (API 29): `AnonymizationVectorsInstrumentedTest` (los mismos 29 vectores con el motor de Android) y
  `DataStoreContributionConsentInstrumentedTest` (4: desactivado por defecto, activar y revocar, sobrevive a reabrir el almacén, y
  revocar detiene el aporte siguiente con el almacén real).
- A mano en el emulador: el interruptor sale desactivado, al activarlo dice "Activado" y se conserva al cerrar y abrir la app.

## Lo que no cubre

- **No se envía nada** (falta el destino, #72) y no hay vista previa de lo que se compartiría. Entregar el texto a un destino es #27; el modo de reporte, #26.
- Solo API 29 (emulador); no se probó en otras versiones de Android ni en un teléfono real.
- No se midió el tiempo de desidentificar ni el de leer todos los contactos para la lista de nombres.
