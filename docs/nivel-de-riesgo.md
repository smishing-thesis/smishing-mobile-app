# Nivel de riesgo

Registro de #9 (RF-05): la salida del modelo y las señales de [senales.md](senales.md) se combinan en un nivel **bajo, medio o
alto**, y cada nivel es **trazable**. Última verificación: 2026-11-08.

> **ATENCIÓN.** La política es una **propuesta de diseño**, no un valor validado con datos reales. No se pudo medir con el
> corpus real: el modelo de la app se entrenó con todo ese corpus, así que medirlo ahí sería evaluar datos de entrenamiento.
> Las cifras de abajo son del conjunto controlado de 84 mensajes sintéticos. Y el nivel todavía no se muestra ni se guarda
> (alerta #10, explicación #11 e historial #12 lo usan desde entonces).

## La política (versión `1.0.0`)

**Respaldo** es una señal de urgencia, suplantación o solicitud de datos. **El enlace solo no cuenta**: lo trae el 73,5 % de
los mensajes legítimos del corpus real (ver [senales.md](senales.md)).

En este orden, la primera que se cumple decide:

| # | Regla (`id`) | Cuándo | Nivel |
|---|---|---|---|
| 1 | `risk.model_and_evidence` | el modelo lo marca sospechoso **y** hay al menos un respaldo | **Alto** |
| 2 | `risk.model_only` | el modelo lo marca sospechoso y no hay respaldo | **Medio** |
| 3 | `risk.signals_only` | el modelo no lo marca, pero hay suplantación o dos o más respaldos | **Medio** |
| 4 | `risk.no_evidence` | ninguna de las anteriores | **Bajo** |

Por qué así:
- **El modelo manda, las señales lo respaldan.** El modelo ve el texto completo; las señales explican y corroboran. Que solo
  el modelo lo marque da *medio*, no *alto*: el contrato del modelo avisa que los mensajes cortos o conversacionales pueden
  marcarse como sospechosos sin serlo.
- **Las señales solas llegan a medio, nunca a alto.** Si el modelo no lo marca, un mensaje solo sube a *medio* con
  suplantación (que ya exige un enlace ajeno o pedir datos) o con dos respaldos a la vez. Sin el modelo no hay *alto*.
- **No hay nivel si el modelo no evaluó el mensaje** (menos de 15 caracteres limpios, o análisis suspendido por falta de rol o
  permisos): se respeta la política de entrada del modelo de no alertar. Tampoco se buscan señales.

La política es una función pura (`RiskPolicy.decide`): el mismo puntaje y las mismas señales dan siempre el mismo nivel.

## Qué lo hace trazable

Cada `RiskAssessment` guarda lo necesario para explicar y repetir el nivel (RNF-07):

| Dato | Para qué |
|---|---|
| `rule` (`risk.model_and_evidence`…) | qué regla de la política decidió el nivel |
| `model.score`, `model.threshold`, `model.isSmishing` | lo que dijo el modelo y con qué umbral (el del JSON, no 0.5) |
| `model.modelVersion`, `model.preprocessingVersion` | las versiones del modelo y del preprocesamiento |
| `signals` y `signals.rulesVersion` | las señales (cada una con el id de su regla) y la versión de las reglas |
| `evidence` | cuáles de ellas contaron como respaldo |
| `policyVersion` | la versión de esta política |

Para ello `ClassificationResult.Classified` ahora incluye el `threshold`. El texto del mensaje no se registra: `RiskAssessment`
no muestra los fragmentos de las señales en `toString`.

### Código

| Pieza | Capa | Qué hace |
|---|---|---|
| `RiskLevel`, `RiskRule`, `RiskAssessment`, `RiskResult` | `domain.risk` | los niveles, las reglas con id estable y el resultado trazable |
| `RiskPolicy` | `domain.risk` | la política (función pura) |
| `RiskAssessor` | `domain.risk` | clasifica con el modelo, busca las señales y aplica la política |

## Cómo verificar

Con el emulador de API 29 abierto y `JAVA_HOME` en un JDK de 17 a 23 (ver [entorno.md](entorno.md)):

```powershell
.\gradlew.bat testDebugUnitTest --rerun
.\gradlew.bat connectedDebugAndroidTest --rerun
```

- `RiskPolicyTest` (JVM): los casos de la tabla; que el enlace solo no cuenta; sobre **todas** las combinaciones de modelo y
  señales, que la decisión es determinista, que **agregar una señal o que el modelo marque nunca baja el nivel** y que sin el
  modelo nunca hay *alto*; y que el resultado lleva todo lo de la tabla de trazabilidad.
- `RiskAssessorTest` (JVM): un mensaje evaluado recibe su nivel; si el modelo no lo evaluó, no hay nivel y no se buscan señales.
- `RiskAssessmentInstrumentedTest` (dispositivo): **la prueba funcional**. El modelo real (LiteRT) y las reglas de señales
  reales sobre los 84 mensajes del conjunto controlado. Comprueba que cada nivel es el que dicen el modelo y las señales
  (calculado aparte de la política) y que lleva las versiones, el umbral y la regla. Escribe `risk-level-controlled.json`
  (sin mensajes) en los resultados de la prueba.

## Resultado (2026-11-08)

Emulador Pixel 4, Android 10 (API 29), x86_64, con Temurin 17 y `--rerun`.

| Ejecución | Pruebas | Fallos |
|---|---|---|
| `testDebugUnitTest` (JVM) | 120 (13 nuevas; 1 omitida: la medición con el corpus, que solo corre si se define la variable) | 0 |
| `connectedDebugAndroidTest` | 85 (1 nueva): 84 ejecutadas y 1 omitida a propósito | 0 |

**Las pruebas no son vacías.** Se hizo que "el modelo lo marca" fuera siempre *alto*, sin exigir respaldo, y falló la prueba de la
regla "modelo sin respaldo = medio". Se revirtió.

**Conjunto controlado en el teléfono** (84 mensajes sintéticos, modelo real y reglas de señales reales). 77 tienen nivel y 7 no:
son demasiado cortos para el modelo y no se evalúan.

| Etiqueta | Mensajes con nivel | Bajo | Medio | Alto |
|---|---|---|---|---|
| Legítimo | 47 | 41 | 6 | 0 |
| Smishing | 30 | 1 | 10 | 19 |

Qué regla decidió cada nivel: `risk.no_evidence` 42, `risk.model_and_evidence` 19, `risk.model_only` 10,
`risk.signals_only` 6.

**Qué aporta combinar las señales.** El modelo solo marcó 23 de los 30 mensajes de smishing y 6 de los 47 legítimos.
- Los **6 legítimos que el modelo marcó** (conversacionales o muy genéricos: un "dale, nos vemos", dos mensajes personales, un
  aviso de entrega, una promoción) quedan en **medio y ninguno en alto**, porque ninguno trae un respaldo.
- De los **7 mensajes de smishing que el modelo no marcó**, 6 suben a **medio** por las señales (`risk.signals_only`) y 1
  se queda en **bajo** (`smishing_prize_02`: solo trae un enlace).
- De los 23 que el modelo marcó, 19 llegan a **alto** y 4 se quedan en **medio** (sin respaldo).
- Si se alerta con *medio o alto*: se cubren 29 de 30 mensajes de smishing y se alerta en 6 de 47 legítimos. Si se alerta solo
  con *alto*: 19 de 30 y 0 de 47. Qué nivel dispara qué acción es de #10 y #13.

Léase con las advertencias de abajo: son 84 mensajes inventados, y las reglas de señales se afinaron viendo esos mismos mensajes.

## Lo que no cubre

- **La política no está validada con datos reales.** Es una propuesta. Con el corpus real no se puede medir el nivel porque el
  modelo lo vio al entrenarse; hace falta un conjunto que el modelo no haya visto y con enlaces reales.
- **La política se definió antes de medir y no se ajustó a estos mensajes**, pero las reglas de señales sí se afinaron viendo
  el conjunto controlado (ver [senales.md](senales.md)): las cifras de abajo son optimistas.
- **Los umbrales de la política** (qué cuenta como respaldo; dos respaldos bastan sin el modelo) son una decisión de diseño, no
  salen de los datos. Si el equipo prefiere otra combinación, cambia solo `RiskPolicy`.
- **Medio** mezcla dos casos distintos ("el modelo lo marca sin respaldo" y "las señales solas"); #10 y #11 decidirán si se
  presentan distinto. Hoy `rule` los distingue.
- **Qué hace la app con cada nivel**: desde #10 **solo el nivel alto alerta** ([alerta-de-riesgo.md](alerta-de-riesgo.md)); bloquear o retirar
  es de #13 y siguientes.
- El nivel se calcula sobre los mensajes que llegan, se muestra en el detalle y en el historial y se **guarda** con sus versiones
  (#10 a #12, [historial.md](historial.md)).
- No se midió el tiempo de evaluar un mensaje (modelo más señales) en un teléfono de gama baja.
- Solo se probó en el emulador API 29 x86_64 y en la JVM.
