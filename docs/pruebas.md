# Pruebas del proyecto

Qué pruebas tiene la app y cuál es el criterio. Última revisión: 2026-11-16 (corrida completa, ver más abajo).

## Criterio

Las pruebas automáticas están para proteger **lo crítico del negocio**: lógica que, al cambiar el código, puede romper otros flujos sin que
nadie lo note. No están para comprobar que algo aparece en una pantalla ni que un texto es el esperado: eso se ve a ojo.

Al cambiar algo se corre **solo lo que ese cambio toca**.

## Tipos

| Tipo | Dónde | Qué protege |
|---|---|---|
| **Unitarias en la JVM** | `app/src/test` | la lógica del dominio, sin emulador: nivel de riesgo, preprocesamiento contra los vectores del modelo, señales, el orden "guardar, consultar el modo, actuar, avisar", mitigación (bloquear, retirar, revertir), estado de la protección, resumen semanal, no leídos, compuerta de permisos, reglas de copia de seguridad |
| **De lenguaje** | `ExplanationWordingTest` (JVM) | que ningún texto de la app afirme certeza (la tesis pide no presentar la sospecha como certeza) |
| **En el dispositivo, sin pantalla** | `app/src/androidTest` | lo que depende de Android: el modelo LiteRT contra Keras, la clasificación de punta a punta, Room (historial, acciones, migración) y las preferencias |
| **De punta a punta** | `SmsRoleInstrumentedTest`, `AnalysisSuspensionInstrumentedTest` | el flujo real con SMS entregados al receptor: rol, recepción, alerta, modos, bloqueo y restauración, sin internet, demora, notificaciones y lectura |

## Lo que se quitó (#61)

Se quitaron las **pruebas de pantallas con Compose** (11 archivos, unas 100 pruebas): comprobaban que cada pantalla mostraba ciertos
elementos y textos y que los botones respondían. Cada cambio de texto las rompía sin que hubiera un error real.

Las decisiones que esas pruebas fijaban **ya no tienen una prueba automática de pantalla**, y se vigilan a ojo:
- que Inicio no diga "activa" si falta un requisito (la **lógica** sigue en `ProtectionStatusTest`; la pantalla ya no);
- que el detalle nunca muestre el puntaje ni porcentajes;
- que marcar como seguro pida confirmación;
- que los botones tengan el área táctil de 48 dp y que las marcas del historial no usen "Peligroso" ni "Seguro".

Siguen cubiertas de punta a punta, con el sistema real: la alerta, el detalle desde la notificación, marcar como seguro con su confirmación,
la lista con no leídos y el historial.

## Cómo correr todo y qué dio (#30, #22)

Dos comandos, desde la raíz del repo, con Java 17 (ver [entorno.md](entorno.md)). El segundo necesita el emulador `Pixel_4_API_29` abierto:

```powershell
.\gradlew.bat testDebugUnitTest --rerun
.\gradlew.bat connectedDebugAndroidTest
```

`--rerun` evita que Gradle dé por buenos resultados guardados. El resultado se lee de los XML (`app/build/test-results/testDebugUnitTest` y
`app/build/outputs/androidTest-results/connected/debug`); un resumen sin textos de mensajes está en
[reportes/pruebas-2026-11-16.json](reportes/pruebas-2026-11-16.json).

**Corrida del 2026-11-16, rama `30-22-pruebas` sobre `main` (`d73f1b4`), emulador Pixel 4 API 29 (x86_64):**

| Suite | Resultado |
|---|---|
| JVM (`testDebugUnitTest`) | **259 pruebas en 34 clases, 0 fallos, 1 omitida** (`CorpusSignalReportTest`, que solo corre con el corpus real; es por diseño) |
| Dispositivo (`connectedDebugAndroidTest`), primera corrida | 90 pruebas, **2 fallos**, 3 omitidas |
| Dispositivo, tras el arreglo | **90 pruebas, 0 fallos, 2 omitidas**; `BUILD SUCCESSFUL` en 1 min 51 s |

**Los 2 fallos eran un defecto real, no de la prueba.** `thenAMessageIsSentToTheChosenRecipientAndAppearsInItsConversation` y
`thenTheConversationsShowReceivedAndSentMessages` no encontraban la burbuja con el estado «Enviado». Desde #51 la burbuja tiene un manejador
de toque, y eso la separaba del nodo de afuera que llevaba «Enviado/Recibido» para el lector de pantalla (**TalkBack ya no lo habría dicho**).
Se arregló poniendo el estado en el mismo nodo que el texto y el toque (`MessageBubble.stateText`). No se había visto antes porque al cambiar
#51 solo se corrieron las pruebas que ese cambio tocaba: es el costo de esa regla, y por eso conviene correr todo de vez en cuando.

**Qué se omitió y por qué.** Las pruebas de dispositivo que dependen del estado del emulador se omiten con `assumeTrue` si no se cumple su
condición; **cuáles se omiten cambia entre corridas** porque cada una deja el emulador en otro estado:

- `roleThenNotificationsFollowTheAndroid13Permission`: solo Android 13 o superior, así que **en esta corrida (API 29) se omitió**. Sí se
  corrió antes, el 2026-11-10, en un emulador API 35 (Android 15) y en dos fases (hay que revocar el permiso antes), y pasó tres veces
  seguidas: ver [avisos-y-no-leidos.md](avisos-y-no-leidos.md). **No se repitió con el código de hoy.**
- `analysisResumesWhenThePermissionsAreGrantedAgain` (en esta corrida; en la primera se omitió `analysisIsSuspendedWhileAnSmsPermissionIsMissing`
  y esta pasó): piden un estado inicial distinto (con todos los permisos o con alguno quitado). **Cada una pasó en una corrida, pero no
  corrieron las dos en la misma.**
- `roleFirstThenPermissionsThenAnalysis` (omitida en la primera corrida, pasó en la segunda): pide que el rol aún no esté concedido.
- `thenAMessageIsSentTo…` solo corre en el emulador 5554 (el que entrega a sí mismo lo enviado a su número).

### Qué cubre cada área (criterios de #30)

| Área | JVM | Dispositivo |
|---|---|---|
| Permisos y rol | `AnalysisGateTest` (6), `SmsRoleStateTest` (4), `ProtectionStatusTest` (6) | `AnalysisSuspensionInstrumentedTest` (2), `SmsRoleInstrumentedTest` (rol primero, luego permisos y análisis) |
| Preferencias | — | `DataStoreMitigationSettingsInstrumentedTest` (7), `DataStoreContributionConsentInstrumentedTest` (4) |
| Clasificación local | `LocalMessageClassifierTest` (8), `GatedMessageClassifierTest` (3), `PreprocessingVectorsTest` (4), `SignalDetectorTest` (27), `RiskPolicyTest` (11), `RiskAssessorTest` (2) | `LiteRtParityInstrumentedTest` (2, contra Keras), `OnDeviceClassifierInstrumentedTest` (2), `PreprocessingVectorsInstrumentedTest` (2), `SignalDetectorInstrumentedTest` (28), `RiskAssessmentInstrumentedTest` (1) |
| Historial | `MessageRiskTest` (6), `HistoryMarksTest` (10), `WeeklySummaryTest` (6), `BackupRulesTest` (5) | `RoomAnalysisHistoryInstrumentedTest` (12), `HistoryMigrationTest` (3), `RoomMitigationLogInstrumentedTest` (6) |
| Acciones de mitigación | `MitigationExecutorTest` (24), `MitigationPolicyTest` (7), `AnalyzeReceivedMessageTest` (23) | `SmsRoleInstrumentedTest` (automático y preguntar) |

- **Casos normales, permisos denegados y fallos de acción:** los fallos de acción están en `MitigationExecutorTest` (bloqueo no disponible, rechazado
  por el sistema, sin copia local, reintento, qué no se puede revertir); el permiso quitado, en `AnalysisGateTest` y `AnalysisSuspension…`.
- **Huecos que esta corrida muestra:** no hay una prueba automática de `AndroidPermissionStatusReader` (el estado de cada permiso que ve
  Ajustes), ni del permiso de contactos (#53) ni del interruptor de Ajustes, ni de las pantallas (se quitaron en #61, ver arriba).

### Qué cubre cada punto del criterio de #22

| Punto | Pruebas de punta a punta (`SmsRoleInstrumentedTest`, salvo indicación) |
|---|---|
| Rol | `theSystemOffersTheRoleRequest`, `roleFirstThenPermissionsThenAnalysis` |
| Permisos | `AnalysisSuspensionInstrumentedTest` (2); `roleThenNotificationsFollowTheAndroid13Permission` se omitió en esta corrida (API 29); su última corrida fue en API 35 el 2026-11-10 |
| Recepción | `thenReceivedSmsLandsOnceInItsConversation`, `thenWithoutAnyConnectionAMessageIsReceivedClassifiedAndAlerted` |
| Envío | `thenAMessageIsSentToTheChosenRecipientAndAppearsInItsConversation`, `thenAShortMessageIsAlsoNotifiedAndASentOneIsNeverUnread` |
| Alertas | `thenARiskyMessageAlertsAndTheAlertOpensItsDetailWithoutActivatingLinks`, `thenEachMessageIsNotifiedAndOpeningItsConversationReadsItAndClearsItsNotification` |
| Historial | `thenTheHistoryKeepsEachAnalysisLocallyAndItSurvivesDeletingTheMessage` |
| Modos | `thenTheChosenModeDecidesWhatHappensToARiskyMessage`, `thenAskModeActsOnlyWhenThePersonChoosesAndTheOnlyNotifyModeNeverActs` |
| Acciones reversibles | `thenTheAutomaticModeBlocksAndRemovesAndMarkingSafeRestoresBoth` |

### Lo que esta corrida no demuestra

- **Esta corrida fue solo en un emulador API 29 (Android 10, el mínimo declarado) x86_64.** No incluye Android 13 o posterior. El permiso de
  notificaciones de Android 13+ se verificó el 2026-11-10 en un emulador API 35. **El envío de SMS se probó en API 35 el 2026-11-16**: la app
  envía bien (burbuja «Enviado», una sola fila saliente no fallida, conversación), pero **la copia que el emulador debía devolverse a sí
  mismo no llegó en 10 s**, y no se determinó por qué (se atribuye al emulador, sin comprobarlo). Ese último paso quedó solo para API < 33.
  Tampoco hay teléfono real, ARM ni un teléfono de gama baja.
- Las pruebas de punta a punta usan SMS sintéticos entregados al receptor, no mensajes de la red.
- No se midió la cobertura de código: «cubre» significa que hay pruebas de esa área, no un porcentaje.
- Las pruebas de dispositivo que dependen del estado del emulador pueden omitirse; hay que leer la lista de omitidas, no solo «0 fallos».
