# Permisos de SMS y suspensión del análisis

Registro de #3 (RF-02, RF-15, RES-05): la app pide solo los permisos de SMS que necesita, después del rol, y suspende el
análisis si se revocan. Última verificación: 2026-11-08.

## Permisos que pide la app

| Permiso | Para qué | Cuándo se pide | Evidencia de que hace falta |
|---|---|---|---|
| `READ_SMS` | leer los mensajes: es el "permiso de lectura" del paso 3 y lo necesitan las conversaciones (#5) | después del rol | lo pide el diseño; la lectura del buzón es de #5, ya hecha ([conversaciones.md](conversaciones.md)) |
| `SEND_SMS` | enviar los SMS que la persona redacta (#6) | el rol lo concede; si falta, la app lo pide al enviar | **comprobado en API 29** con el rol concedido: Android lo otorga solo (`GRANTED_BY_ROLE`) y el envío funciona; ver [enviar-sms.md](enviar-sms.md). **No** forma parte de la compuerta del análisis: analizar no necesita enviar |
| `RECEIVE_SMS` | recibir los SMS entrantes | después del rol | **comprobado en API 29**: el sistema declara `requiredPermissions=[RECEIVE_SMS]` en la difusión `SMS_DELIVER`. Con el permiso quitado, el receptor aparece como `Skipped`; al devolverlo, se entrega de nuevo |

No se piden otros. Tampoco se pide `READ_CONTACTS` (los nombres de contacto no están justificados por ninguna historia
hecha), ni `RECEIVE_MMS` ni `RECEIVE_WAP_PUSH`: no hay historia que reciba MMS. Cada permiso nuevo debe llegar con su
motivo en esta tabla.

## Cómo los otorga Android

Se comprobó en el emulador (API 29) con `dumpsys package`:

- Los dos son permisos **restringidos** (`restricted=true`). Una app que no es el gestor de SMS predeterminado no puede
  tenerlos cuando se instala desde una tienda; en las instalaciones de desarrollo con `adb` o Android Studio sí, por la
  marca `RESTRICTION_INSTALLER_EXEMPT`.
- Con el rol de SMS, el sistema los concede solo (`GRANTED_BY_ROLE`). Por eso la app **no muestra un diálogo de
  permisos** a quien ya aceptó el rol: al tocar "Permitir", Android los concede en el acto.
- La persona puede quitar **un solo** permiso aunque conserve el rol (ese es el caso que suspende el análisis).
- Al quitar el rol, el sistema retira también los permisos que dio.

Por eso el orden es siempre rol primero y permisos después: pedir los permisos antes no tendría efecto en una
instalación desde tienda y el rol ya los trae.

## Qué hace la app

- **Paso 3 del onboarding** ("Permiso de lectura"), con los componentes de [design-system.md](design-system.md). Solo
  aparece cuando la app ya tiene el rol. Con todos los permisos concedidos muestra la confirmación.
- **Si Android ya no muestra la solicitud** (porque se rechazó dos veces), el botón pasa a "Abrir ajustes" y lleva a los
  ajustes de la app. Esta rama se probó con la pantalla, pero no de principio a fin: ver "Lo que no cubre".
- **Compuerta del análisis** (`AnalysisGate`): el análisis solo está activo con el rol y todos los permisos. Si falta algo
  está suspendido, con el motivo (`RoleNotHeld`, `PermissionsNotGranted`). Consulta el estado actual cada vez.
- **`GatedMessageClassifier`**: envuelve al clasificador. Con el análisis suspendido no llama al modelo y devuelve
  `NotClassified(ANALYSIS_SUSPENDED)`. Quien reciba los SMS (#4) debe usar este clasificador y no el directo.
- `MainActivity` vuelve a comprobar el rol y los permisos cada vez que la app vuelve a primer plano.

| Pieza | Capa |
|---|---|
| `SmsPermissionState`, `SmsPermissionRepository` | `domain.sms` |
| `AnalysisStatus`, `SuspensionReason`, `analysisStatus`, `AnalysisGate`, `RoleAndPermissionsGate` | `domain.sms` |
| `GatedMessageClassifier` | `domain.classification` |
| `AndroidSmsPermissionRepository` | `data.sms` |
| `SmsPermissionScreen` | `ui.onboarding` |

## Cómo verificar

Con el emulador de API 29 abierto y `JAVA_HOME` en un JDK de 17 a 23 (ver [entorno.md](entorno.md)):

```powershell
.\gradlew.bat testDebugUnitTest --rerun
.\gradlew.bat connectedDebugAndroidTest --rerun
```

- `AnalysisGateTest` y `GatedMessageClassifierTest` (JVM): la regla, y que con el análisis suspendido no se llama al
  modelo, se suspende y se reanuda al cambiar los permisos.
- `SmsPermissionScreenTest` (dispositivo): los estados de la pantalla, los botones y el área táctil de 48 dp.
- `SmsRoleInstrumentedTest.roleFirstThenPermissionsThenAnalysis` (dispositivo): sin rol el análisis está suspendido y
  la app pide primero el rol; al conceder el rol, Android concede los permisos, el análisis se activa y el clasificador
  analiza un mensaje.

### La revocación, en dos fases

**Android mata el proceso de la app cuando le quitan un permiso, y quitar el rol quita los permisos que da.** Las pruebas
instrumentadas corren dentro de ese proceso, así que ninguna puede quitar un permiso: se comprobó que la ejecución
termina con `Instrumentation run failed due to Process crashed`. La revocación se prueba quitando el permiso **desde
fuera**, entre dos ejecuciones:

```powershell
.\gradlew.bat installDebug installDebugAndroidTest
adb shell am instrument -w -e class com.app.smishing.data.sms.SmsRoleInstrumentedTest#roleFirstThenPermissionsThenAnalysis com.app.smishing.test/androidx.test.runner.AndroidJUnitRunner
adb shell pm revoke com.app.smishing android.permission.READ_SMS
adb shell am instrument -w -r -e class com.app.smishing.data.sms.AnalysisSuspensionInstrumentedTest com.app.smishing.test/androidx.test.runner.AndroidJUnitRunner
adb shell pm grant com.app.smishing android.permission.READ_SMS
adb shell am instrument -w -r -e class com.app.smishing.data.sms.AnalysisSuspensionInstrumentedTest com.app.smishing.test/androidx.test.runner.AndroidJUnitRunner
```

`AnalysisSuspensionInstrumentedTest` mide el estado en que encuentra la app y se omite si no se cumple su condición:
`analysisIsSuspendedWhileAnSmsPermissionIsMissing` solo corre con un permiso quitado, y
`analysisResumesWhenThePermissionsAreGrantedAgain` solo con todo concedido. Con `-r`, el código de estado de cada una es
`0` si corrió y aprobó, y `-4` si se omitió; un `OK` a secas no distingue las dos cosas.

## Resultado (2026-11-08)

Emulador Pixel 4, Android 10 (API 29), x86_64, emulador 37.2.13, con Temurin 17 y `--rerun`.

| Ejecución | Pruebas | Fallos |
|---|---|---|
| `testDebugUnitTest` (JVM) | 43 (9 nuevas) | 0 |
| `connectedDebugAndroidTest` | 37 (7 nuevas): 36 ejecutadas y 1 omitida a propósito | 0 |

La prueba omitida es `analysisResumesWhenThePermissionsAreGrantedAgain`: se omite en la ejecución completa porque, al
empezar, la app aún no tiene permisos. Se ejecuta en la tercera fase de la revocación (ver abajo).

**Revocación en dos fases** (cada paso, con el estado de los permisos medido con `dumpsys package`):

| Paso | Permisos | Resultado |
|---|---|---|
| Fase 1: se concede el rol | `READ_SMS` y `RECEIVE_SMS` concedidos | el flujo aprueba: el análisis se activa y se analiza un mensaje |
| Se quita `READ_SMS` | `READ_SMS` no concedido | la prueba de suspensión **corre y aprueba**; la de reanudación se omite |
| Se devuelve `READ_SMS` | todos concedidos | la de suspensión se omite; la de reanudación **corre y aprueba** |
| Se quita `RECEIVE_SMS` | `RECEIVE_SMS` no concedido | la prueba de suspensión **corre y aprueba** |
| Se devuelve `RECEIVE_SMS` | todos concedidos | la de reanudación **corre y aprueba** |

**Revisión manual** (capturas en el emulador): con el rol activo y `READ_SMS` quitado, la app abre directamente el paso 3
("PASO 3 DE 3", "Permiso de lectura"), igual que el diseño; al tocar "Permitir", Android concede el permiso sin diálogo y
la app muestra la confirmación; "Omitir" lleva a la tarjeta provisional "Protección apagada". Desde #5, con el rol y
los permisos concedidos esa confirmación ya no se ve: la app abre directamente la lista de mensajes.

## Lo que no cubre

- **La rama "Abrir ajustes" no se probó de principio a fin.** Hace falta que Android niegue la solicitud sin diálogo
  (un rechazo permanente), y en API 29 no hay `pm set-permission-flags` para provocarlo desde fuera. Se probó la pantalla
  y su lógica, no el recorrido real.
- **No hay un diálogo de permisos que revisar** para quien tiene el rol, porque Android los concede solo. El diálogo
  existiría en una instalación de desarrollo sin el rol, pero el flujo no lo pide antes del rol.
- La suspensión se verificó con el clasificador y la compuerta, **no con SMS reales**: recibir mensajes es #4 (hecha, ver
  [recepcion-sms.md](recepcion-sms.md)) y analizarlos va después; quien lo conecte deberá usar `GatedMessageClassifier`. Hoy ningún flujo de la app lo llama.
- `RECEIVE_SMS` se comprobó necesario en API 29 y en el emulador; no en otras versiones de Android, ni en capas de
  fabricante. #4 lo comprobó de principio a fin: sin el permiso un SMS real no llegó, con él sí
  (ver [recepcion-sms.md](recepcion-sms.md)).
- Solo se probó en un emulador API 29 x86_64: no hay prueba en API 35, en ARM ni en un teléfono físico.
- Los textos "ya puede leer y analizar", el del bloqueo y "Abrir ajustes" no están en el diseño y deben revisarse con
  quien lo diseñó.
- No se probó con TalkBack ni con letra grande.
