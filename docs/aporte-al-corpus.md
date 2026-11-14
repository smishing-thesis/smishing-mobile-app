# Aporte opcional al corpus: consentimiento y desidentificación

Registro de #18 (RF-13) y #19 (RF-14). Última verificación: 2026-11-14.

> **ATENCIÓN.** Esta parte **no envía nada**. Está el consentimiento y la desidentificación; falta el destino y el envío (#26 y #27, que
> dependen del backend). Activar el interruptor hoy solo guarda la decisión.

## Consentimiento (#18)

- **Ajustes → Privacidad → "Compartir mensajes de forma anónima".** Un interruptor, **desactivado por defecto**, con el texto "Desactivado.
  Puede cambiarlo cuando quiera." (o "Activado. …"). La decisión se guarda en el teléfono (DataStore, `contribution_consent`) y sobrevive a
  cerrar la app.
- **Revocarlo detiene los envíos futuros** porque el consentimiento se **lee cada vez** que se prepara un aporte (`PrepareContribution`),
  no una sola vez. El backend no guarda nada que identifique al dispositivo, así que el teléfono es quien lo verifica antes de enviar.
- Sin consentimiento, `PrepareContribution` **no toca el texto**: ni siquiera arma el desidentificador.

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
- Dispositivo (API 29): `AnonymizationVectorsInstrumentedTest` (los mismos 29 vectores con el motor de Android) y
  `DataStoreContributionConsentInstrumentedTest` (4: desactivado por defecto, activar y revocar, sobrevive a reabrir el almacén, y
  revocar detiene el aporte siguiente con el almacén real).
- A mano en el emulador: el interruptor sale desactivado, al activarlo dice "Activado" y se conserva al cerrar y abrir la app.

## Lo que no cubre

- **No se envía nada** y no hay vista previa de lo que se compartiría. Entregar el texto a un destino es #27; el modo de reporte, #26.
- Solo API 29 (emulador); no se probó en otras versiones de Android ni en un teléfono real.
- No se midió el tiempo de desidentificar ni el de leer todos los contactos para la lista de nombres.
