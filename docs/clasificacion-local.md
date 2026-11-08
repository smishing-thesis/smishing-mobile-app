# Clasificación local del SMS

Registro de #7 (RF-03, RNF-03): la app clasifica un SMS dentro del teléfono, sin consultar el backend.
Última verificación: 2026-11-07. Reporte completo: [reportes/clasificacion-local-api29.json](reportes/clasificacion-local-api29.json).

## Qué hace

Une el preprocesamiento ([preprocesamiento.md](preprocesamiento.md), #33) y el modelo ([modelo-litert.md](modelo-litert.md), #34)
en un solo paso: texto del SMS entra, resultado sale.

1. Preprocesa el texto con las reglas del JSON.
2. Si el texto limpio tiene menos caracteres que el mínimo del JSON (15), **no se clasifica**: el modelo no se llama y
   no se alerta ni se bloquea.
3. Si no, el modelo devuelve un puntaje y se decide con el umbral del JSON (`score > 0.9035`, no 0.5).
4. El resultado guarda el puntaje, la decisión y las versiones del modelo y del preprocesamiento (RNF-07).

## Código

| Pieza | Capa | Qué hace |
|---|---|---|
| `MessageClassifier`, `ModelScorer` | `domain.classification` | contrato: `classify(texto)` y puntuador de una entrada ya preprocesada |
| `ClassificationResult` | `domain.classification` | `Classified` (puntaje, decisión, versiones) o `NotClassified` (`TOO_SHORT`) |
| `LocalMessageClassifier` | `domain.classification` | ordena los pasos de arriba; no usa red ni conoce LiteRT |
| `LiteRtModel` | `data.classification` | implementa `ModelScorer`; serializa las llamadas porque el intérprete de LiteRT no admite hilos simultáneos |
| `OnDeviceClassifier` | `data.classification` | arma todo desde los `assets` con `OnDeviceClassifier.create(context)`; es `AutoCloseable` |

El modelo se carga una sola vez, al crear el clasificador, y se reutiliza. Quien lo cree debe conservar la instancia y
llamar a `close()` cuando ya no la necesite. Cómo se comparte en toda la app queda para cuando se decida la
inyección de dependencias.

## Cómo verificar

Con el emulador de API 29 abierto, en modo avión y con `JAVA_HOME` en un JDK de 17 a 23 (ver [entorno.md](entorno.md)):

```powershell
adb shell cmd connectivity airplane-mode enable
adb shell settings get global airplane_mode_on      # debe imprimir 1
.\gradlew.bat testDebugUnitTest --rerun
.\gradlew.bat connectedDebugAndroidTest --rerun
```

- `LocalMessageClassifierTest` (JVM, con un puntuador falso): política de entrada y su límite, umbral, versiones del
  resultado, y que el modelo recibe exactamente la entrada del preprocesador.
- `OnDeviceClassifierInstrumentedTest` (dispositivo, modelo real): clasifica de principio a fin los 52 mensajes
  sintéticos del reporte de Keras y repite las llamadas desde 4 hilos a la vez. Escribe un reporte sin los mensajes.

## Resultado (2026-11-07)

| Dato | Valor |
|---|---|
| Dispositivo | emulador Google "Android SDK built for x86_64" (AVD Pixel 4), Android 10 (API 29), x86_64 |
| Sin conexión | modo avión activado y la app no declara el permiso `INTERNET` |
| Modelo / preprocesamiento | 1.0.0 / 1.1.0 |
| Casos | 52 |
| Clasificados / no clasificados | 46 / 6 |
| Decisiones distintas a Keras (entre los clasificados) | **0** |
| Mensajes que la política trató distinto a lo esperado | **0** |
| Llamadas simultáneas (4 hilos, 156 llamadas) | mismos resultados que en secuencia |

Los 6 no clasificados son `emoji_02`, `unicode_digits_01`, `min_length_01`, `min_length_03`, `empty_01` y `empty_02`,
justo los que los vectores marcan con `classify = false`. Si hubieran llegado al modelo, Keras habría marcado
cuatro de ellos como sospechosos (puntaje 0.993): los dos mensajes vacíos y los dos de solo emoji (uno de ellos de
8 emoji). Para esos casos la política de entrada es lo que evita la falsa alerta.

## Lo que no cubre

- Solo se probó en un emulador x86_64 con API 29: no hay prueba en ARM, en dispositivo físico ni en API 35.
- "Sin consultar el backend" se respalda con el modo avión y la ausencia del permiso `INTERNET`, y porque este
  código no usa red; no con una captura de tráfico. La prueba registra ambos datos pero no los exige: cuando #18
  agregue el permiso para el aporte opcional, el reporte mostrará `declaresInternetPermission: true` y habrá que
  revisar este criterio.
- La prueba de llamadas simultáneas pasa, pero no demuestra que no haya condiciones de carrera: no se comprobó que
  falle al quitar la sincronización de `LiteRtModel`.
- Son 52 mensajes sintéticos. Confirman que el flujo completo reproduce a Keras, no que el modelo acierte en
  mensajes reales: sus límites están en `.claude/model-contract.md`.
- No se midió la demora ni la memoria (#21).
- Aquí solo se clasifica un texto. Conectar la recepción de SMS (#4, ya hecha) con el análisis, asignar un nivel de riesgo (#9), mostrar las señales (#8),
  alertar (#10) y guardar el historial (#12) son otros issues.
