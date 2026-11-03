# Preprocesamiento en Kotlin

Registro de #33 (RF-03). La app convierte cada SMS en la entrada del modelo exactamente como lo hace Python.
La definición de las reglas vive en `charcnn-smishing-model` (`docs/preprocessing.md`); aquí solo se registra cómo
se implementó y cómo se verificó. Última verificación: 2026-11-02.

## Fuente

Pre-release `v1.0.0` de `smishing-thesis/charcnn-smishing-model`: modelo 1.0.0, preprocesamiento 1.1.0.

| Archivo | Dónde está | SHA-256 |
|---|---|---|
| `preprocessing_config.json` | `app/src/main/assets/` (se empaqueta en la app) | `54cc1625d6fe52bbe288c15673176277fccdf404a5031b471ac6d6d37a7fad2e` |
| `preprocessing_vectors.json` | `app/src/sharedTest/assets/` (solo pruebas) | `2647590833651a813cf5ec784bfa3fa626d7f3e17af7192a84cca7c3517a8748` |

Los hashes son los de los archivos tal como se bajaron de la release. Los 38 mensajes de los vectores son
sintéticos (enlaces como `http://url`, números secuenciales); no hay mensajes reales.

## Código

| Pieza | Capa | Qué hace |
|---|---|---|
| `PreprocessingConfig`, `PreprocessingResult` | `domain.preprocessing` | modelo de las reglas y resultado de cada etapa |
| `TextPreprocessor` | `domain.preprocessing` | limpieza, normalización v2, enmascarado, política de entrada y codificación |
| `PythonCompat` | `domain.preprocessing` | semántica de Python que Java no trae (ver abajo) |
| `PreprocessingConfigParser` | `data.preprocessing` | lee el JSON con `org.json` y rechaza versiones que no conoce |
| `PreprocessingConfigLoader` | `data.preprocessing` | carga el JSON desde los assets de la app |

Las reglas, el alfabeto, la tabla de homoglifos, la longitud y el mínimo de caracteres se leen del JSON; no
están copiados al código. La versión del preprocesamiento queda en `PreprocessingResult.preprocessingVersion`
para guardarla con cada resultado. Si el JSON declara otra versión (hoy solo se conoce `1.1.0`, con
normalización `2` y forma `NFKC`), el parser lanza `UnsupportedPreprocessingException`.

## Diferencias con Python que se replican

- **`\s`, `\S`, `\v`, `\d`, `\w`, `\W` y `\b`** de las expresiones regulares se traducen de forma explícita.
  La JVM los trata como ASCII y el ICU de Android como Unicode; además Android rechaza la bandera
  `UNICODE_CHARACTER_CLASS` (`Unsupported flags: 256`, visto en API 29). La traducción explícita da el mismo
  resultado en ambos.
- **Espacios:** el conjunto de `str.isspace()` de Python incluye U+001C a U+001F, que Java no cuenta.
- **Dígitos:** `str.isdigit()` acepta superíndices y círculos numerados que `Character.isDigit` no. La tabla
  sale de Python 3.12 (Unicode 15.0).
- **Longitudes** en puntos de código (`codePointCount`), no en unidades UTF-16.
- **NFKC** con `java.text.Normalizer`, y `lowercase(Locale.ROOT)`.

## Cómo verificar

Con `JAVA_HOME` apuntando al JDK 17 de Android Studio (ver [entorno.md](entorno.md)):

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat connectedDebugAndroidTest
```

Las dos usan el mismo comprobador (`PreprocessingVectorsChecker`, en `app/src/sharedTest/java`): para cada
vector compara `cleaned`, `normalized`, `masked`, `cleaned_chars`, `classify` y los 160 valores de `input_ids`.
También comprueba que la versión y el `spec_sha256` de los vectores coincidan con los del JSON, y que los
vectores sigan cubriendo homoglifos, emoji, ancho completo, espacios de ancho cero, saltos de línea, texto largo
y abreviaciones.

## Resultado (2026-11-02)

| Ejecución | Pruebas | Fallos | Vectores |
|---|---|---|---|
| `testDebugUnitTest` (JVM) | 8 (4 de vectores, 3 del parser, 1 de plantilla) | 0 | 38 de 38 |
| `connectedDebugAndroidTest`, emulador Pixel 4 API 29 (x86_64) | 6 (2 de preprocesamiento, 3 de entorno, 1 de plantilla) | 0 | 38 de 38 |

Para comprobar que las pruebas no son vacías se rompió a propósito la tabla de homoglifos: la prueba falló en
`homoglyph_01` (3 discrepancias) y se revirtió.

## Lo que no cubre

- Solo se probó en un emulador x86_64 con API 29. No se probó en API 35 con estos vectores, ni en un
  dispositivo físico ni en ARM.
- Los vectores son 38 casos. Casos Unicode raros que no están en ellos (por ejemplo, los dígitos de
  `PythonCompat.EXTRA_DIGITS`) siguen la semántica de Python 3.12, pero ningún vector los ejercita
  directamente. Las versiones de Unicode de Android varían según el sistema.
- No se midió la demora del preprocesamiento ni el consumo de memoria (#21).
- Aquí no se carga el modelo; la comparación del `.tflite` con Keras es #34.
