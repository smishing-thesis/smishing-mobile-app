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

## Generar el APK en GitHub (#82)

El workflow **APK de pruebas (qa)** compila el mismo APK en GitHub con la dirección y la clave del backend guardadas en el repositorio, para
cambiarlas sin un pull request. Se lanza a mano: pestaña **Actions** > *APK de pruebas (qa)* > *Run workflow*. No corre en pull requests ni
en cada push. El APK queda como artefacto `app-qa-apk` durante 7 días.

Se crean una sola vez en **Settings > Secrets and variables > Actions**:

| Qué | Nombre | Dónde |
|---|---|---|
| Dirección del backend (`https://...`) | `SMISHING_BACKEND_URL` | pestaña *Variables* |
| Clave del backend | `SMISHING_BACKEND_API_KEY` | pestaña *Secrets* |
| Keystore de depuración compartido, en base64 (#84) | `ANDROID_DEBUG_KEYSTORE_BASE64` | pestaña *Secrets* |

La versión de la aplicación (`versionName`, por ejemplo `1.0-qa`) se ve al final de **Ajustes > Acerca de**, para saber de un vistazo qué
compilación está instalada.

Si falta alguna, el workflow falla con un mensaje claro y no genera APK (así no se entrega uno que no envía aportes ni uno con otra firma).

**Cambiar la clave:**
1. Cambiarla en el backend (variable `SMISHING_API_KEY`) y reiniciarlo.
2. Cambiar el secreto `SMISHING_BACKEND_API_KEY` de este repositorio, con el mismo valor.
3. Lanzar el workflow e instalar el APK nuevo (con la firma fija se instala encima del anterior sin desinstalar).

**Las apps ya instaladas conservan la clave con la que se compilaron** (va dentro del APK). Tras el cambio, el backend las rechaza con 401: siguen
clasificando, alertando y mostrando todo, pero el envío de aportes se detiene y lo pendiente queda en la cola, sin perderse, hasta instalar el
APK nuevo.

### Firma fija del APK de GitHub (#84)

El tipo `qa` se firma con la clave de **depuración**. Sin más, el runner de GitHub genera una nueva en cada ejecución, y Android no deja instalar
un APK sobre otro de distinta firma (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`): habría que desinstalar, y se perderían el historial y la cola de
aportes. Se comprobó que el APK de GitHub y el compilado en local tenían certificados distintos. Por eso el workflow escribe en
`debug.keystore` de una carpeta temporal un keystore fijo, guardado como secreto, y le indica a Gradle esa carpeta con `ANDROID_USER_HOME` (escribirlo
solo en `~/.android` no bastó en el runner). Al final **falla si el certificado del APK no coincide con el del keystore**, y muestra ambas huellas SHA-256.

Se genera una sola vez (la huella no es secreta; el archivo sí) y se guarda **fuera de Git** (`*.keystore` está en `.gitignore`):

```powershell
keytool -genkeypair -keystore debug-github.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
```

El secreto se crea con el archivo en base64, desde tu terminal (el valor no pasa por ningún otro lado):

```powershell
gh secret set ANDROID_DEBUG_KEYSTORE_BASE64 --repo smishing-thesis/smishing-mobile-app --body ([Convert]::ToBase64String([IO.File]::ReadAllBytes("debug-github.keystore")))
```

- **Guardar una copia del archivo** en un lugar seguro del equipo. Si se pierde, se genera otro, se actualiza el secreto y todos los teléfonos de prueba
  deben desinstalar la app una vez.
- **Compilaciones locales:** para que un APK compilado en tu computador se pueda actualizar con uno de GitHub (y al revés), copia ese mismo
  archivo a `~/.android/debug.keystore`. Cambia la clave de depuración de todas tus apps de Android Studio, que se reinstalarán una vez.
- Es solo para pruebas internas: la clave de distribución es la de #24 y no tiene nada que ver con esta.

**Quién ve la clave:** el APK la lleva dentro, así que quien descargue el artefacto puede extraerla; el repositorio es privado y el artefacto dura
pocos días. En el computador, una variable de entorno `SMISHING_BACKEND_API_KEY` olvidada gana sobre `local.properties`.

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
