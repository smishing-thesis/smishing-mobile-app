# App de SMS predeterminada

Registro de #2 (RF-01): la app explica por qué necesita ser la aplicación de mensajería predeterminada, la solicita y
comprueba si la conserva. Última verificación: 2026-11-08.

> **ATENCIÓN.** Una app con el rol de SMS recibe en exclusiva los mensajes entrantes y debe guardarlos ella. Hoy los
> receptores están vacíos (la recepción es #4), así que **mientras #4 no esté hecho, esta app solo debe ponerse como
> gestor de SMS en un emulador, nunca en un teléfono con mensajes reales: los SMS que lleguen se perderían.**

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
| `SmsDeliverReceiver`, `MmsDeliverReceiver`, `RespondViaMessageService` | `data.sms` | vacíos; Android los exige para ofrecer el rol |
| `ComposeSmsActivity` | `ui` | vacía (se cierra al abrirse); Android la exige para ofrecer el rol |
| `DefaultSmsRoleScreen` | `ui.onboarding` | la pantalla del paso 2, con los componentes de [design-system.md](design-system.md) |
| `MainActivity` | `ui` | pantalla de entrada: muestra el paso 2, lanza la solicitud y comprueba el rol en `onResume` |

### Manifest

Para ofrecer a una app como gestor de SMS, Android exige cuatro componentes: una actividad que reciba `sms:`,
`smsto:`, `mms:` y `mmsto:`; un receptor de `SMS_DELIVER` (con el permiso `BROADCAST_SMS`); un receptor de
`WAP_PUSH_DELIVER` (con `BROADCAST_WAP_PUSH`); y un servicio de `RESPOND_VIA_MESSAGE` (con
`SEND_RESPOND_VIA_MESSAGE`). Los cuatro están declarados en `AndroidManifest.xml`.

**Permisos.** Esta historia no agrega ninguna etiqueta `uses-permission`. Los permisos de SMS se piden después del rol y
solo los necesarios (#3). Los permisos del manifest que aparecen en los componentes (`BROADCAST_SMS`, etc.) son los
que solo el sistema puede tener: protegen los componentes y no los pide la app.

## Decisiones provisionales

- **"Omitir por ahora"** lleva a una tarjeta "Protección apagada" con el botón "Activar protección", que vuelve al
  paso 2. Es un destino provisional, tomado del diseño de la pantalla de inicio inactiva, hasta que exista esa
  pantalla (#17) y el resto del onboarding (#3).
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
- `SmsRoleInstrumentedTest` (dispositivo): el sistema acepta a la app como gestor de SMS, el estado cambia al quitarlo,
  se ofrece la solicitud, y `MainActivity` vuelve a comprobar el rol al regresar a la app (se concede, se quita y se
  vuelve a conceder con comandos de shell, como lo haría la persona en los ajustes).
- `DefaultSmsRoleScreenTest` (dispositivo): los tres estados de la pantalla, los botones y el área táctil de 48 dp.

## Resultado (2026-11-08)

Emulador Pixel 4, Android 10 (API 29), x86_64, emulador 37.2.13, con Temurin 17 y `--rerun`.

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
- No se guarda ni se procesa ningún SMS: eso es #4. No hay flujo de permisos: eso es #3.
