# Señales presentes en un mensaje

Registro de #8 (RF-04): la app identifica, cuando corresponden, las señales de **urgencia**, **enlace**, **suplantación** y
**solicitud de datos** en un SMS. Última verificación: 2026-11-08.

> **ATENCIÓN.** Estas señales son **reglas escritas a mano**, no salen del modelo. Se probaron con mensajes sintéticos, no
> con mensajes reales: las cifras de abajo no son una estimación de cómo funcionan en un teléfono. Y todavía no se
> muestran en pantalla (eso es #11) ni se calculan sobre los mensajes que llegan (falta dónde guardar los resultados, #12).

## Qué es cada señal

El modelo solo devuelve un puntaje, y ningún repo de la organización define las señales: la definición es de esta historia
y debe revisarse para la memoria.

| Señal | Cuándo se detecta | Ejemplos (inventados) |
|---|---|---|
| **Urgencia** | el mensaje presiona a actuar ya: amenaza de bloqueo o multa, plazo en horas, "ahora mismo", "evite el embargo" | "Su cuenta será bloqueada hoy", "responda antes de 24 horas" |
| **Enlace** | hay una URL o un dominio ("http://…", "www.…", "bit.ly/…", "interbank.pe") | "Verifique aquí http://bcp-seguro.info" |
| **Suplantación** | nombra una entidad conocida **y** (trae un enlace que no es de ella **o** pide datos) | "BCP: … http://bcp-seguro.info" |
| **Solicitud de datos** | un verbo de pedir ("ingrese", "confirme", "actualice", "valide") cerca de un dato ("clave", "datos", "identidad", "dirección") | "Actualice sus datos en…" |

Dos cuidados para no acusar a mensajes legítimos:
- **Suplantar no es nombrar.** Un aviso real de Interbank con su enlace oficial no es suplantación: lo es si el enlace va a
  otro dominio o si pide datos. Por eso cada entidad lleva su lista de dominios oficiales.
- **Advertir no es pedir.** "No compartas tu código" y "usa el código 908172 para confirmar" no son una solicitud de datos:
  solo cuenta un verbo de pedir en imperativo junto al dato.

## Cómo funciona

1. Las reglas están en `app/src/main/assets/signal_rules.json` (versionado, `1.0.0`), no en el código: cambiar una lista de
   palabras no exige tocar Kotlin. Un lector (`SignalRulesParser`) rechaza versiones desconocidas, patrones que no compilan
   y reglas repetidas, nombrando la regla.
2. El detector (`RuleBasedSignalDetector`) aplica las reglas a una copia del mensaje del **mismo largo**: en minúsculas, sin
   tildes, con las letras parecidas del modelo (cirílico, griego) pasadas a latinas y con las sustituciones de quien ofusca
   ("8CP", "cu3nta", "Y4pe"). Como tiene el mismo largo, **el fragmento que muestra cada señal es un trozo del mensaje tal
   como llegó**.
3. Cada señal lleva el **id de la regla** que la produjo (por ejemplo `urgency.status_threat`, `impersonation.bcp`,
   `link.url`) y el análisis lleva la **versión de las reglas**: es lo que #9 necesita para que el nivel de riesgo sea
   trazable.
4. Las señales salen en el orden del diseño ("¿Por qué es riesgoso?"): urgencia, suplantación, enlace, solicitud de datos.

El texto del mensaje no se registra en logs: `DetectedSignal` no muestra su fragmento en `toString`.

### Código

| Pieza | Capa | Qué hace |
|---|---|---|
| `SignalType`, `DetectedSignal`, `SignalAnalysis`, `SignalDetector` | `domain.signals` | los tipos y el contrato |
| `SignalRules`, `PatternRule`, `EntityRule` | `domain.signals` | las reglas ya leídas |
| `RuleBasedSignalDetector`, `foldText`, `toHomoglyphs` | `domain.signals` | el detector y la copia normalizada del texto |
| `SignalRulesParser` | `data.signals` | lee `signal_rules.json` |
| `AndroidSignalDetectorFactory` | `data.signals` | arma el detector con las reglas empaquetadas y los homoglifos del modelo |

## Lo que hay que revisar de las reglas

- **Los dominios oficiales** (`officialDomains`) son los de las entidades más comunes en Perú. Los que aparecen en los
  mensajes legítimos del conjunto controlado (`interbank.pe`, `claro.com.pe`, `olvacourier.com`, `sunat.gob.pe`, `bbva.pe`)
  están respaldados por esos mensajes; **los demás los puse de memoria y alguien del equipo debe comprobarlos**. Si falta un
  dominio oficial, un aviso real con ese enlace se acusaría de suplantación.
- **La lista de entidades** es corta (bancos, billeteras, SUNAT, RENIEC, SAT, MTC, courier y operadoras). Una entidad que no
  esté no se detecta.
- Los nombres que también son palabras comunes ("claro", "sat") solo cuentan seguidos de ":" o dentro de un enlace.

## Cómo verificar

Con el emulador de API 29 abierto y `JAVA_HOME` en un JDK de 17 a 23 (ver [entorno.md](entorno.md)):

```powershell
.\gradlew.bat testDebugUnitTest --rerun
.\gradlew.bat connectedDebugAndroidTest --rerun
```

- `SignalDetectorContract` (`sharedTest`) corre igual en la JVM (`SignalDetectorTest`) y en el dispositivo
  (`SignalDetectorInstrumentedTest`), para comprobar que el motor de expresiones regulares de Android da lo mismo que el de la
  JVM. Cubre cada señal con ejemplos que sí y que no, la ofuscación, los homoglifos, la trazabilidad, el orden, la
  privacidad y el conjunto controlado.
- `SignalRulesParserTest` (JVM): el JSON empaquetado se lee, y se rechazan una versión desconocida, un patrón inválido, un
  JSON mal formado, una sección que falta, una lista de dominios vacía y reglas repetidas.
- **El conjunto controlado** es `controlled_messages.csv` del repo del modelo (`tests/fixtures`): 84 mensajes sintéticos con
  categoría: 49 legítimos sin enlace, 5 legítimos con su enlace oficial y 30 de smishing. Se copió a
  `app/src/sharedTest/assets` solo para estas pruebas.

## Resultado (2026-11-08)

Emulador Pixel 4, Android 10 (API 29), x86_64, con Temurin 17 y `--rerun`.

| Ejecución | Pruebas | Fallos |
|---|---|---|
| `testDebugUnitTest` (JVM) | 106 (35 nuevas) | 0 |
| `connectedDebugAndroidTest` | 84 (28 nuevas): 83 ejecutadas y 1 omitida a propósito | 0 |

Las 27 pruebas del contrato corren **igual en la JVM y en el dispositivo**, así que el motor de expresiones regulares de
Android da lo mismo que el de la JVM para estas reglas.

**Las pruebas no son vacías.** Se quitó "datos" de los datos que se piden y fallaron 3 pruebas del detector. Se revirtió.

**Conjunto controlado** (84 mensajes sintéticos del repo del modelo). Cuántos mensajes de cada categoría producen cada señal:

| Categoría | Mensajes | Urgencia | Suplantación | Enlace | Solicitud de datos |
|---|---|---|---|---|---|
| Legítimos sin enlace (OTP, avisos del banco, de entrega, de servicio, promociones y personales; 8 categorías) | 49 | 0 | 0 | 0 | 0 |
| Legítimos con su enlace oficial | 5 | 0 | 0 | 5 | 0 |
| Smishing: banco | 8 | 6 | 7 | 8 | 3 |
| Smishing: entrega | 5 | 1 | 3 | 5 | 1 |
| Smishing: gobierno | 4 | 2 | 4 | 4 | 1 |
| Smishing: ofuscado | 6 | 5 | 6 | 6 | 1 |
| Smishing: premios | 4 | 1 | 2 | 4 | 1 |
| Smishing: cortos | 3 | 0 | 1 | 3 | 0 |
| **Smishing, total** | **30** | **15** | **23** | **30** | **7** |

- Los 30 mensajes de smishing traen un enlace y el detector lo ve en los 30.
- **25 de 30 tienen al menos otra señal además del enlace**; cinco solo tienen el enlace (ver "Lo que no cubre").
- Ningún mensaje legítimo sin enlace produjo señales, y los cinco con su enlace oficial solo produjeron "enlace" (ninguna
  suplantación). Léase con la advertencia de "Lo que no cubre": las reglas se afinaron viendo esos mismos mensajes.
- Los mensajes ofuscados ("8CP", "cu3nta", "I n t e r b a n k", "Y4pe") se reconocen igual que los normales.

## Lo que no cubre

- **Son reglas hechas por mí y probadas con 84 mensajes sintéticos que ya había visto al escribirlas.** Para evitar falsas
  alarmas quité o acoté reglas mirando esos mismos mensajes legítimos (por ejemplo, "vence pronto" y "cancelada" se
  descartaron por aparecer en avisos reales, y "cuenta" no cuenta como dato). Por eso **el 0 falsas alarmas no es una
  estimación**: con mensajes reales habrá más. La medición honesta necesita mensajes que no se hayan usado para escribir las
  reglas.
- **No se midió con mensajes reales** ni se comparó con lo que el modelo detecta.
- Cinco mensajes de smishing del conjunto solo producen "enlace" (`smishing_bank_08`, `smishing_prize_02`,
  `smishing_prize_04`, `smishing_short_01`, `smishing_short_03`): no tienen urgencia, entidad ni solicitud de datos tal como
  se definen (premios, un aviso de "bloquea aquí", enlaces sueltos). **No se ajustaron las reglas a ellos.**
- **Evasiones que no se cubren:** símbolos que no estén en la tabla de sustituciones, direcciones escritas como "bcp[.]com" o
  "bcp punto com", texto en una imagen, otros idiomas y un enlace sin dominio reconocible.
- **La solicitud de datos exige el verbo y el dato en la misma frase** y a menos de 40 caracteres. Se pierde, por ejemplo,
  "Su clave vence hoy. Actualícela" (el verbo viene después, con pronombre).
- **Los dominios sueltos** se reconocen con una lista de terminaciones (`.com`, `.pe`, `.info`…): una palabra pegada a un punto
  con una terminación de la lista ("hola.me") se tomaría por enlace.
- Un mensaje legítimo con un enlace siempre produce la señal de **enlace**: es solo "hay un enlace". Qué pesa eso lo decide #9.
- **No se midió el tiempo** de las reglas en un teléfono de gama baja.
- Solo se probó en el emulador API 29 x86_64 y en la JVM.
- Las señales **no se muestran** (#11) ni se calculan sobre los mensajes recibidos (#12), y no se combinan con el modelo (#9).
