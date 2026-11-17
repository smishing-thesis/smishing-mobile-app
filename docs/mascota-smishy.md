# Mascota Smishy

El escudo con ojos que acompaña a la app. Se diseñó en Figma (archivo TP1, frames "Smishy — mascota" y "Smishy — investigando
(animación)") y se guarda aquí en dos formas: el recurso que usa la app y el SVG de origen.

## Dónde están

| Pose | Recurso de la app (`app/src/main/res/drawable/`) | SVG de origen (`designs/smishy/`) | Para qué |
|---|---|---|---|
| Activo | `smishy_active.xml` | `smishy_active.svg` | Protección activa: contento |
| Apagado | `smishy_off.xml` | `smishy_off.svg` | Falta un permiso: dormido, con ZZZ |
| Pantalla de carga | `smishy_splash.xml` | `smishy_splash.svg` | Sobre fondo azul (borde claro) |
| Investigando | `smishy_investigating.xml` | `smishy_investigating.svg` | Cuadro base de la animación, con lentes y cejas |

Son vectores (`VectorDrawable`), no imágenes, así que no pesan y se ven nítidos en cualquier pantalla. Los colores son los de
`SmishingPalette`. La pose de alerta (preocupado, ámbar) está dibujada en Figma pero no se agregó a la app por ahora.

## Dónde se usa

- **Inicio:** la tarjeta de protección (`ProtectionCard`) lleva una escena ilustrada arriba con Smishy (`smishy_active` o `smishy_off`).
  Activa: el fondo cambia con la hora del teléfono (`DayPart`): mañana 6:00 a 11:59 (`scene_morning`, montañas verdes), mediodía y tarde
  12:00 a 16:59 (`scene_midday`, montañas azules, sol arriba al centro y pradera verde), atardecer 17:00 a 19:29 (`scene_sunset`, sol casi
  oculto a la derecha) y noche 19:30 a 5:59 (`scene_night`, estrellas y luna). Apagada: siempre `scene_off` (ciudad de noche en blanco y
  negro). Se calcula al abrir la pantalla; la galería de debug muestra los cuatro fondos. Los SVG de origen están en `designs/smishy/`.
- **Pantalla de carga:** `SmishySplashScreen` (Smishy a pantalla completa, sobre el azul de la marca, con su nombre y tres puntos que suben y bajan
  como una ola, de 14 dp y cada 1,2 s; quietos si las animaciones del sistema están desactivadas).
  Se ve mientras se lee lo guardado. **Smishy va quieto**: la carga es tan rápida que la animación no se alcanza a ver. La animación
  sigue en el código (`animated = true`) pero no se usa hoy. La fuente del nombre es provisional (`SmishingTypography.Wordmark`).

- **Onboarding (pasos 1 y 2):** `IllustratedOnboardingLayout` (en `Onboarding.kt`) con una ilustración vectorial arriba, los puntos del paso
  encima, el título y el texto abajo y los botones. Paso 1 (`onboarding_welcome_art`): Smishy y dos mensajes, uno normal y uno peligroso
  (cara enojada en rojo), sobre azul. Paso 2 (`onboarding_role_art`): la app de mensajes actual y Smishy, sobre azul claro; el texto del
  botón se parte en dos líneas si no cabe y "Omitir por ahora" va debajo. "PASO N DE M" ya no se ve: solo los puntos, y el lector de
  pantalla lo sigue leyendo. El texto es `BodyMedium` (16 sp); con letra grande en el teléfono, la pantalla se desplaza.
  El paso 1 va **sin puntos** (avanza con "Siguiente"); los puntos solo salen en el paso 2, donde el primero vuelve al paso 1 ("Atrás"
  del sistema también). El cambio entre pasos se desliza (350 ms; a la izquierda al avanzar, a la derecha al volver) y el
  resto de cambios de pantalla (carga, Inicio, Mensajes) usan un fundido corto, todo en `MainActivity` (`AnimatedContent`).
  La pantalla de recuperación del permiso de lectura sigue con el diseño anterior (`OnboardingStepLayout`), sin ilustración.
- **Pantalla de carga del sistema** (la que Android muestra antes de que la app dibuje): `Theme.SmishingDetector.Splash`
  (`core-splashscreen`), con `smishy_splash_icon` quieto sobre el azul. Reemplaza al icono por defecto de Android.
- **Icono de la app:** `ic_launcher_foreground` (Smishy) sobre `ic_launcher_background` (azul), en vez del robot de Android.

## Animación "investigando"

Hoy **no se usa en ninguna pantalla** (se probó en la pantalla de carga y es demasiado rápida); queda lista para disparar al cambiar un
permiso o en otro momento. Pensada para una sola vez. Está dibujada en Compose (`SmishyAnimation.kt`, `SmishyInvestigating`),
con la línea de tiempo de `SmishyInvestigatingTimeline`. Vista previa para abrir en el navegador:
`designs/smishy/animacion_investigando.html`; si se cambia una, se cambia la otra. Dura 5 s:

1. 0 a 0,5 s: los lentes y las cejas aparecen **juntos**, poco a poco; la sonrisa pasa a un gesto pensativo (una ceja alta y
   arqueada, la otra baja).
2. 0,5 a 3,9 s: mira al frente, luego a la derecha (1,5 s) y después a la izquierda (2,9 s).
3. 3,9 a 4,4 s: vuelve al frente, parpadea y sonríe.
4. 4,4 a 5,0 s: los lentes y las cejas desaparecen **juntos**, poco a poco.

Con las animaciones del sistema desactivadas (accesibilidad), no se anima: se muestra el Smishy normal.

## Pendiente

- **Fuente:** sin decidir. El onboarding de los bocetos usaba Nunito (Google Fonts, pesos 600 y 800); la app sigue con la fuente del
  sistema. Cuando se decida, se cambia solo en `SmishingTypography` (`family`).
- Decidir dónde usar la animación (por ejemplo al cambiar un permiso, donde la persona sí se queda mirando la pantalla).
- Ajustar la transición entre la pantalla de carga del sistema (Smishy quieto, centrado) y la de Compose (Smishy animado con el nombre
  debajo): hoy Smishy sube un poco al pasar de una a otra.
- Nada de esto se compiló desde esta sesión (Gradle falla aquí); la primera prueba en el emulador es la de quien lo compile.
- Las ZZZ del Smishy apagado son parte del dibujo; si se quieren animar (subir y desvanecerse) hay que separarlas.
