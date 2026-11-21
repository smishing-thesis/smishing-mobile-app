# Contrato con el backend: lo que necesita la app (#72)

> **Estado (2026-11-21):** el backend atendió esto con el endpoint de lote `POST /api/v1/contributions/batch` (OpenAPI 1.1.0, PR del
> backend #48) y la app lo usa (#72, ver [aporte-al-corpus.md](aporte-al-corpus.md)). Lo que sigue es el documento original; lo que se
> decidió distinto: el backend **no** acepta `anonymization_version`, el máximo es 20 aportes y 327680 bytes por lote, y los motivos de
> rechazo son `not_anonymized`, `unknown_model_version` e `invalid_contribution`.

Documento para la sesión que trabaja en `smishing-backend`. Lo escribe el agente de la app (`smishing-mobile-app`) a partir de su
código. Sirve para que el backend ajuste su contrato a la Memoria **y** a lo que la app ya hace, de modo que los dos repositorios
queden alineados. Fecha de lectura de ambos repos: 2026-11-21.

**Cómo leerlo.** Lo marcado **[HECHO]** es lo que la app hace hoy, verificado en su código (con la ruta del archivo). Lo marcado
**[NECESITA]** es lo que la app requiere del backend. Lo marcado **[DECIDIR]** son decisiones abiertas para el backend. Lo que no se
pudo comprobar se dice como tal.

## 1. Objetivo

Que el aporte opcional llegue del teléfono al corpus: la app ya encola, anonimiza y envía por lotes (en el código); lo que falta es el
envío real a este backend (#72, hoy `NotConfiguredContributionSender`, que no envía nada). **Nadie consume todavía el contrato v1**
(no existe ningún cliente HTTP en la app), así que **se puede cambiar sin romper a nadie**. Si el backend debe moverse para amoldarse a la
Memoria y a la app, es el momento.

## 2. Reglas que no se rompen

- El backend no guarda nada que identifique a la persona ni al dispositivo: ni IP, ni ID, ni cuenta, ni telemetría, ni fecha original.
  Por eso **el consentimiento se verifica en el teléfono** antes de enviar; el backend no puede comprobarlo.
- Nunca texto de mensajes, direcciones ni claves en logs, respaldos ni mensajes de error (ni siquiera en depuración).
- Las pruebas usan solo mensajes sintéticos. **No** abrir ni usar los archivos de SMS reales que haya en la carpeta de la tesis.
- Los issues van en español; una rama y un PR por historia; nunca commits en `main`; no agregar a Claude como coautor. Los resultados
  se registran con honestidad: si algo falló o no se pudo medir, se dice.

## 3. Lo que hace la app hoy [HECHO]

1. **Consentimiento.** Interruptor de Ajustes "Compartir datos de forma anónima", **desactivado por defecto** y revocable. Se lee cada
   vez que se va a preparar un aporte, no una sola vez (`domain/sharing/Contribution.kt`).
2. **Qué se encola.** Solo un mensaje de **riesgo alto**, y solo con el consentimiento activo (`QueueingAnalysisHistory`, en
   `domain/sharing/PendingContributions.kt`). La cola guarda el id del análisis; el texto se lee del historial al enviar. Riesgo alto
   significa: el modelo lo marca como sospechoso **y** hay una señal de respaldo (urgencia, suplantación o pedido de datos). Por tanto, en
   la práctica **todo lo que se envía hoy es `smishing`**; la app no envía mensajes `safe`.
3. **Cuándo se envía.** Un trabajo de WorkManager (`data/sharing/ContributionWorker.kt`) corre cuando hay conexión, incluso con la app
   cerrada, y envía **en lotes de 20** (`SendPendingContributions.BATCH_SIZE`).
4. **Antes de cada aporte** se vuelve a comprobar el consentimiento y se **anonimiza** con las reglas **1.3.0**, las mismas de
   `sms-anonymizer` (`domain/sharing/Anonymizer.kt`, `AnonymizationRules.VERSION = "1.3.0"`). Si tras anonimizar queda vacío o una regla aún
   reconoce algo, el aporte se **descarta**, no se envía.
5. **Revocar** el consentimiento vacía la cola; **borrar** un mensaje del historial lo saca de la cola.
6. **Resultado del envío.** Hoy la interfaz del envío distingue solo `Sent`, `Retry` y `NotConfigured` **para el lote entero**
   (`ContributionSendResult`). Con `Retry` el lote se conserva y se reintenta; la app se detiene en el primer lote que falla.
7. **Datos disponibles por mensaje** (en el análisis guardado): el texto anonimizado, la etiqueta (`isSmishing`), `modelVersion`
   (hoy `1.0.0`), la versión del preprocesamiento (`1.1.0`) y la versión de las reglas de anonimización (`1.3.0`).
8. **Sin red hoy.** La app **no declara el permiso `INTERNET`** (hay una prueba de dispositivo que falla si aparece, y los docs lo
   afirman). Declararlo es trabajo de la app en #72, con su justificación.

## 4. Lo que la app necesita del backend [NECESITA]

### 4.1 Envío por lotes (la diferencia principal)

El backend recibe **un aporte por solicitud**; la app envía hasta 20 juntos. Enviar de uno en uno es lento (20 solicitudes, 20 viajes
con TLS, y la cuota de 60 por minuto se consume rápido). **Se necesita una forma de lote**. Propuesta para que el backend la revise y
ajuste contra la Memoria:

```
POST /api/v1/contributions/batch        (HTTPS, X-API-Key)

{
  "contributions": [
    { "text": "…anonimizado…", "label": "smishing", "model_version": "1.0.0", "anonymization_version": "1.3.0" }
  ]
}
```

- **Tamaño:** de 1 a 20 aportes por lote (configurable en el servidor; la app lee el máximo del contrato, no lo supone). Límite de bytes
  del cuerpo del lote [DECIDIR]: 20 aportes de 2048 puntos Unicode caben en unos 160 KB en el peor caso.
- **`anonymization_version`** [DECIDIR]: la app puede informar con qué reglas anonimizó (`1.3.0`); el backend ya reanonimiza con su propio
  recurso `anonymization-1.3.0.json`. Útil para saber si hay que reanonimizar con reglas nuevas; opcional si no aporta.
- **Respuesta con un resultado por aporte**, para que la app saque de la cola lo que ya no debe reintentar:

```
200 OK
{ "results": [
    { "index": 0, "status": "accepted" },
    { "index": 1, "status": "duplicate" },
    { "index": 2, "status": "rejected", "reason": "not_anonymized" }
] }
```

  - `accepted` y `duplicate` (mismo hash de contenido): la app lo **saca de la cola**. Un duplicado no es un error: así reenviar es
    seguro (el envío es idempotente).
  - `rejected` (formato, tamaño, no anonimizado, versión de modelo no registrada…): la app lo **descarta y no reintenta sin fin**. El
    `reason` es un código genérico; **nunca** repite el texto ni el valor recibido.
  - Un error del lote entero usa el estado HTTP, sin cuerpo con datos: `401` (clave inválida o revocada), `413`, `422` (estructura
    inválida), `429` con `Retry-After`, `503`.
- **Atomicidad por aporte, no por lote:** que un aporte malo no tire los demás.
- Si el backend prefiere mantener un solo endpoint con una lista, o usar otra ruta, es decisión suya; lo importante es el contrato de
  arriba (varios aportes por solicitud y un resultado por cada uno).

### 4.2 Campos y cómo se llenan

| Campo del contrato | De dónde sale en la app | Nota |
|---|---|---|
| `text` | texto ya anonimizado | 1 a 2048 puntos Unicode, como hoy |
| `label` | `isSmishing` del análisis | hoy siempre `smishing` (solo se envía riesgo alto) |
| `model_version` | `modelVersion` del análisis (`1.0.0`) | máx. 20 caracteres; debe estar **registrada** en el catálogo, o se rechaza |
| `anonymization_version` | `AnonymizationRules.VERSION` (`1.3.0`) | propuesto, opcional |

La app no enviará remitente, teléfono, fecha original, ID ni nada del dispositivo.

### 4.3 Autenticación y cuotas

- `X-API-Key` compartida, como ya está definido (#6). La app la llevaría en la compilación (nunca en Git). El propio backend ya documenta que
  **una clave embebida en Android se puede extraer**: por eso las defensas reales son la cuota, la reanonimización y la cuarentena.
- **[NECESITA]** Que un `401` sea inequívoco, para que la app **deje de reintentar** (la clave pudo ser revocada) y no gaste batería.
- **[DECIDIR]** Cómo se distribuye y rota la clave entre versiones de la app (el backend admite solapamiento de claves).
- La cuota actual (60 por minuto por par de red inmediato y 600 globales, por proceso) **sí alcanza** si un lote es una sola solicitud. La
  app respetará `Retry-After`.

### 4.4 Transporte y entornos

- Solo HTTPS. La app verifica el certificado con las autoridades del sistema. **No está decidido** si habrá fijación de certificado.
- **[NECESITA]** Un entorno contra el que probar. Hoy el backend solo tiene el servidor local con TLS y certificado efímero; el emulador de
  Android no confía en ese certificado, así que la app tendrá una configuración **solo de depuración** para el servidor local (el emulador
  ve la máquina anfitriona como `10.0.2.2`). El despliegue (#19 y #20) sigue en Backlog: la URL base de producción aún no existe.
- La app necesita saber el **máximo del lote** y la **versión del contrato**; propuesta: que el OpenAPI las publique.

### 4.5 Errores y reintentos (cómo los interpreta la app)

| Qué llega | Qué hace la app |
|---|---|
| `200` con `accepted` o `duplicate` | saca el aporte de la cola |
| `200` con `rejected` | lo descarta de la cola, anota solo el código (sin texto) y sigue |
| `401` | detiene los envíos; conserva la cola; no reintenta sin fin |
| `429` | espera lo que diga `Retry-After` y reintenta |
| `413` o `422` del lote | divide o descarta según el caso; no reintenta igual |
| `503`, otros `5xx` o sin conexión | conserva el lote y reintenta más tarde (el backend ya dice que un fallo de base **no confirma** recepción) |

## 5. Alineación con la Memoria [DECIDIR / VERIFICAR]

- El backend ya contrastó su esquema físico con la Memoria (`docs/thesis-schema-review.md`, migración 0005): tablas `contributed_message`,
  `message_label`, `model_version`, con `content_hash CHAR(64)`, `review_status`, `category`, `source` y `version_tag VARCHAR(20)`.
- **El agente de la app no tiene la Memoria** (`Memoria_Díaz_Valenzuela_ISW.docx`): no pudo comprobar nombres ni entidades por su cuenta.
  Lo que se sabe de ella viene de los documentos del backend. **Pedimos que el backend verifique contra la Memoria**, sin suponer:
  - los nombres de entidades y atributos del subsistema backend y de la compartición optativa (RF-13 y RF-14);
  - si el envío por lote, el campo `anonymization_version` o los códigos de rechazo contradicen algo de la Memoria;
  - cualquier nombre del contrato público (`text`, `label`, `model_version`) que convenga renombrar para coincidir con ella.
- Si una decisión se aparta de la Memoria, **se documenta como decisión técnica** (como hizo #4), no como cita de la Memoria.
- Los nombres físicos de la base no tienen por qué ser los del JSON público; SQLAlchemy ya mapea entre ambos.

## 6. Preguntas abiertas para el backend [DECIDIR]

1. ¿Endpoint de lote nuevo o el actual aceptando una lista? ¿Ruta y forma del cuerpo?
2. ¿Máximo de aportes por lote y límite de bytes del cuerpo?
3. ¿Acepta `anonymization_version`? ¿Guarda esa versión junto al aporte?
4. ¿Qué códigos de `reason` en el rechazo, siempre genéricos y sin texto?
5. ¿Cómo se distribuye y rota la clave? ¿Y qué hace el backend si la clave se filtra?
6. ¿Entorno de pruebas accesible desde el emulador, y cuándo el despliegue en Azure?
7. ¿Hace falta aceptar aportes `safe`, o la tesis solo recoge los `smishing`? (la app hoy solo envía `smishing`)

## 7. Secuencia propuesta

1. El backend decide el contrato de lote (puntos 4.1 y 6), lo contrasta con la Memoria y **actualiza `docs/openapi-v1.json`** con sus pruebas.
2. El agente de la app implementa el envío real contra **ese** OpenAPI: arma los campos, agrega el permiso `INTERNET` con su justificación
   (y actualiza la prueba que hoy lo prohíbe), lee las respuestas por aporte y maneja los reintentos.
3. Prueba conjunta contra el servidor local con TLS, con mensajes sintéticos: aceptado, duplicado, rechazado, 401, 429 y 503.

## 8. Criterios de aceptación de la integración

- Un lote de aportes llega al endpoint de pruebas y cada aporte se confirma por separado.
- Un fallo de red, un `429` o un `5xx` dejan los aportes pendientes; un rechazo se registra y no se reintenta sin fin.
- Sin consentimiento o sin anonimizar, no sale nada de la app.
- No hay texto de mensajes en logs, errores ni respaldos, ni en el backend ni en la app.
- Si se retira el consentimiento, la cola se vacía y no se envía más.
