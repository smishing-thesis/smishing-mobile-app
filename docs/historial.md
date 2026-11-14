# Historial de análisis

Registro de #12 (RF-11): cada mensaje analizado deja un registro **local** con su resultado, su nivel, su explicación y la versión
del modelo, y se puede consultar después. Última verificación: 2026-11-09.

> **ATENCIÓN.** El historial **guarda el texto de los mensajes** en una base de datos del teléfono, **sin cifrar**, y **no tiene
> forma de borrarlo** todavía: borrar un mensaje de la app no borra su registro. Hace falta decidir una política de borrado y
> retención antes de usar la app con mensajes reales (ver "Lo que no cubre"). Úsala solo en un emulador.

## Qué se guarda

Una base **Room sobre SQLite** (`history.db`), como define la arquitectura. Tres tablas (el esquema exportado está en
`app/schemas/`):

| Tabla | Qué guarda |
|---|---|
| `analyzed_message` | el mensaje: remitente, **texto**, fecha de recepción y su id en el buzón del sistema |
| `analysis_result` (1:1) | el **resultado**: puntaje, umbral y decisión del modelo; el **nivel** de riesgo y la regla de la política que lo decidió; las **versiones** del modelo, del preprocesamiento, de las reglas de señales y de la política; los respaldos que contaron; la fecha del análisis |
| `detected_signal` (1:N) | la **explicación**: cada señal con su tipo, la regla que la detectó y la parte del mensaje que la motivó |

Desde la **versión 2** de la base (#28) hay además la tabla `mitigation_action` (las acciones tomadas, ver [mitigacion.md](mitigacion.md)) y
la columna `markedSafeAtMillis` del mensaje; la migración conserva lo guardado en la versión 1.

Borrar un mensaje borra en cascada su resultado, sus señales y sus acciones. El id del mensaje en el buzón **no** es una clave foránea: el registro
sigue existiendo aunque el mensaje se borre del buzón.

**La explicación se guarda como datos, no como texto.** Lo que se lee en el detalle ("Le mete prisa para que actúe sin pensar", el
resumen, la recomendación) se genera al mostrarlo a partir de las señales guardadas. Así cambiar la redacción (#11) no altera lo
guardado, y lo guardado sigue siendo la evidencia de por qué se dio ese nivel.

**Qué entra.** Todo mensaje que el modelo evalúa, sea cual sea su nivel (el diseño muestra también los "seguros"). No entran los
demasiado cortos ni los que llegan con el análisis suspendido: no tienen resultado. Se guarda **antes** de avisar, así que el
detalle de la alerta encuentra el registro; si guardar falla, igual se avisa y solo se anota el tipo de error.

## Privacidad

- La base está en el almacenamiento **privado** de la app.
- **Se excluye de las copias de seguridad.** Hasta #12 las reglas de respaldo estaban vacías y la app permite respaldo
  (`allowBackup`): con la base, los textos de los mensajes se habrían subido a la nube de Google y copiado a otro teléfono al
  migrar. `data_extraction_rules.xml` (Android 12 o superior: nube y migración) y `backup_rules.xml` (Android 11 o inferior)
  excluyen todo el dominio de bases de datos, y `BackupRulesTest` lo fija.
- **No está cifrada.** Quien tenga acceso al teléfono con permisos de administrador, o a la memoria interna con el teléfono
  comprometido, puede leerla.
- No se registran textos ni remitentes en logs: `AnalysisRecord` no los muestra en `toString`.

## Qué se ve

- Una pestaña **Historial** junto a **Mensajes**, en una barra inferior. El diseño tiene cuatro pestañas (Inicio, Mensajes, Historial,
  Ajustes); aquí solo están las dos que existen. Inicio es #17 y Ajustes, #31.
- La lista, del más reciente al más antiguo: remitente, fecha ("28 sep 2026"), inicio del mensaje y una marca de estado. La marca
  dice **"Posible estafa"**, **"Sospechoso"** o **"Sin señales"**, no "Peligroso" y "Seguro" como el mockup, para no presentar la
  sospecha como certeza (#11).
- Tocar un registro abre el **detalle**, armado **desde lo guardado** (no se vuelve a evaluar el mensaje). El detalle que abre una
  alerta (#10) también usa lo guardado. Solo si ese mensaje no tiene registro (es anterior al historial) se evalúa de nuevo.
- Un registro solo se usa para un mensaje si el texto coincide: si el almacenamiento de SMS del sistema se borra, sus ids empiezan
  de nuevo y un mensaje nuevo podría tener el id de un registro viejo.
- La lista se actualiza sola cuando llega un análisis nuevo (la base avisa de sus cambios).

## Código

| Pieza | Capa | Qué hace |
|---|---|---|
| `AnalysisRecord`, `AnalysisHistory`, `formatHistoryDate` | `domain.history` | el registro, el contrato del historial y la fecha del diseño |
| `AnalyzeReceivedMessage`, `AnalysisOutcome` | `domain.alerts` | evalúa, guarda y avisa (antes se llamaba `AlertOnRiskyMessage`) |
| `HistoryDatabase`, `HistoryDao`, entidades, `RoomAnalysisHistory` | `data.history` | la base Room, el guardado en una transacción y la conversión desde y hacia la evaluación |
| `HistoryScreen`, `HistoryRow` | `ui.history`, `ui.designsystem` | la pantalla y su fila |
| `MessagesRoute` | `ui.messages` | la barra inferior, la pestaña y el detalle desde lo guardado |

Se agregaron Room 2.8.5 y KSP 2.3.13 (`libs.versions.toml`).

## Cómo verificar

Con el emulador de API 29 abierto y `JAVA_HOME` en un JDK de 17 a 23 (ver [entorno.md](entorno.md)):

```powershell
.\gradlew.bat testDebugUnitTest --rerun
.\gradlew.bat connectedDebugAndroidTest --rerun
```

- `AnalyzeReceivedMessageTest` (JVM, 10): todo mensaje evaluado se guarda, alerte o no; el registro conserva mensaje, resultado y
  explicación; se guarda **antes** de avisar; los no evaluados no se guardan; si guardar falla igual se avisa.
- `BackupRulesTest` (JVM, 5): la base está excluida de la nube y de la migración en Android 12 o superior y de la copia en Android 11 o
  inferior, y ninguna regla la vuelve a incluir.
- `HistoryDateTest` (JVM, 3): la fecha del diseño y la zona horaria.
- `RoomAnalysisHistoryInstrumentedTest` (dispositivo, 10): con una base real, un registro vuelve **idéntico** a como se guardó
  (resultado, nivel, regla, puntaje, umbral, las cuatro versiones, señales con su fragmento y respaldos); las versiones están en la
  tabla misma; los tres niveles; orden; búsqueda por id del buzón; el borrado en cascada; **lo guardado sobrevive a cerrar y volver a
  abrir la base**; el guardado es todo o nada; y el aviso de cambios.
- `HistoryScreenTest` (quitada: ver [pruebas.md](pruebas.md)) (dispositivo, 5): remitente, fecha, inicio y marca de cada registro; las marcas no usan "Peligroso" ni "Seguro";
  tocar abre; el vacío y la carga.
- `SmsRoleInstrumentedTest.thenTheHistoryKeepsEachAnalysisLocallyAndItSurvivesDeletingTheMessage` (dispositivo): **la prueba
  funcional**, con el modelo real. Entran por el receptor un SMS riesgoso, uno que el modelo marca como medio y uno demasiado corto.
  Comprueba que quedan los dos primeros y no el corto; que el riesgoso conserva nivel alto, la regla, las versiones del modelo
  `1.0.0`, del preprocesamiento `1.1.0`, de las reglas `1.1.0` y de la política `1.0.0`, y las cuatro señales con su fragmento; que la
  pestaña Historial los lista con su marca; que el detalle sale de lo guardado; y que, **borrado el mensaje del buzón, el registro sigue y
  sigue abriéndose**.

## Resultado (2026-11-09)

Emulador Pixel 4, Android 10 (API 29), x86_64, con Temurin 17 y `--rerun`.

| Ejecución | Pruebas | Fallos |
|---|---|---|
| `testDebugUnitTest` (JVM) | 147 (12 nuevas; 1 omitida: la medición con el corpus) | 0 |
| `connectedDebugAndroidTest` | 116 (16 nuevas): 115 ejecutadas y 1 omitida a propósito | 0 |

**Las pruebas no son vacías.** Se hicieron dos mutaciones a la vez: no guardar el registro en el caso de uso, y quitar de las reglas de
respaldo la exclusión de la base en la nube. Fallaron 5 pruebas del guardado y la de la nube. Se revirtieron.

**La compilación.** Room 2.8.5 y KSP 2.3.13 compilaron a la primera sobre Kotlin 2.4.21 y AGP 8.13.2 y exportaron el esquema
(`app/schemas/.../1.json`).

**Revisión manual con el sistema** (dos SMS sintéticos entraron con `adb emu sms send`): el registro del receptor dice "Alerted" para el
riesgoso y "Recorded" para el que no alerta; `history.db` aparece en el almacenamiento privado de la app (`databases/`, con su
archivo de registro); la pestaña **Historial** lista los dos mensajes con remitente, fecha ("9 nov 2026"), inicio del mensaje y la
marca "Posible estafa" y "Sospechoso", como el diseño.

**Un fallo que apareció en el camino.** Una prueba vieja (`roleFirstThenPermissionsThenAnalysis`) buscaba un solo nodo con el texto
"Mensajes"; con la barra inferior hay dos (el título y la pestaña). Era una fragilidad de la prueba y se corrigió.

**Un riesgo que se cubrió antes de que ocurriera.** Si el almacenamiento de SMS del sistema se borra, sus ids empiezan de nuevo y un
mensaje nuevo podría coincidir con el id de un registro viejo y mostrar un análisis ajeno. El detalle de una alerta solo usa el
registro si el texto del mensaje coincide.

## Mensajes que ya estaban en el buzón (#59)

El historial solo muestra lo analizado: no lista los mensajes sin analizar (con cientos de miles en el buzón sería inmanejable, y
habría que leerlos todos). En su lugar, **dentro de la conversación**, cada mensaje recibido sin análisis dice "No analizado" con un
botón "Analizar". Al tocarlo se analiza ese mensaje y se guarda en el historial como cualquier otro (nivel, explicación y versiones),
con la hora de recepción del mensaje; si es de riesgo alto o medio, la conversación y la lista de mensajes lo marcan (#60).

La fila responde a lo que pasó: "Analizando…"; si el mensaje no tiene riesgo, un aviso verde "Sin señales de riesgo" con "Ver análisis", que **queda siempre**
en la conversación para todo mensaje recibido analizado sin riesgo (así se distingue de uno que no se analizó; uno que la persona marcó
como seguro no lo lleva, porque sí tuvo riesgo); si falló (por ejemplo, el análisis está suspendido), "No se pudo
analizar. Inténtelo de nuevo." con "Analizar" otra vez. El fallo no se probó en el emulador: no se pudo provocar.

Analizar uno antiguo **no avisa ni actúa**: no hay alerta, ni bloqueo, ni se retira nada. Un mensaje cuenta como analizado solo si hay un
registro **de ese** mensaje (mismo id y mismo texto). Los muy cortos no tienen botón (el modelo no los puntúa). Hoy se analiza de a uno;
analizar toda una conversación o toda la bandeja de una vez no está hecho.

## Retención, borrado y cifrado (#52)

**Decisiones (2026-11-14, del equipo):**
- **Sin retención automática.** Los análisis se guardan hasta que la persona los borre; no se borran solos. Se dice en lenguaje claro en
  Ajustes → Privacidad, en una frase: "El historial guarda el texto de los mensajes en este teléfono hasta que usted lo borre." Es una política explícita, no una falta de política. Consecuencia: el historial crece sin límite y guarda texto de
  mensajes mientras la persona no lo borre; cada registro es chico, pero **no se midió el tamaño** con muchos mensajes.
- **No se cifra la base, y queda documentado.** Razones: la base está en el almacenamiento privado de la app, y la copia de seguridad de
  Android y la migración a otro teléfono **la excluyen** (`backup_rules.xml` y `data_extraction_rules.xml`, desde #12); Android cifra
  los datos del teléfono (no se verificó en un teléfono real, y los teléfonos actualizados de versiones viejas pueden no tenerlo); y el
  borrado deja que la persona controle cuánto texto se guarda. Cifrar con SQLCipher sumaría una dependencia nativa (más peso en el APK, que
  **no se midió**), costo de rendimiento en un teléfono de gama baja y la gestión de una clave con el Keystore. **Lo que queda sin
  proteger:** un teléfono con root, o alguien con acceso al teléfono desbloqueado, podría leer la base. Revisar la decisión si el
  historial pasa a guardar algo más que el texto, o si la tesis exige protección ante ese caso.

**Borrar.** En el Historial, el botón **"Seleccionar"** de la barra (o **mantener presionado un registro**, como atajo) empieza a seleccionar; la
barra dice "N seleccionados", con casillas en cada fila, "Todos" y "Borrar"; tocar otras filas las marca o desmarca, y la flecha cancela. "Borrar" pide confirmar: dice que se borran el
análisis y el texto que guarda el historial, que los mensajes siguen en el teléfono y, si alguno de los seleccionados es un mensaje que la
app **retiró del buzón y todavía se puede restaurar** (#16), que ya no podrá restaurarlo (`restorableCount`). En Ajustes → Privacidad,
**"Borrar todo el historial"** hace lo mismo con todos, con su confirmación. Con cada registro se van su resultado, sus señales y sus
acciones (borrado en cascada): una prueba de dispositivo comprueba que, tras borrar todo, las cuatro tablas quedan vacías.

**Lo que no cubre esto:** borrar el historial no borra los mensajes del buzón (eso es #51), no desbloquea números que el modo automático
haya bloqueado, y no se probó con miles de registros (se borra en tandas de 400 ids) ni en un teléfono real.

**Nombres de contactos (#53):** el Historial muestra el nombre del contacto si hay permiso, pero el registro guarda **solo el número**: el
nombre se resuelve al mostrar y no se guarda.

## Lo que no cubre

- **El historial crece sin límite**: no hay retención automática (decisión de #52, ver arriba). Desde #51, al borrar un mensaje o una
  conversación la confirmación deja borrar también su registro (casilla marcada por defecto); si se desmarca, el texto sigue guardado.
- **La base no está cifrada** (decisión de #52, ver arriba).
- **La historia habla de "las acciones tomadas"**, pero las acciones (bloquear, retirar, marcar como seguro) son #13 a #16: el esquema
  todavía no tiene su tabla (llegará con una migración a la versión 2 de la base) y la pantalla no las muestra.
- **Solo entran los mensajes analizados**: los que llegan con la app como predeterminada y los que la persona analiza a mano (ver
  abajo). Los que ya estaban en el buzón no se analizan solos, y los MMS están fuera de alcance.
- **No tiene búsqueda** (el diseño la tiene) ni filtros.
- **No se midió** el tamaño de la base con muchos mensajes ni el tiempo de guardar o de listar en un teléfono de gama baja, ni cuánto
  pesan Room y KSP en el APK.
- La fecha que se muestra es la del análisis, que es la de recepción más una fracción de segundo.
- No se probó con TalkBack ni con letra grande. Solo se probó en el emulador API 29 x86_64 y en la JVM.
