# Alerta ante un SMS riesgoso

Registro de #10 (RF-06): ante un SMS riesgoso, la app muestra una notificación que **indica el riesgo** y lleva a un **detalle**
del mensaje **sin activar enlaces**. Última verificación: 2026-11-08.

> **ATENCIÓN.** La app sigue sin ser apta para un teléfono con mensajes reales: no avisa de los mensajes nuevos que no son
> riesgosos y los MMS se pierden. Y qué mensajes alertan depende de la política de riesgo (#9), que es una **propuesta sin validar
> con datos reales** ([nivel-de-riesgo.md](nivel-de-riesgo.md)). Úsala como gestor de SMS solo en un emulador.

## Qué hace

1. Cuando llega un SMS, el receptor lo **guarda primero** (#4) y después lo **evalúa**: modelo, señales y política de riesgo, con la
   compuerta del rol y los permisos (#3).
2. Si el nivel es **alto**, muestra una notificación "**Posible estafa detectada**", con el remitente y **un motivo**: "Le pide
   actuar con urgencia", "Finge ser de una entidad" o "Le pide sus datos o claves" (el primero que corresponda, en ese orden).
3. Tocar la notificación, o **Ver detalles**, abre **Detalle del análisis** en la app: el nivel, el mensaje original y por qué se
   avisó.
4. En el detalle **el mensaje es texto plano**: ningún enlace de su texto se activa ni se puede tocar. Tampoco la notificación
   lleva el texto del mensaje.

**Cuándo avisa.** Solo con nivel **alto** (el modelo lo marca y al menos una señal lo respalda). Fue una decisión de diseño:
en el conjunto controlado, alertar con *medio o alto* cubría 29 de 30 mensajes de smishing pero alertaba en 6 de 47 legítimos
(13 %); alertar solo con *alto* cubre 19 de 30 y no alerta en ningún legítimo. Cambiarlo es cambiar `AlertPolicy.ALERT_LEVELS`.

## Qué se decidió sobre el contenido de la notificación

El diseño tiene dos versiones de la notificación. Se usó la de **remitente y motivo** (con "Ver detalles"), porque **no muestra el
texto del mensaje** y por eso no lo filtra en la pantalla de bloqueo. La otra versión mostraba el inicio del texto.

- La notificación es **privada** en la pantalla de bloqueo: ahí solo dice "Posible estafa detectada / Abra la aplicación para ver
  el detalle", **sin remitente ni motivo** (versión pública).
- No se registra el remitente, el motivo ni el texto en logs.

## Cómo se abre el detalle sin guardar nada

No se guarda el resultado del análisis (el historial y el almacenamiento son #12). La notificación solo lleva el **id del mensaje** en
el buzón. Al tocarla, el detalle lee el mensaje y **lo vuelve a evaluar**: como la evaluación es determinista (mismo modelo, mismas
reglas, misma política), da el mismo nivel y los mismos motivos que al recibirlo. Si el mensaje ya no está en el buzón, el detalle
lo dice.

## Código

| Pieza | Capa | Qué hace |
|---|---|---|
| `AlertPolicy`, `AlertReason`, `RiskAlert`, `AlertOnRiskyMessage` | `domain.alerts` | cuándo se avisa y por qué motivo; evalúa el mensaje y avisa |
| `AnalysisPipeline` | `data.analysis` | arma una sola vez por proceso el modelo con la compuerta y las reglas de señales |
| `AndroidRiskAlertNotifier` | `data.alerts` | la notificación (canal "Alertas de estafa", versión pública, "Ver detalles") |
| `SmsDeliverReceiver` | `data.sms` | tras guardar el SMS, lo evalúa y avisa; un fallo del análisis no toca el mensaje guardado |
| `MessageDetailScreen`, `MessageDetailState` | `ui.alerts` | "Detalle del análisis", con los componentes de [design-system.md](design-system.md) |
| `MessagesRoute`, `MainActivity` | `ui` | abren el detalle al tocar la alerta (`singleTop`: con la app abierta no se crea otra copia) |
| `RequestNotificationPermission` | `ui.alerts` | pide el permiso de notificaciones en Android 13 o superior |

Para ello `ReceiveIncomingSms` ahora devuelve el id del mensaje guardado y el mensaje.

## Permiso

`POST_NOTIFICATIONS`, documentado con su motivo en [permisos-sms.md](permisos-sms.md). Solo Android 13 (API 33) o superior lo pide
en tiempo de ejecución, al abrir los mensajes; antes no hace falta. Si la persona lo niega, las alertas no se muestran y no se
insiste.

## Decisiones que conviene revisar

- **Textos fuera del diseño:** los del detalle para los niveles ("Posible estafa", "Mensaje sospechoso", "Sin señales de riesgo"),
  "No se encontraron señales de riesgo", el texto de un mensaje no analizado y el de un mensaje que ya no está. El mockup dice
  "Mensaje **Peligroso**"; se usó "Posible estafa" para **no presentar la probabilidad como certeza**, que es lo que pide #11.
- **El pulido del lenguaje del detalle es de #11.** Los motivos usan las frases del diseño ("Finge ser de una entidad" afirma más de
  lo que sabemos; #11 puede suavizarlas).
- **El detalle todavía no tiene** "Acciones realizadas", "Este mensaje es seguro" ni las acciones de bloquear o retirar (#13 a #16).

## Cómo verificar

Con el emulador de API 29 abierto y `JAVA_HOME` en un JDK de 17 a 23 (ver [entorno.md](entorno.md)):

```powershell
.\gradlew.bat testDebugUnitTest --rerun
.\gradlew.bat connectedDebugAndroidTest --rerun
```

- `AlertOnRiskyMessageTest` (JVM): solo el nivel alto avisa; sin nivel no se avisa; el motivo es el primero en el orden del diseño y el
  enlace solo nunca lo es; la alerta no lleva el texto ni muestra el remitente en `toString`.
- `ReceiveIncomingSmsTest` (JVM): un mensaje guardado devuelve su id y el mensaje; un duplicado o uno ignorado no, para no analizarlo dos veces.
- `MessageDetailScreenTest` (dispositivo, 8): el nivel, el mensaje, los motivos y la recomendación; "Posible estafa" y no "Peligroso";
  **el texto del mensaje no tiene acción de toque**; el medio solo por el modelo, el bajo, el no analizado, el mensaje que ya no está y volver.
- `SmsRoleInstrumentedTest.thenARiskyMessageAlertsAndTheAlertOpensItsDetailWithoutActivatingLinks` (dispositivo): **la prueba
  funcional**. Entrega al receptor un SMS que el modelo marca como medio y otro de riesgo alto con enlace. Comprueba que **solo el de
  riesgo alto avisa**; que la notificación indica el riesgo con el remitente y el motivo, **sin el texto del mensaje** y con versión
  pública sin remitente; que tocarla abre el detalle con el nivel, el mensaje y los motivos; y que **tocar el enlace del mensaje no abre
  nada**: la app sigue al frente y no se lanza un navegador.

## Resultado (2026-11-08)

Emulador Pixel 4, Android 10 (API 29), x86_64, con Temurin 17 y `--rerun`.

| Ejecución | Pruebas | Fallos |
|---|---|---|
| `testDebugUnitTest` (JVM) | 128 (8 nuevas; 1 omitida: la medición con el corpus) | 0 |
| `connectedDebugAndroidTest` | 94 (9 nuevas): 93 ejecutadas y 1 omitida a propósito | 0 |

**Las pruebas no son vacías.** Se hizo que también alertara el nivel medio y falló la prueba "solo el alto avisa". Se revirtió.

**Revisión manual con el sistema** (capturas en el emulador; el SMS entró con `adb emu sms send`, sin llamar al receptor a mano):
- Llegó el SMS sintético y el registro dice "Stored" y después "Alerted". La notificación apareció con el escudo, "Posible estafa
  detectada", el remitente y el motivo "Le pide actuar con urgencia", y "Ver detalles"; se parece al diseño. En el diseño el
  título va en rojo; aquí el rojo está en el icono y en la acción.
- Al tocar "Ver detalles" se abrió "Detalle del análisis" con "Posible estafa", el mensaje original, los cuatro motivos con sus
  iconos y la recomendación: coincide con el diseño.
- **Se tocó el enlace del mensaje** y no pasó nada: la app siguió en la misma pantalla y no hay ningún navegador entre las actividades.

**Un fallo que apareció en el camino y no era de #10.** Una prueba de #8 buscaba un salto de línea simple en `signal_rules.json`. Al
hacer `pull`, Git dejó el archivo con CRLF en Windows y la prueba falló. Se corrigió la prueba, no el archivo.

## Lo que no cubre

- **No se probó en Android 13 o superior.** El emulador es API 29: la solicitud de `POST_NOTIFICATIONS` y el comportamiento de las
  notificaciones nuevas (por ejemplo, sin permiso) **no se verificaron**. Es lo más urgente de revisar en un dispositivo real.
- **Solo se avisa si la app es la predeterminada y recibe el SMS** (requiere el rol de SMS y los permisos); no analiza mensajes
  que ya estaban en el buzón ni los que llegan por MMS.
- **Alertar solo con nivel alto deja sin aviso 11 de 30 mensajes de smishing** del conjunto controlado (los de nivel medio).
- Las cifras del conjunto controlado son optimistas (ver [nivel-de-riesgo.md](nivel-de-riesgo.md)); con mensajes reales habrá
  alertas que sobran y alertas que faltan.
- **No se midió el tiempo** de evaluar y avisar en un teléfono de gama baja. En el emulador, el primer mensaje (con la carga del
  modelo) tardó unos 124 ms y el siguiente 40 ms. Android da unos 10 segundos a un receptor de difusión.
- Si el análisis falla (por ejemplo, la biblioteca del modelo), el mensaje queda guardado y no se avisa; solo se anota el tipo de error.
- **No se probó** con TalkBack ni con letra grande. No se midió el contraste de la notificación en el tema oscuro del sistema.
- Solo se probó en el emulador API 29 x86_64.
