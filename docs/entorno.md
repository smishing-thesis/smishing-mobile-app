# Entorno Android y LiteRT

Registro del entorno de build del prototipo (issue #1, RNF-08). Última verificación: 2026-11-23.

## Configuración del proyecto

| Elemento | Valor |
|---|---|
| Nombre / paquete | Smishy / `com.app.smishing` |
| minSdk | 29 |
| targetSdk | 36 (#63; es lo que Google Play exige desde el 31 de agosto de 2026 para publicar apps nuevas y actualizaciones) |
| compileSdk | 37 (lo exigen `androidx.core` 1.19 y Compose 1.12; AGP 9.5 admite hasta 37) |
| Java (fuente y destino) | 11 |
| Lenguaje | Kotlin, scripts de Gradle en Kotlin DSL |

## Herramientas

| Herramienta | Versión |
|---|---|
| Android Gradle Plugin | 9.5.1 |
| Gradle (wrapper) | 9.9.1 |
| Kotlin | 2.4.21 |
| JDK para Gradle | 17 o superior (AGP 9.5 pide 17 como mínimo; Gradle 9.9.1 acepta hasta el 25); verificado con Temurin 17.0.13 |
| Android Studio | Rabbit 2 (2026.2.2) o más nueva (la primera que admite AGP 9.5); verificado con Rabbit 2 (2026.2.2) |

Las versiones están en `gradle/libs.versions.toml`.

### Android Studio y JDK

- **Versión mínima del IDE.** AGP 9.5 exige Android Studio Rabbit 2 (2026.2.2) o más nuevo (Rabbit 1 llega hasta AGP 9.4).
  Con una versión más vieja el sync falla con `The project is using an incompatible version (AGP 9.5.1)...`. La compilación
  por terminal funciona igual, pero el IDE no.
- **JDK del IDE.** El JDK que trae Android Studio cambia con la versión: Koala traía el 17, pero Rabbit 2 trae el 25,
  que Gradle 9.9.1 sí acepta (con Gradle 8.13 daba `supports Java versions between 1.8 and 23`). Si el sync falla con
  `Incompatible Gradle JVM version`, en **Settings → Build, Execution, Deployment → Build Tools → Gradle** elige
  en **Gradle JDK** un JDK de 17 a 21 (o usa **Download JDK…**). Esa opción queda en `.idea/` y
  `.gradle/config.properties`, que no se versionan: cada persona la configura en su equipo.
- No aceptes el "AGP Upgrade Assistant" ni "Migrate to Gradle Daemon toolchain" que ofrece el IDE: el primero
  cambia la versión de AGP verificada y el segundo escribe en el repo un archivo que fija el JDK, y eso es una
  decisión de equipo.

## Dependencias de inferencia

| Dependencia | Versión | Motivo |
|---|---|---|
| `com.google.ai.edge.litert:litert` | 2.3.2 | Runtime LiteRT. La API usada es `org.tensorflow.lite.Interpreter`, compatible con modelos TFLite exportados desde Keras. |

Solo para pruebas en la JVM: `org.json:json` 20260814 (en el dispositivo se usa el `org.json` de Android).

## Interfaz (Compose)

| Dependencia | Versión | Motivo |
|---|---|---|
| `androidx.compose:compose-bom` | 2026.09.00 (Compose UI 1.12.1) | fija las versiones de Compose |
| `androidx.activity:activity-compose` | 1.13.0 | actividades con Compose |
| `org.jetbrains.kotlin.plugin.compose` | 2.4.21 (la de Kotlin) | compilador de Compose |
| `material-icons-core` | del BOM | iconos básicos; el set extendido se evita por peso |

**Compose 1.12 y AGP 9.** Compose UI 1.12 (BOM 2026.08.00 y 2026.09.00) y `androidx.core` 1.19 exigen AGP 9.1 o superior y
`compileSdk` 37, por eso se subió el AGP a 9.5.1 (#63). Detalle de la librería en [design-system.md](design-system.md).

El AAR incluye `libLiteRt.so` para `arm64-v8a`, `armeabi-v7a` y `x86_64`. No incluye `x86`.

### Por qué Kotlin 2.4.21

LiteRT 2.3.2 se publica con metadatos de Kotlin 2.4.0. Con Kotlin 1.9.0 (el de la plantilla de Android Studio)
la compilación pasaba mientras la app no tenía código Kotlin, pero fallaba al compilar la primera prueba:
`Module was compiled with an incompatible version of Kotlin`. Por eso se subieron Kotlin, AGP y Gradle juntos.

## Cómo compilar y probar

Con `JAVA_HOME` apuntando a un JDK de 17 a 23 (no el 25 que trae Android Studio Rabbit):

```powershell
$env:JAVA_HOME = '<ruta a un JDK 17>'     # por ejemplo, la carpeta de Eclipse Temurin 17
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat connectedDebugAndroidTest
```

`connectedDebugAndroidTest` necesita un emulador o dispositivo conectado. Si Gradle no cambió nada, marca las
pruebas como `UP-TO-DATE` y reutiliza el resultado anterior sin ejecutarlas; para forzar la ejecución añade
`--rerun` (por ejemplo, `.\gradlew.bat testDebugUnitTest --rerun`).

Las pruebas del preprocesamiento y del modelo están en [preprocesamiento.md](preprocesamiento.md) y
[modelo-litert.md](modelo-litert.md).

## Prueba funcional

`app/src/androidTest/java/com/app/smishing/LiteRtEnvironmentTest.kt` comprueba, sin cargar ningún modelo:

- la app declara minSdk 29;
- la clase `org.tensorflow.lite.Interpreter` está disponible;
- la librería nativa `libLiteRt.so` carga en el dispositivo.

### Resultado (2026-11-02)

`connectedDebugAndroidTest`: BUILD SUCCESSFUL, 4 pruebas y 0 fallos en cada dispositivo (las 3 de arriba y
`ExampleInstrumentedTest` de la plantilla).

| Dispositivo | Android | ABI |
|---|---|---|
| Emulador Pixel 4, API 29 (`system-images;android-29;google_apis_playstore;x86_64`) | 10 (el mínimo declarado) | x86_64 |
| Emulador Pixel 7, API 35 | 15 | x86_64 |

La imagen de API 29 debe ser `x86_64`: la de `x86` no sirve, porque LiteRT 2.3.2 no incluye librería nativa para esa ABI.

### Lo que esta verificación no cubre

- No se probó en un dispositivo físico.
- No se probaron las ABI `arm64-v8a` ni `armeabi-v7a`; los emuladores usaron `x86_64`.
- Esta prueba no carga ningún modelo. La carga del `.tflite` y su comparación con Keras están en
  [modelo-litert.md](modelo-litert.md) (#34).

### Revalidación con el JDK 17 y Android Studio Rabbit 2 (2026-11-02)

Tras actualizar Android Studio se repitieron las pruebas por terminal con Temurin 17.0.13 y `--rerun`:
`testDebugUnitTest` con 12 pruebas y 0 fallos, y `connectedDebugAndroidTest` en el emulador Pixel 4 API 29 con 8
pruebas y 0 fallos. El sync de Gradle en el IDE termina correctamente (con la advertencia de Gradle 8.13).

## targetSdk 36 (#63)

Con `targetSdk` 36, Android dibuja la app **bajo las barras del sistema** (edge-to-edge) y ya no se puede evitar. Qué cambió en la app:

- `MainActivity` llama a `enableEdgeToEdge()`: barra de estado transparente con iconos claros y barra de navegación transparente con iconos
  oscuros (el diseño es solo claro).
- `SmishingTopBar` pinta su color hasta arriba y deja su contenido bajo la barra de estado (`windowInsetsPadding(WindowInsets.statusBars)`).
- `StatusBarIcons` pone los iconos de la barra de estado oscuros sobre fondo claro (el paso 2 de bienvenida y el del permiso) y claros sobre el
  azul (barra superior, bienvenida, pantalla de carga).
- Las pantallas sin barra inferior (conversación, nuevo mensaje, detalle del análisis, onboarding) dejan lugar a la barra de navegación.
- `MainActivity` declara `windowSoftInputMode="adjustResize"`. Sin eso, con el teclado abierto el sistema desplazaba la ventana y,
  además, la app sumaba el espacio del teclado (`imePadding`): la barra superior quedaba fuera de la pantalla.
- Se cambió `quadraticBezierTo` por `quadraticTo` en `SmishyAnimation.kt` (el único aviso de deprecación que quedaba en el código).

### Verificación (2026-11-23)

Emulador **Pixel 7, API 36** (`system-images;android-36;google_apis_playstore;x86_64`, Android 16), con la app compilada con `targetSdk` 36:

- `testDebugUnitTest`: 296 pruebas, 0 fallos, 1 omitida. `connectedDebugAndroidTest`: 95 pruebas, 0 fallos, 3 omitidas (las que solo
  aplican a otras versiones de Android). La compilación con `--warning-mode all` no muestra avisos de deprecación.
- A mano, con capturas: pantalla de carga, bienvenida, paso 2, Inicio, Mensajes, Historial, Ajustes, conversación (también con el teclado
  abierto), nuevo mensaje y detalle del análisis. Ningún contenido queda bajo la barra de estado ni la de navegación, y los iconos de la barra
  de estado se leen sobre cada fondo.
- La alineación de 16 KB de las bibliotecas nativas dentro del APK pasa `zipalign -c -P 16`; no se revisó el alineamiento ELF de cada una.

**No se verificó:** el paso 3 (permiso de lectura) en pantalla, porque con el rol de SMS concedido Android otorga ese permiso y no se puede
quitar para llegar a él; su diseño usa el mismo patrón que el paso 2. Tampoco Android 11 a 14, ni un teléfono real.

**Android 10 (API 29, el `minSdk`), mismo APK con `targetSdk` 36:** emulador Pixel 4 API 29, a mano y con capturas de Inicio, Mensajes,
Historial, Ajustes, una conversación (con el teclado abierto: la barra superior sigue visible y el campo queda sobre el teclado) y el detalle
del análisis. Todo se ve igual que en API 36, con iconos de la barra de estado claros sobre el azul y nada bajo la barra de navegación. No se
corrieron las pruebas automáticas en API 29 con este `targetSdk`.

Otros cambios de comportamiento de Android 15 y 16 **no se tocaron** porque la app no los usa: no hay servicios en primer plano, alarmas exactas
ni `onBackPressed` (solo `BackHandler`, compatible con el gesto de volver predictivo).

## Advertencias conocidas

- Kotlin avisa que Gradle 8.13 quedará sin soporte en Kotlin 2.5.0, que exigirá Gradle 8.14.4 o superior.
  No afecta la compilación actual.
