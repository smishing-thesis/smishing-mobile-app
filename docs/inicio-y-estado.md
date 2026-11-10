# Inicio, estado de la protección y bienvenida

Registro de #17 (RF-12, RF-15): la pantalla de inicio muestra el estado **real** del rol, los permisos y la protección, y no dice
que la protección está activa si falta algún requisito. Incluye el paso 1 del onboarding. Última verificación: 2026-11-10, en el
emulador de API 29.

## Cuándo está activa la protección

Solo si se cumplen **los tres** requisitos (`ProtectionStatus`, `domain.settings`):

| Requisito | Por qué |
|---|---|
| Aplicación de mensajería principal (rol de SMS) | sin él no se reciben ni se guardan los mensajes |
| Permiso de leer y recibir SMS | sin él no se analizan los mensajes que llegan |
| Notificaciones | sin ellas el análisis corre, pero no se puede avisar |

- **Enviar mensajes no cuenta**: no afecta a la detección.
- Un requisito que no se pudo comprobar **cuenta como faltante**; mientras se leen los estados, la pantalla **no afirma nada** (ni
  "activa" ni "apagada").
- El estado se lee del sistema y **se vuelve a leer cada vez que la persona regresa a la app** (por ejemplo, tras cambiar un permiso
  en los ajustes del sistema): se comprobó que, al conceder el rol con la app abierta, Inicio pasa a "activa" sin reabrirla.
- No es lo mismo que la compuerta del análisis (`AnalysisGate`, rol y permisos de SMS): esta pide además las notificaciones.

## La pantalla "Inicio"

Primera pestaña de la barra inferior (Inicio · Mensajes · Historial · Ajustes); la app se abre en ella.

- **Activa:** "Protección activa — La aplicación está revisando sus mensajes en este momento", con el indicador "Activo". No dice
  "protegido": no puede asegurarlo.
- **Apagada:** tarjeta roja "Protección apagada", el indicador "Inactivo" y el botón "Activar protección". La lista "Lo que necesita
  la protección" marca cuál falta. Si falta el rol o el permiso de SMS, el botón vuelve al onboarding; si solo faltan las
  notificaciones, abre los ajustes de notificaciones de la app.
- **Resumen de la semana:** mensajes revisados y mensajes con posible estafa, de los **últimos 7 días** (ventana móvil, no la semana
  del calendario), solo de lo que hay en el historial de este teléfono. Cuenta como posible estafa un mensaje de riesgo alto que la
  persona **no** marcó como seguro. Con la protección apagada se ve atenuado, con los números reales; mientras se lee, muestra "--".

## Paso 1: bienvenida

`WelcomeScreen`: "¡Bienvenido!", qué hace la app y que el análisis se hace en el teléfono, sin internet. **No promete protección**
("ayudarle a detectar", "le avisaremos si un mensaje tiene señales"): se cambió el texto del mockup, que decía "protegerlo" y
"inmediatamente". Se muestra **una sola vez** (la marca `welcome_seen` está en el DataStore de preferencias) y **solo si la app aún
no tiene el rol y los permisos**: quien ya los tiene no la ve. "Omitir" en el paso 2 o 3 lleva a Inicio con la protección apagada.

## Código

| Pieza | Capa |
|---|---|
| `ProtectionStatus`, `OnboardingProgress` | `domain.settings` |
| `WeeklySummary`, `weeklySummary` | `domain.history` |
| `DataStoreOnboardingProgress` | `data.settings` |
| `HomeScreen`, `HomeRoute` | `ui.home` |
| `WelcomeScreen` | `ui.onboarding` |

No se agregó ningún permiso.

## Pruebas

- JVM (205 en total): `ProtectionStatusTest` (con los tres requisitos activa; faltando cualquiera no, y la nombra; enviar no cuenta;
  lo no comprobado cuenta como faltante) y `WeeklySummaryTest` (ventana de 7 días con su borde, lo futuro no cuenta, lo marcado como
  seguro no es posible estafa). Los textos nuevos entran en `ExplanationWordingTest`.
- Dispositivo (171 en total, 1 omitida por diseño): `HomeScreenTest` (activa solo con todo; sin cada requisito no dice activa;
  mientras se lee no afirma nada; sigue a los permisos; resumen), `WelcomeScreenTest` (no promete protección) y el flujo del rol de
  `SmsRoleInstrumentedTest`, ahora con la bienvenida, el paso del rol, Inicio activo y el paso a Mensajes, con el rol real.
- Mutación: quitar las notificaciones de los requisitos rompe 2 pruebas JVM, y mostrar "activa" sin comprobar rompe 7 del dispositivo
  (revertidas).
- A mano: bienvenida, "Omitir" → Inicio apagado con lo que falta, y al conceder el rol con la app abierta → Inicio activo.

## Lo que no se verificó

- **Quitar el rol o un permiso con la app abierta:** Android mata el proceso; solo se vio la lectura al volver a abrir y al conceder.
- **Android 13 o superior** (permiso de notificaciones en tiempo de ejecución), TalkBack y un teléfono real.
- **Que se entienda** la pantalla: lo evalúa #23 con personas. Las pruebas de lenguaje son mecánicas.
- El modelo no es un requisito del estado: si no se pudiera cargar, Inicio seguiría diciendo "activa" (el análisis lo reportaría
  como no evaluado). No se probó esa falla.
