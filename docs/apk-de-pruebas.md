# APK de pruebas internas

Registro de #32: cómo generar e instalar el APK para probar el flujo SMS → análisis → alerta → historial en un teléfono, y la plantilla
para anotar lo que se observe. Última verificación del APK: 2026-11-10, en el emulador de API 29. **#32 sigue abierto**: su criterio
pide ejecutar el protocolo en al menos un dispositivo real y guardar versión, sistema y resultado, y eso no se ha hecho.

## Generar el APK

Tipo de compilación `qa`: como el de producción (sin depuración) pero firmado con la clave de depuración, para poder instalarlo. No es
para distribuir: el App Bundle firmado es #24.

```powershell
.\gradlew.bat assembleQa
```

El APK queda en `app/build/outputs/apk/qa/app-qa.apk` (no se versiona: `*.apk` está en `.gitignore`).

Se comprobó con `aapt`: paquete `com.app.smishing`, versión `1.0-qa`, `minSdk` 29, `targetSdk` 34, **no depurable**, con las bibliotecas
nativas de `arm64-v8a`, `armeabi-v7a`, `x86` y `x86_64`. Pesa unos **54 MB**, casi todo bibliotecas nativas del modelo para las cuatro
arquitecturas; no se redujo. No se minifica (`isMinifyEnabled = false`).

## Instalar

Con el teléfono por USB y la depuración USB activada:

```powershell
adb install -r app\build\outputs\apk\qa\app-qa.apk
```

- Instalado con `adb`, Android deja que la app reciba el rol de SMS y los permisos restringidos (lo mismo que se ve en el emulador).
- **No se comprobó instalarlo desde un archivo** (por ejemplo, descargado al teléfono) **ni en Android 13 o superior**. Allí Android puede
  tratar los permisos de SMS como "ajustes restringidos" para las apps instaladas fuera de una tienda: habría que abrir la información
  de la app, el menú ⋮ y **Permitir ajustes restringidos** antes de aceptar el rol. Si pasa, anótalo.
- Al instalar sobre una versión anterior con `-r` se conservan los datos; para probar desde cero, desinstalar primero.

## Antes de probar con mensajes reales

El historial **guarda el texto de los mensajes sin cifrar y no se puede borrar** (#52 está pendiente). Para el registro de la prueba y
para no dejar datos personales, usar **mensajes sintéticos** (inventados) y no copiar texto de mensajes reales a ningún reporte.
Tampoco se reciben **MMS** mientras la app sea la de mensajería predeterminada (#48), y todavía no se pueden borrar mensajes ni
conversaciones (#51) ni se muestra el nombre del contacto (#53): no la uses como única app de mensajes todavía. Cada SMS ya avisa y se
marca como leído al abrir su conversación (#49, #50; ver [avisos-y-no-leidos.md](avisos-y-no-leidos.md)).

## Protocolo

Mensajes de ejemplo para enviar desde otro teléfono (inventados, con un enlace que no existe):

- Riesgo alto: `BBVA: Su tarjeta sera suspendida hoy. Actualice sus datos en http://bbva-pe.top`
- Sin señales: `Te quiero mucho, cuidate`
- Demasiado corto (no se evalúa): `Hola ya`

| # | Escenario | Resultado esperado |
|---|---|---|
| 1 | Primera apertura | Bienvenida (paso 1), luego el rol (paso 2). Tras aceptar, Inicio con "Protección activa" |
| 2 | Llega el mensaje de riesgo alto | Aparece la alerta con el remitente y un motivo, **sin el texto**; tocarla abre el detalle sin abrir el enlace |
| 3 | Llega el mensaje sin señales | Aviso del mensaje (remitente y texto; en la pantalla de bloqueo solo "Mensaje nuevo"); queda en Mensajes y en el Historial; no hay alerta de riesgo |
| 4 | Llega el mensaje demasiado corto | Aviso del mensaje; queda en Mensajes; **no** entra en el Historial |
| 4b | Tocar el aviso de un mensaje | Abre su conversación; el mensaje queda leído y el aviso desaparece; en la lista ya no tiene contador |
| 5 | Ajustes → "Preguntarme antes de actuar", y llega el de riesgo alto | El detalle ofrece "Bloquear número" y "Quitar el mensaje de la bandeja"; nada se toca antes |
| 6 | Ajustes → "Protegerme automáticamente", y llega el de riesgo alto | El número queda bloqueado y el mensaje sale de la bandeja; el detalle lo dice |
| 7 | En el detalle, "Marcar como seguro" | Se desbloquea, el mensaje vuelve a su conversación leído, y el Historial dice "Marcado como seguro" |
| 8 | Modo avión, y llega el de riesgo alto | Funciona igual (ver [demora-y-sin-internet.md](demora-y-sin-internet.md)) |
| 9 | Quitar un permiso o las notificaciones en los ajustes del sistema y volver a la app | Inicio pasa a "Protección apagada" y marca cuál falta |
| 10 | Reiniciar el teléfono y recibir el de riesgo alto | Sigue funcionando sin abrir la app antes |
| 11 | Escribir y enviar un SMS a otro número | Aparece en la conversación como enviado |

Los registros de la app **no incluyen el texto ni el remitente**: `adb logcat -s SmsDeliverReceiver Latency` muestra el resultado del
análisis sin el contenido.

## Registro de resultados

Copiar una fila por prueba. "No se declara superada sin evidencia": anotar qué se observó y, si falló, cómo.

| Fecha | Versión | Teléfono y Android | Escenario | Resultado (pasó / falló / no se pudo) | Qué se observó |
|---|---|---|---|---|---|
| | `1.0-qa` | | | | |

## Lo que no se verificó

- Ningún teléfono real (solo el emulador de API 29, x86_64), ni Android 13 o superior, ni doble SIM, ni mensajes reales de un operador.
- La instalación desde un archivo y los "ajustes restringidos" descritos arriba.
- El APK `qa` **se instaló y se probó a mano solo en el emulador**: con el rol concedido, un SMS de riesgo alto entregado por el
  sistema dio la alerta ("Posible estafa detectada") sin tocar nada más. El resto del flujo se probó con la compilación de depuración
  (ver las demás guías de `docs/`), no con el APK `qa`. No se corrió la suite de pruebas contra el APK `qa`.
