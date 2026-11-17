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

- **Inicio:** la tarjeta de protección (`ProtectionCard`) muestra `smishy_active` si la protección está activa y `smishy_off` si está
  apagada. Reemplazó al primer escudo con candado (`img_protection_*`, ya retirado).
- **Pantalla de carga:** `SmishySplashScreen` (Smishy a pantalla completa, sobre el azul de la marca, con su nombre y tres puntos).
  Se ve mientras se lee lo guardado. **Smishy va quieto**: la carga es tan rápida que la animación no se alcanza a ver. La animación
  sigue en el código (`animated = true`) pero no se usa hoy. La fuente del nombre es provisional (`SmishingTypography.Wordmark`).

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

- Decidir dónde usar la animación (por ejemplo al cambiar un permiso, donde la persona sí se queda mirando la pantalla).
- Ajustar la transición entre la pantalla de carga del sistema (Smishy quieto, centrado) y la de Compose (Smishy animado con el nombre
  debajo): hoy Smishy sube un poco al pasar de una a otra.
- Nada de esto se compiló desde esta sesión (Gradle falla aquí); la primera prueba en el emulador es la de quien lo compile.
- Las ZZZ del Smishy apagado son parte del dibujo; si se quieren animar (subir y desvanecerse) hay que separarlas.
