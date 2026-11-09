# Explicación sencilla de la alerta

Registro de #11 (RF-06, RNF-04): el detalle de la alerta (#10) está redactado en lenguaje claro, **nombra las señales detectadas**
y **no presenta la probabilidad como certeza**. Última verificación: 2026-11-09.

> **ATENCIÓN.** Lo que se verificó es **mecánico**: que los textos no usen palabras de certeza ni jerga técnica, que cada señal
> detectada aparezca nombrada y que nunca se muestre el puntaje. **No se verificó que la gente entienda el texto**: eso lo evalúa
> #23, con personas. Tampoco se contrastó con un criterio medible de RNF-04, cuyo texto no está en el repositorio.

## Qué cambió

La pantalla y la notificación de #10 usaban las frases del mockup. Varias afirmaban más de lo que la app sabe: "Finge ser de una
entidad" atribuye una intención, y "¿Por qué es riesgoso?" da por hecho el riesgo. Ahora:

| Dónde | Antes (#10) | Ahora |
|---|---|---|
| Notificación, motivo | "Le pide actuar con urgencia" | "Le mete prisa para que actúe" |
| Notificación, motivo | "Finge ser de una entidad" | "Dice ser de una entidad conocida" |
| Notificación, motivo | "Le pide sus datos o claves" | "Le pide datos personales o claves" |
| Detalle, encabezado de la lista | "¿Por qué es riesgoso?" | "Qué encontramos" |
| Detalle, urgencia | "Le pide actuar con urgencia" | "Le mete prisa para que actúe sin pensar" |
| Detalle, suplantación | "Finge ser de una entidad" | "Nombra a una entidad conocida, pero el enlace no es el suyo o le pide datos" |
| Detalle, enlace | "Contiene un enlace" | "Trae un enlace: no lo abra si no está seguro" |
| Detalle, datos | "Le pide sus datos o claves" | "Le pide datos personales o claves" |
| Detalle, recomendación | "No abra el enlace. Si tiene dudas, contacte directamente a la entidad." | "No abra enlaces ni responda con sus datos. Si tiene dudas, llame a la entidad a un número que ya conozca." |

Y se agregó, en el detalle:
- **Un resumen** bajo el nivel que dice lo que se sabe y lo que no. Alto: "Este mensaje tiene señales de las estafas por SMS. No
  podemos estar seguros de que lo sea, pero conviene tratarlo con mucho cuidado." Medio: "Algo en este mensaje nos hace dudar,
  pero no estamos seguros…". Bajo: "No encontramos señales de estafa… Eso no garantiza que sea de fiar".
- **Para cada señal, la parte del mensaje que la motivó**, entre comillas ("En el mensaje: «sera suspendida»", «BBVA», el enlace,
  «Actualice sus datos»). Así la persona ve de dónde sale el aviso. Es texto plano, como el resto del mensaje.
- **Un aviso**: "Este análisis es automático y puede equivocarse."
- **Cuando el nivel es medio por el análisis y no hay nada concreto**, lo dice así en lugar de inventar una señal.

## Reglas de redacción

1. **Describir lo que dice el mensaje, no afirmar lo que es.** "Le mete prisa", no "es una estafa"; "nombra a una entidad", no
   "finge ser".
2. **Decir lo que se sabe y lo que no.** El nivel alto es "Posible estafa", no "Peligroso"; el nivel bajo no promete que sea de fiar.
3. **Nunca mostrar el puntaje del modelo, ni porcentajes.**
4. **Palabras corrientes**: sin "modelo", "umbral", "puntaje", "probabilidad", "algoritmo", "regla" ni parecidos.
5. **Oraciones de hasta 25 palabras.**

Esas reglas están en `PlainLanguage` (`app/src/sharedTest`). Son mínimas y mecánicas: detectan lo que prohíben, no garantizan que el
texto sea claro.

## Cómo verificar

Con el emulador de API 29 abierto y `JAVA_HOME` en un JDK de 17 a 23 (ver [entorno.md](entorno.md)):

```powershell
.\gradlew.bat testDebugUnitTest --rerun
.\gradlew.bat connectedDebugAndroidTest --rerun
```

- `ExplanationWordingTest` (JVM, 7): lee `strings.xml` tal cual está y comprueba que **todas** las cadenas del detalle y de la alerta
  cumplen las reglas, que los textos que admiten duda lo hacen ("Posible", "No podemos estar seguros", "no garantiza", "puede
  equivocarse") y que las razones no afirman una intención. También comprueba que las reglas **sí detectan** los textos malos (el
  mockup "Mensaje Peligroso", un porcentaje, "el modelo…", "es seguro"), para que el control no sea vacío.
- `MessageDetailScreenTest` (dispositivo, 14): cada señal detectada aparece nombrada con la parte del mensaje que la motivó, y solo
  las que se detectaron; el resumen de cada nivel; el nivel bajo no promete que sea seguro; y, para tres evaluaciones con puntajes
  0,97, 0,94 y 0,04, que **ningún texto en pantalla** muestra un puntaje ni incumple las reglas.
- `SmsRoleInstrumentedTest.thenARiskyMessageAlertsAndTheAlertOpensItsDetailWithoutActivatingLinks` (dispositivo): **la prueba
  funcional**. Un SMS riesgoso real entra por el receptor, aparece la notificación y al tocarla el detalle **nombra las cuatro
  señales que se detectaron, cada una con su fragmento**, muestra el resumen y el aviso, y **todos los textos que hay en pantalla**
  (menos el mensaje original) cumplen las reglas de lenguaje.

## Resultado (2026-11-09)

Emulador Pixel 4, Android 10 (API 29), x86_64, con Temurin 17 y `--rerun`.

| Ejecución | Pruebas | Fallos |
|---|---|---|
| `testDebugUnitTest` (JVM) | 135 (7 nuevas; 1 omitida: la medición con el corpus) | 0 |
| `connectedDebugAndroidTest` | 100 (6 nuevas): 99 ejecutadas y 1 omitida a propósito | 0 |

**El control no es vacío.** Se puso a propósito el texto del mockup, "Mensaje Peligroso 97 %", como título del nivel alto y fallaron 2
pruebas: la de las reglas de lenguaje y la de "los textos admiten duda". Se revirtió.

**Revisión manual con el sistema** (el SMS entró con `adb emu sms send`): la notificación dice "Posible estafa detectada — … — Le mete
prisa para que actúe"; al tocar "Ver detalles", el detalle muestra "Posible estafa", el resumen que admite la duda, las cuatro señales con
su cita del mensaje («sera suspendida», «BBVA», el enlace, «Actualice sus datos»), "Este análisis es automático y puede equivocarse" y
la recomendación.

**Lo que enseñó el proceso.** Al cambiar los textos, la prueba de las cadenas pasó a la primera con los textos nuevos; para
comprobar que no era vacía hubo que romperla a propósito. Y al escribirla se vio que la frase del mockup "Finge ser…" no pasaba las
reglas: era la que más afirmaba.

## Lo que no cubre

- **No se probó la comprensión.** Que un adulto mayor entienda "Le mete prisa para que actúe sin pensar" o el resumen no se sabe: es
  lo que evaluará #23. Las reglas mecánicas solo evitan errores de fondo.
- **RNF-04 no se contrastó con un criterio medible**: su texto no está en el repositorio.
- Las reglas de lenguaje son una lista corta y solo en español: no detectan toda afirmación de certeza que alguien pueda escribir
  mañana.
- La notificación sigue llamándose **"Posible estafa detectada"** (el texto del diseño): "detectada" afirma algo, aunque "Posible" lo
  modera. Si se quiere más prudencia, sería "Posible estafa" a secas; es decisión del equipo.
- El texto que se le muestra a la persona entre comillas es **un trozo del mensaje**: si es largo, ocupa varias líneas.
- **No se probó con TalkBack ni con letra grande.** Hoy cada fila lee el texto y su cita en orden, pero no se escuchó.
- Los textos nuevos no están en el diseño de Figma y deben revisarse con quien lo diseñó.
- Solo se probó en el emulador API 29 x86_64 y en la JVM.
