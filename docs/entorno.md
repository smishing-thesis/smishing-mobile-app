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
| JDK para compilar | 17 (el que trae Android Studio, `jbr`) |

Las versiones están en `gradle/libs.versions.toml`.

## Dependencias de inferencia

| Dependencia | Versión | Motivo |
|---|---|---|
| `com.google.ai.edge.litert:litert` | 2.3.2 | Runtime LiteRT. La API usada es `org.tensorflow.lite.Interpreter`, compatible con modelos TFLite exportados desde Keras. |

El AAR incluye `libLiteRt.so` para `arm64-v8a`, `armeabi-v7a` y `x86_64`. No incluye `x86`.

### Por qué Kotlin 2.4.21

LiteRT 2.3.2 se publica con metadatos de Kotlin 2.4.0. Con Kotlin 1.9.0 (el de la plantilla de Android Studio)
la compilación pasaba mientras la app no tenía código Kotlin, pero fallaba al compilar la primera prueba:
`Module was compiled with an incompatible version of Kotlin`. Por eso se subieron Kotlin, AGP y Gradle juntos.

## Cómo compilar y probar

Con `JAVA_HOME` apuntando al JDK 17 de Android Studio:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleDebug
.\gradlew.bat connectedDebugAndroidTest
```

`connectedDebugAndroidTest` necesita un emulador o dispositivo conectado.

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
- No se cargó ni ejecutó ningún modelo. Eso corresponde a #33 y #34.

## Advertencias conocidas

- Kotlin avisa que Gradle 8.13 quedará sin soporte en Kotlin 2.5.0, que exigirá Gradle 8.14.4 o superior.
  No afecta la compilación actual.
