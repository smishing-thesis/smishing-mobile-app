# Señales presentes en un mensaje

Registro de #8 (RF-04): la app identifica, cuando corresponden, las señales de **urgencia**, **enlace**, **suplantación** y
**solicitud de datos** en un SMS. Última verificación: 2026-11-08.

> **ATENCIÓN.** Estas señales son **reglas escritas a mano**, no salen del modelo. Se midieron con un conjunto de mensajes
> sintéticos y con el corpus real anonimizado (ver "Validación con el corpus real"): **detectan una parte pequeña del
> smishing real** (urgencia o solicitud de datos en 28 % de los mensajes de smishing del corpus), así que explican algunas
> alertas pero no sirven como detector por sí solas. Desde #10 a #12 se calculan sobre los mensajes que llegan y se guardan en el
> historial ([historial.md](historial.md)).

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

1. Las reglas están en `app/src/main/assets/signal_rules.json` (versionado, `1.1.0`), no en el código: cambiar una lista de
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
| `testDebugUnitTest` (JVM) | 107 (36 nuevas; 1 solo corre con el corpus, ver abajo) | 0 |
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

## Validación con el corpus real

Se midieron las reglas con `sms-anonymizer/data/labeled/final.csv`, el corpus real ya anonimizado y etiquetado (versión
1.0.4): **4 057 mensajes legítimos y 53 de smishing, sin repetidos**. El corpus real no se versiona y este informe no lleva
texto: solo conteos. La medición la hace `CorpusSignalReportTest` (JVM), que **no corre por defecto**: solo si la variable de
entorno `SMISHING_CORPUS_CSV` apunta al CSV, y escribe en `app/build/reports` (no versionado) conteos e índices, nunca mensajes.

**Lo que este corpus no permite medir.** La anonimización reemplaza **todos los enlaces por `<URL>`** y los nombres por
`<NAMED_ENTITY>`. Entonces:
- **Suplantación no es medible**: sin el dominio real no se sabe si el enlace es el oficial. Con un enlace de reemplazo, el
  11,5 % de los legítimos "suplantaba" (todo aviso real de Entel, Movistar o Yape): es un artefacto de la prueba, no un
  resultado. Esa cifra no debe citarse.
- **Enlace** se mide solo como presencia, y casi no distingue: lo traen el **73,5 %** de los legítimos y el **98 %** del
  smishing (52 de 53).

**Método.** Para no ajustar las reglas a lo que se mide, el corpus se dividió por índice en **desarrollo** (pares: 2 027
legítimos y 28 de smishing) y **reserva** (impares: 2 030 y 25), y de la reserva solo se tomaron los números, sin leer sus
mensajes. Se leyeron solo los de smishing de desarrollo sin señal y las frases (no los mensajes) que activaban cada regla
en los legítimos.

**Resultado.** Mensajes con urgencia o solicitud de datos, con las reglas `1.1.0`:

| Conjunto | Legítimos | Smishing |
|---|---|---|
| Desarrollo | 44 de 2 027 (2,2 %) | 9 de 28 (32,1 %) |
| Reserva | 31 de 2 030 (1,5 %) | 6 de 25 (24,0 %) |
| **Todo el corpus** | **75 de 4 057 (1,8 %)** | **15 de 53 (28,3 %)** |

Con solo 53 mensajes de smishing la incertidumbre es grande: el 28,3 % tiene un intervalo de confianza del 95 % de
aproximadamente **18 % a 42 %**. Por señal, en todo el corpus (legítimos / smishing): urgencia 63 de 4 057 (1,6 %) / 9 de 53
(17 %); solicitud de datos 12 de 4 057 (0,3 %) / 8 de 53 (15 %).

**Qué muestra.**
- Las falsas alarmas de **urgencia** en los legítimos son sobre todo **avisos de cobro** ("paga hoy y evita el corte", "vence
  hoy"): esos avisos de verdad presionan. La señal describe el mensaje, no su intención, así que **urgencia por sí sola no
  distingue** una estafa de un cobro legítimo: #9 no debe darle un peso alto.
- Los mensajes de smishing sin urgencia ni solicitud de datos son sobre todo un **gancho de dinero o premio** ("se han
  abonado…", "bono", "retirar el monto", "ganadores", "puntos por vencer") seguido de un enlace. **Ninguna de las cuatro
  señales de #8 lo describe**, y es lo más común que quedó sin explicar. Un posible quinto tipo ("beneficio o premio") queda
  como decisión para el equipo; no se agregó porque no está en el criterio de #8 y exige decidir cómo pesa en #9.

**Qué se cambió y qué no.** Se agregó una regla (`urgency.legal_threat`: "demanda judicial", "acciones legales"; versión de las
reglas `1.1.0`): no tuvo ninguna falsa alarma en desarrollo. **Su efecto no se demostró**: sumó 1 mensaje de smishing en
desarrollo (el que la motivó) y 0 en la reserva. Se descartaron "próximo a vencer" (4 legítimos contra 3 de smishing en
desarrollo) y "evitar recargos/corte" (11 contra 1): no distinguían. No se tocó la solicitud de datos, cuyas pocas falsas
alarmas (4 en desarrollo) son instrucciones como "ingrese el siguiente código".

## Lo que no cubre

- **Son reglas hechas por mí y probadas con 84 mensajes sintéticos que ya había visto al escribirlas.** Para evitar falsas
  alarmas quité o acoté reglas mirando esos mismos mensajes legítimos (por ejemplo, "vence pronto" y "cancelada" se
  descartaron por aparecer en avisos reales, y "cuenta" no cuenta como dato). Por eso **el 0 falsas alarmas no es una
  estimación**: con mensajes reales habrá más. La medición honesta necesita mensajes que no se hayan usado para escribir las
  reglas.
- **Con mensajes reales solo se midieron urgencia y solicitud de datos**, y con pocos mensajes de smishing (53). La suplantación y
  los dominios oficiales **siguen sin validarse con datos reales**: el corpus no conserva los enlaces. Tampoco se comparó con lo
  que el modelo detecta.
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
- Las señales se calculan sobre los mensajes recibidos (#10), se muestran en el detalle (#11) y se guardan en el historial (#12); se
  combinan con el modelo en el nivel de riesgo (#9).
