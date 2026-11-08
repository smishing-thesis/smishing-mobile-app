# Librería de diseño (Compose + Material 3)

Los tokens y los componentes reutilizables del diseño ("SMS Shield", Figma TP1, septiembre 2026), para armar todas las
pantallas sin repetir colores, tipografías ni estilos. Última verificación: 2026-11-07.

Vive en el paquete `com.app.smishing.ui.designsystem` dentro del módulo `app`. Es solo tema claro, como el diseño.

## Decisiones

| Decisión | Elección | Por qué |
|---|---|---|
| Toolkit de UI | Compose con Material 3 | los diseños son Material 3: los tokens son el tema y cada componente una función reutilizable |
| Ubicación | paquete dentro de `app` | lo más simple; se puede extraer a un módulo `:designsystem` más adelante |
| Contraste de los chips | texto más oscuro que el del diseño | ver abajo |
| Textos | siempre por parámetro | la librería no tiene cadenas fijas; las pantallas las toman de recursos |

## Tokens (`theme/`)

| Archivo | Qué contiene |
|---|---|
| `SmishingPalette` | los 15 colores del diseño y `statusColors(Status)` para Seguro, Sospechoso y Peligroso |
| `SmishingTypography` | los 13 estilos de texto (Roboto, en sp) y su equivalente en Material 3 |
| `Spacing`, `Radius`, `SmishingShapes`, `Elevation`, `MinTouchTarget` | espaciado de 4 a 48 dp, radios de 0 a 28 dp, tres elevaciones y el área táctil de 48 dp |
| `SmishingTheme` | el tema de Material 3 con todo lo anterior; las pantallas se envuelven en él |

Una prueba de la JVM (`DesignTokensTest`) compara cada valor con el diseño, para que no se desvíen.

### Contraste de los chips de estado

Con los colores del diseño, el texto sobre el fondo de su chip no llega al mínimo AA de 4,5:1, y la guía de tipografía
pide pensar en adultos mayores. Los chips y los textos de estado usan variantes más oscuras; los fondos y los iconos
conservan los colores del diseño.

| Estado | Color del diseño | Contraste sobre su fondo | Variante usada | Contraste |
|---|---|---|---|---|
| Seguro | `#1E8E3E` | 3,70 | `#137333` | 5,24 |
| Sospechoso | `#E37400` | 2,90 | `#995200` | 5,50 |
| Peligroso | `#D93025` | 4,05 | `#C5221F` | 4,92 |

Las variantes también cumplen 4,5:1 sobre blanco y sobre el fondo secundario. Un test lo comprueba, y otro deja
constancia de que los colores originales no lo cumplen.

## Componentes (`components/`)

| Componente | Para qué |
|---|---|
| `PrimaryButton`, `SecondaryButton`, `LinkButton` | acción principal, secundaria y de baja prioridad ("Omitir por ahora") |
| `StatusChip` | etiqueta Peligro, Sospechoso o Seguro, con icono además del color |
| `ProtectionCard`, `SummaryCard`, `SummaryRow` | estado de la protección (activa o apagada) y resumen de la semana |
| `AlertBanner`, `StatusBanner` | aviso rojo con acción ("Ver riesgo") y encabezado de estado ("Mensaje Peligroso") |
| `SmishingTopBar`, `SearchAction`, `SmishingBottomBar`, `SmishingExtendedFab` | barras superior (azul o blanca) e inferior, y el botón "Escribir" |
| `ConversationRow`, `MessageBubble`, `MessageInputBar`, `SmishingTextField` | lista de conversaciones, burbujas, redacción y campos de texto |
| `RadioOption`, `SwitchRow`, `PermissionRow`, `SectionLabel` | ajustes y permisos |
| `SmishingDialog`, `ConfirmDialog` | diálogos ("¿Está seguro?", "Compartir datos de forma anónima") |
| `OnboardingStepLayout`, `StepIndicator` | los pasos de bienvenida |
| `OriginalMessageBox`, `RiskReasonRow`, `ActionDoneRow`, `InfoPanel` | el detalle del análisis |

`icons/SmishingIcons` trae los ocho iconos que el set básico de Material no tiene (error, chat, historial, reloj,
enlace, llave, entidad y escudo). Se evita el set extendido de Material porque pesa mucho para gama baja.

### Cómo se usa

```kotlin
SmishingTheme {
    PrimaryButton(text = stringResource(R.string.siguiente), onClick = { /* ... */ })
    StatusChip(Status.Danger, stringResource(R.string.peligro))
}
```

`Status` es solo presentación. El nivel de riesgo real lo asignará el dominio (#9).

## Galería (solo debug)

`ComponentGalleryActivity` muestra todos los componentes y aparece como icono "Galería de componentes" en el
emulador. Existe solo en la variante debug. Con `--ei section N` (0 a 8) muestra una sola sección, útil para
capturarla:

```powershell
adb shell am start -n com.app.smishing/.ui.designsystem.ComponentGalleryActivity --ei section 4
```

## Verificación (2026-11-07)

Con Temurin 17 y `--rerun`; dispositivo: emulador Pixel 4, Android 10 (API 29), x86_64.

| Ejecución | Pruebas | Fallos |
|---|---|---|
| `testDebugUnitTest` (JVM) | 30, de ellas 10 de tokens y contraste | 0 |
| `connectedDebugAndroidTest` | 23, de ellas 13 de componentes | 0 |

Las pruebas de dispositivo comprueban que cada componente se muestra, responde al toque, y que los botones, filas y
campos respetan el área táctil de 48 dp.

**Comparación visual con el diseño.** Se capturaron las 9 secciones de la galería en el emulador y se compararon a
ojo con las pantallas del diseño. Coinciden las tarjetas de protección, la lista de mensajes, la conversación con el
banner, el detalle del análisis, los ajustes, la navegación inferior y los dos diálogos. Se corrigieron tres
diferencias que aparecieron al compararlas: el texto de los botones de varias líneas no estaba centrado, el botón
del banner era demasiado ancho y partía el título, y los diálogos salían teñidos de azul por la elevación tonal de
Material (ahora son blancos y planos). No hay una comparación automática de píxeles, así que esta revisión es visual.

### Costo para gama baja

Se midió el APK de release con R8 (reducción de código y recursos activada solo para medir) en `main` y en esta rama:

| APK de release | Tamaño |
|---|---|
| `main`, sin Compose | 19,71 MB |
| Con Compose y todos los componentes conservados | 21,61 MB (+1,90 MB, +9,6 %) |
| Con Compose, sin conservar los componentes | 20,02 MB (+0,31 MB) |

La segunda fila es la que importa: la tercera es engañosa, porque R8 elimina Compose si ninguna pantalla del release lo
usa, como ocurre hoy. Casi todo el aumento está en el código (dex de 1,19 a 2,74 MB). El APK sigue dominado por las
librerías nativas de LiteRT (17,6 MB para tres ABI).

El APK de debug pasa de 35,00 a 56,18 MB, pero incluye herramientas de depuración y no es lo que se distribuye.

## Lo que no cubre

- **Rendimiento en gama baja.** No se midió consumo de memoria ni velocidad de dibujo en hardware de gama baja. El
  arranque en frío de la galería de debug fue de unos 2,4 s en el emulador del PC, que no es representativo y no tiene
  contra qué compararse porque la app base no tenía pantallas.
- **Solo API 29, x86_64.** No se probó en ARM, en un teléfono físico ni en otras versiones de Android.
- **Accesibilidad.** Se comprobaron contrastes y área táctil. No se probó con TalkBack ni con tamaños de letra grandes.
- **Pantallas completas.** Esta librería son las piezas; las pantallas de las historias (#2, #3, #5, #6 y las demás) se
  arman con ellas y se verifican en cada historia. El diseño de la notificación (pantalla de bloqueo y panel) no
  está en la librería, porque es de Android y no de Compose.
- **Modo oscuro.** El diseño no lo define y no se implementó.
- **Tipografía.** La guía de tipografía dice que el cuerpo mínimo es 18 sp, pero su propia escala incluye cuerpo de 16
  y notas de 14 y 12. Se implementó la escala tal cual está.
- **Elevaciones.** El diseño nombra tres niveles (sutil, media, alta) sin dar valores; se eligieron 1, 4 y 8 dp.
- **Nombre de la app.** El diseño dice "Lorem Ipsum" en las pantallas y "SMS Shield" en el sistema de diseño. La
  librería no lo fija: las pantallas lo reciben por parámetro.
- **Compose 1.12 o más nuevo.** Pide AGP 9.1 o superior. Con AGP 8.13.2 la última versión posible es la del BOM
  2026.06.01 (Compose UI 1.11.4); ver [entorno.md](entorno.md).
