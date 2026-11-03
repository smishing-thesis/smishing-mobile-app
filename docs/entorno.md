# Entorno Android y LiteRT

Registro del entorno de build del prototipo (issue #1, RNF-08). Última verificación: 2026-11-02.

## Configuración del proyecto

| Elemento | Valor |
|---|---|
| Nombre / paquete | Smishing Detector / `com.app.smishing` |
| minSdk | 29 |
| targetSdk | 34 |
| compileSdk | 35 (lo exige `androidx.core 1.15.0`) |
| Java (fuente y destino) | 11 |
| Lenguaje | Kotlin, scripts de Gradle en Kotlin DSL |

## Herramientas

| Herramienta | Versión |
|---|---|
| Android Gradle Plugin | 8.13.2 |
| Gradle (wrapper) | 8.13 |
| Kotlin | 2.4.21 |
| JDK para Gradle | de 17 a 23 (lo que acepta Gradle 8.13); verificado con Temurin 17.0.13 |
| Android Studio | Narwhal 3 Feature Drop (2025.1.3) o más nueva; verificado con Rabbit 2 (2026.2.2) |

Las versiones están en `gradle/libs.versions.toml`.

### Android Studio y JDK

- **Versión mínima del IDE.** AGP 8.13 exige Android Studio Narwhal 3 (2025.1.3) o más nuevo. Con Koala
  (2024.1.2), el sync falla: `The project is using an incompatible version (AGP 8.13.2)... Latest supported
  version is AGP 8.6.1`. La compilación por terminal funciona igual, pero el IDE no.
- **JDK del IDE.** El JDK que trae Android Studio cambia con la versión: Koala traía el 17, pero Rabbit 2 trae el 25,
  que Gradle 8.13 rechaza (`Gradle 8.13 supports Java versions between 1.8 and 23`). Si el sync falla con
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

## Advertencias conocidas

- Kotlin avisa que Gradle 8.13 quedará sin soporte en Kotlin 2.5.0, que exigirá Gradle 8.14.4 o superior.
  No afecta la compilación actual.
