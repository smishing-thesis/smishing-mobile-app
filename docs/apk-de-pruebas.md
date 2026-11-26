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

Se comprobó con `aapt` (2026-11-10, con `targetSdk` 34; hoy es 36): paquete `com.app.smishing`, versión `1.0-qa`, `minSdk` 29, **no depurable**, con las
bibliotecas nativas de `arm64-v8a`, `armeabi-v7a`, `x86` y `x86_64`. **Desde #24 se compila con R8** (minificado): pesa unos **23 MB**; antes,
sin minificar, pesaba unos 54 MB. Ver [google-play.md](google-play.md). Un error del APK minificado se lee con su `mapping.txt`; el workflow de
GitHub lo sube como el artefacto `app-qa-mapping`.

## Generar el APK en GitHub (#82)

El workflow **APK de pruebas (qa)** compila el mismo APK en GitHub con la dirección y la clave del backend guardadas en el repositorio, para
cambiarlas sin un pull request. **Corre solo en cada merge a `quality`** (#90) y también se puede lanzar a mano (pestaña **Actions** > *APK de
pruebas (qa)* > *Run workflow*). No corre en pull requests. Ver [Ramas, APK de pruebas y releases](#ramas-apk-de-pruebas-y-releases-90).
El APK queda como artefacto `app-qa-apk` durante 7 días y, desde `quality`, también en el release `qa-latest`.

Se crean una sola vez en **Settings > Secrets and variables > Actions**:

| Qué | Nombre | Dónde |
|---|---|---|
| Dirección del backend (`https://...`) | `SMISHING_BACKEND_URL` | pestaña *Variables* |
| Clave del backend | `SMISHING_BACKEND_API_KEY` | pestaña *Secrets* |
| Keystore de depuración compartido, en base64 (#84) | `ANDROID_DEBUG_KEYSTORE_BASE64` | pestaña *Secrets* |

La versión de la aplicación se ve al final de **Ajustes > Acerca de**, para saber de un vistazo qué compilación está instalada. Cada APK de
GitHub trae la suya, `<último release>-qa.<número de ejecución>` (por ejemplo `1.0.0-qa.12`); una compilación local sin propiedades dice `1.0-qa`.

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

**Quién ve la clave:** el APK la lleva dentro, así que quien lo descargue puede extraerla. **El repositorio es público** (el plan gratuito no ofrece
protección de ramas en repositorios privados) y se acepta así (#90): `qa-latest` y los releases se bajan sin iniciar sesión. En el computador, una variable de entorno `SMISHING_BACKEND_API_KEY` olvidada gana sobre `local.properties`.

## Ramas, APK de pruebas y releases (#90)

Dos ramas de larga vida. **`quality`** es la predeterminada: ahí van los PRs de cada issue y ahí se prueba. **`main`** es lo publicado. Las dos están
protegidas por un ruleset (*Settings > Rules > Rulesets*): solo se entra por PR, con `android-verify` en verde, sin force push ni borrado, y
solo con "Merge commit". Los tags `v*` tienen su propio ruleset: no se borran ni se mueven.

| Quién | Qué pasa | Resultado |
|---|---|---|
| Tú fusionas un PR a `quality` | corre **APK de pruebas (qa)** (unos 5 minutos) | APK `qa` con versión `X.Y.Z-qa.N` en el release **`qa-latest`** |
| Tú fusionas un PR de `quality` a `main` | corre **Release (main)** | tag `vX.Y.Z` y release con el App Bundle |

### Bajar el último APK de pruebas

Un solo clic, sin entrar a Actions ni descomprimir:

```
https://github.com/smishing-thesis/smishing-mobile-app/releases/download/qa-latest/smishy-qa.apk
```

`qa-latest` es **un solo release que se reemplaza en cada merge** (no se acumula uno por merge); sus notas dicen la versión y el commit. Hay que
esperar a que termine el workflow antes de bajarlo; si no, se baja el APK del merge anterior. Como el APK `qa` siempre se firma con el mismo
keystore, se instala encima del anterior sin desinstalar. La fecha que GitHub muestra en ese release puede ser vieja: la que vale es la versión.

### Publicar un release

No hace falta cambiar código ni poner tags a mano.

1. Abre un PR de **`quality` a `main`** y ponle una etiqueta: **`major`**, **`minor`** o **`patch`** (gana la más fuerte; sin etiqueta cuenta como `patch`).
2. Cuando `android-verify` esté en verde, fusiónalo con "Merge commit".
3. El workflow calcula la versión desde el último tag (`v1.0.0` + `minor` = `1.1.0`), crea el tag, compila el App Bundle `release` (R8, sin demo),
   y publica el release con las notas generadas a partir de los PRs.

La versión sale del tag y de `-Psmishing.versionName`, `-Psmishing.versionCode` y `-Psmishing.qaBuild` (ver `app/build.gradle.kts`). El número de
`versionCode` es el de ejecución del workflow, que siempre sube. El cálculo está en `tools/next-version.sh`.

**Firma:** sin la clave de subida (#86) el bundle sale **sin firmar** y las notas del release lo dicen: no sirve para Google Play ni se instala. Para
probar la app en un teléfono se usa el APK de `qa-latest`. Cuando exista la clave, se crean estos secretos y el siguiente release sale firmado:
`ANDROID_UPLOAD_KEYSTORE_BASE64` (el `.jks` en base64, como en #84), `SMISHING_UPLOAD_STORE_PASSWORD`, `SMISHING_UPLOAD_KEY_ALIAS` y `SMISHING_UPLOAD_KEY_PASSWORD`.

### Configuración que vive en GitHub (no en el código)

- **Etiquetas** `major`, `minor` y `patch` del repositorio.
- **Check `solo-desde-quality`** (workflow *Promotion check*): hace que a `main` solo entren PRs de `quality`. Es obligatorio en el ruleset de las ramas,
  junto con `android-verify`.
- **`quality` no se sincroniza sola con `main`.** GitHub no deja poner a *GitHub Actions* en la *Bypass list* de un ruleset, así que un workflow no puede
  empujar a una rama protegida. Después de un release, GitHub dirá que `quality` está "N commits detrás de `main`": son solo los commits de fusión, sin
  código, y no hace falta hacer nada. El siguiente PR de `quality` a `main` se fusiona igual.

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
