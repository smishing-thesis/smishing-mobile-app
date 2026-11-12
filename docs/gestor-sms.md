# App de SMS predeterminada

Registro de #2 (RF-01): la app explica por qué necesita ser la aplicación de mensajería predeterminada, la solicita y
comprueba si la conserva. Última verificación: 2026-11-08.

> **ATENCIÓN.** Una app con el rol de SMS recibe en exclusiva los mensajes entrantes y debe guardarlos ella. Desde #4
> guarda los SMS, desde #5 los muestra y desde #6 permite enviar, pero **todavía pierde los MMS y no avisa de los
> mensajes nuevos, así que solo debe ponerse como gestor de SMS en un emulador, nunca en un teléfono con mensajes
> reales.** Ver [recepcion-sms.md](recepcion-sms.md), [conversaciones.md](conversaciones.md) y
> [enviar-sms.md](enviar-sms.md).

## Qué hace

1. Si la app no tiene el rol, muestra el paso 2 del onboarding ("Aplicación principal"): explica el motivo antes de pedirlo.
2. "Usar como aplicación por defecto" abre el diálogo del sistema (`RoleManager.createRequestRoleIntent`).
3. Cuando la persona acepta, la app lo muestra con un visto verde.
4. Cada vez que vuelve a primer plano, la app vuelve a comprobar el rol: si la persona lo quitó en los ajustes del
   sistema, vuelve a mostrar la explicación.

Tres estados (`SmsRoleState`): `Held` (lo tiene), `NotHeld` (no lo tiene) y `Unavailable` (el teléfono no ofrece el rol).

## Código

| Pieza | Capa | Qué hace |
|---|---|---|
| `SmsRoleState`, `smsRoleState`, `SmsRoleRepository` | `domain.sms` | los tres estados y la regla que los decide |
| `AndroidSmsRoleRepository` | `data.sms` | consulta el rol con `RoleManager` y crea el intent de la solicitud |
| `SmsDeliverReceiver` | `data.sms` | desde #4 guarda los SMS entrantes ([recepcion-sms.md](recepcion-sms.md)) |
| `MmsDeliverReceiver`, `RespondViaMessageService` | `data.sms` | vacíos; Android los exige para ofrecer el rol |
| `ComposeSmsActivity` | `ui` | vacía (se cierra al abrirse); Android la exige para ofrecer el rol |
| `DefaultSmsRoleScreen` | `ui.onboarding` | la pantalla del paso 2, con los componentes de [design-system.md](design-system.md) |
| `MainActivity` | `ui` | pantalla de entrada: muestra el paso 2, lanza la solicitud y comprueba el rol en `onResume`; con el rol y los permisos, muestra los mensajes (#5) |

### Manifest

Para ofrecer a una app como gestor de SMS, Android exige cuatro componentes: una actividad que reciba `sms:`,
`smsto:`, `mms:` y `mmsto:`; un receptor de `SMS_DELIVER` (con el permiso `BROADCAST_SMS`); un receptor de
`WAP_PUSH_DELIVER` (con `BROADCAST_WAP_PUSH`); y un servicio de `RESPOND_VIA_MESSAGE` (con
`SEND_RESPOND_VIA_MESSAGE`). Los cuatro están declarados en `AndroidManifest.xml`.

**Permisos.** Esta historia (#2) no agregó ninguna etiqueta `uses-permission`. Los permisos de SMS (`READ_SMS` y
`RECEIVE_SMS`) llegaron con #3, se piden después del rol y solo los necesarios: ver [permisos-sms.md](permisos-sms.md).
Los permisos del manifest que aparecen en los componentes (`BROADCAST_SMS`, etc.) son los que solo el sistema puede
tener: protegen los componentes y no los pide la app.

## Decisiones provisionales

- **"Omitir por ahora"** lleva a una tarjeta "Protección apagada" con el botón "Activar protección", que vuelve al
  paso 2 o 3, según dónde se omitió. Es un destino provisional, tomado del diseño de la pantalla de inicio inactiva,
  hasta que exista esa pantalla (#17) y el paso 1 del onboarding.
- **Textos fuera del diseño.** El diseño no tiene el estado "ya tiene el rol" ni "el teléfono no lo ofrece". Los textos
  de esos dos estados (`default_sms_held_message` y `default_sms_unavailable`) son míos y deben revisarse con quien
  diseñó.
- **Navegación y estado.** No hay librería de navegación ni ViewModel: la decisión sigue abierta. El estado vive en
  `MainActivity`.

## Cómo verificar

Con el emulador de API 29 abierto y `JAVA_HOME` en un JDK de 17 a 23 (ver [entorno.md](entorno.md)):

```powershell
.\gradlew.bat testDebugUnitTest --rerun
.\gradlew.bat connectedDebugAndroidTest --rerun
```

- `SmsRoleStateTest` (JVM): la regla de los tres estados.
- `SmsRoleInstrumentedTest` (dispositivo): el sistema ofrece la solicitud del rol, y el flujo `roleFirstThenPermissionsThenAnalysis`
  comprueba que sin el rol la app pide primero el rol, que el sistema acepta a la app como gestor de SMS al concederlo
  con un comando de shell (como lo haría la persona) y que `MainActivity` lo nota al volver a la app.
- `DefaultSmsRoleScreenTest` (quitada: ver [pruebas.md](pruebas.md)) (dispositivo): los tres estados de la pantalla, los botones y el área táctil de 48 dp.

> **Cambio con #3.** Esta historia tenía pruebas que concedían y **quitaban** el rol dentro de la prueba. Al declarar los
> permisos de SMS (#3), quitar el rol los retira y Android mata el proceso de la app, que es el mismo en que corre la
> prueba (`Instrumentation run failed due to Process crashed`). Esas pruebas se reemplazaron por un flujo que solo
> avanza; que la app nota que el rol se quitó se comprueba a mano, como se describe abajo. Ver
> [permisos-sms.md](permisos-sms.md).

## Resultado al cerrar #2 (2026-11-08)

Emulador Pixel 4, Android 10 (API 29), x86_64, emulador 37.2.13, con Temurin 17 y `--rerun`. Estos son los números de
ese momento; los actuales, con #3, están en [permisos-sms.md](permisos-sms.md).

| Ejecución | Pruebas | Fallos |
|---|---|---|
| `testDebugUnitTest` (JVM) | 34 (4 nuevas) | 0 |
| `connectedDebugAndroidTest` | 32 (9 nuevas) | 0 |

**Las pruebas no son vacías.** Se cambió a propósito la acción del receptor de `SMS_DELIVER` en el manifest: el sistema
dejó de aceptar a la app (`expected:<Held> but was:<NotHeld>`) y fallaron las pruebas del rol. Se revirtió.

**Revisión manual con el diálogo real del sistema** (capturas en el emulador): sin el rol aparece la explicación; al
tocar el botón el sistema muestra "Set Smishing Detector as your default SMS app?" con la app junto a "Messages" (la
actual); al aceptar, la app muestra la confirmación y `cmd role dump` lista `holders=com.app.smishing`; al quitar el
rol y volver a la app, reaparece la explicación; "Omitir por ahora" lleva a "Protección apagada". Al quitar el rol, el
sistema devolvió la función a Google Messages por su cuenta.

## Lo que no cubre

- Solo se probó en un emulador API 29 x86_64. No se probó en API 35, en ARM ni en un teléfono físico. El diálogo del
  rol puede verse distinto en otras versiones y en capas de fabricante.
- De los cuatro componentes, solo se comprobó que el receptor de `SMS_DELIVER` es imprescindible. Los otros tres
  están declarados porque Android lo documenta así, pero no se probó uno por uno que cada uno sea necesario.
- El estado `Unavailable` solo se probó con la pantalla; no se probó en un dispositivo real sin telefonía.
- El diálogo del sistema se vio en inglés porque el emulador está en inglés; lo traduce Android, no la app.
- No se probó con TalkBack ni con letra grande.
- Esta historia no guarda ni procesa ningún SMS: la recepción es #4 (ver [recepcion-sms.md](recepcion-sms.md)) y los
  permisos se piden en #3.
- Hoy ninguna prueba automática quita el rol; que la app lo nota al quitarlo se comprobó solo a mano (ver arriba).
