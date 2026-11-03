# Modelo LiteRT en el dispositivo

Registro de #34 (RF-03, RNF-06): el `.tflite` corre en el teléfono, sin red, y decide igual que Keras.
Última verificación: 2026-11-02. Reporte completo: [reportes/paridad-modelo-api29.json](reportes/paridad-modelo-api29.json).

## Fuente

Pre-release `v1.0.0` de `smishing-thesis/charcnn-smishing-model` (modelo 1.0.0, preprocesamiento 1.1.0).

| Archivo | Dónde está | SHA-256 |
|---|---|---|
| `smishing_charcnn.tflite` (123 544 bytes) | `app/src/main/assets/` (se empaqueta en la app) | `dfe3fa4148942c1fdbfc7df416bc1bac0521f4dc80bb9d9f356eb95b542dc380` |
| `parity_report.json` (puntaje de Keras por caso) | `app/src/androidTest/assets/` (solo pruebas) | `010018dc6bdcb8ca97f4fbeb9be1c990b3ef14dab8ae31f35863d7f03dd3cb2e` |

El hash del modelo coincide con el que declara `preprocessing_config.json` (`export.tflite_sha256`). Se comprueba
en una prueba de la JVM y otra vez al cargar el modelo en la app: si no coincide, no se carga
(`ModelIntegrityException`). Los 52 mensajes del reporte son sintéticos.

## Código

| Pieza | Capa | Qué hace |
|---|---|---|
| `ModelInfo` | `domain.classification` | versión, umbral y hash del modelo; `isSmishing(score)` es `score > umbral` |
| `ModelInfoParser` | `data.classification` | lee esos datos de `preprocessing_config.json` |
| `LiteRtModel` | `data.classification` | verifica el hash, carga el `.tflite` con LiteRT y devuelve el puntaje de una entrada ya preprocesada |

El umbral sale del JSON (0.9034745693206787), no se usa 0.5. La política de entrada (menos de 15 caracteres no se
clasifica) no se aplica aquí: es de #7. En esta comparación se mide la decisión del modelo sobre los 52 casos.

## Cómo verificar

Con el emulador de API 29 abierto, en modo avión y con `JAVA_HOME` apuntando al JDK 17 de Android Studio
(ver [entorno.md](entorno.md)):

```powershell
adb shell cmd connectivity airplane-mode enable
adb shell settings get global airplane_mode_on      # debe imprimir 1
.\gradlew.bat connectedDebugAndroidTest
```

La prueba `LiteRtParityInstrumentedTest` preprocesa cada mensaje con el código de #33, corre LiteRT y compara la
decisión con la de Keras. Gradle deja el reporte en
`app/build/outputs/connected_android_test_additional_output/`. No incluye los mensajes, solo identificadores,
puntajes y decisiones.

## Resultado (2026-11-02)

| Dato | Valor |
|---|---|
| Dispositivo | emulador Google "Android SDK built for x86_64" (AVD Pixel 4) |
| Android | 10 (API 29) |
| ABI | x86_64 |
| LiteRT | 2.3.2 (`org.tensorflow.lite.Interpreter`) |
| Tamaño del modelo | 123 544 bytes |
| Entrada / salida | `FLOAT32` `[1, 160]` / `FLOAT32` `[1, 1]` |
| Sin conexión | modo avión activado y la app no declara el permiso `INTERNET` |

| Comparación | Resultado |
|---|---|
| Casos | 52 |
| Decisiones distintas a Keras | **0** |
| Diferencia de puntaje contra Keras | máxima 0.00555, media 0.00083 |
| Diferencia contra el TFLite de escritorio | máxima 1.2e-7 (redondeo de punto flotante) |

La diferencia máxima contra Keras coincide con la que ya registraba el repo del modelo (0.00555), y contra el
TFLite de escritorio el dispositivo da el mismo puntaje salvo redondeo.

Casos más cercanos al umbral, que son los que podrían cambiar de decisión: `ligature_nfkc_01` (0.929) queda
0.026 por encima, y `robustness_12` (0.864) queda 0.039 por debajo. Ambos coinciden con Keras.

## Lo que no cubre

- Solo se probó en un emulador x86_64 con API 29. No se probó en ARM (`arm64-v8a`, `armeabi-v7a`), en un
  dispositivo físico ni en API 35.
- "Sin conexión" se respalda con el modo avión y la ausencia del permiso `INTERNET`, no con una captura de
  tráfico. La prueba registra ambos datos pero no los exige: cuando #18 agregue el permiso para el aporte
  opcional, el reporte mostrará `declaresInternetPermission: true` y habrá que revisar este criterio.
- Son 52 casos sintéticos. Confirman que el teléfono reproduce a Keras, no que el modelo acierte en mensajes
  reales: esos límites son del modelo (ver `.claude/model-contract.md`).
- No se midió la demora ni la memoria de la inferencia (#21).
