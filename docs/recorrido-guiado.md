# Recorrido guiado con Smishy

Registro de #88. Estado: implementado, **sin probar todavía en un teléfono ni en el emulador**.

## Qué hace

Smishy invita a ver un recorrido corto la primera vez que se llega a Inicio (ya con el rol y los permisos de SMS, y después del
aviso de los contactos). Son 9 pasos, uno por toque, con un globo que señala cada elemento y oscurece el resto: las 4 pestañas, los
2 modos de protección, el aviso de una estafa y cómo leer su detalle. Termina con un cierre que dice dónde volver a verlo.

- **Persistencia:** `tutorial_state` en el DataStore de preferencias (`pending`, `done`, `skipped`; lo desconocido vale `pending`).
  Solo se guarda al **terminar** u **omitir**. Aceptar la invitación, cerrar la app a mitad o tocar Atrás no guardan nada: sigue
  pendiente y la invitación vuelve a salir.
- **Revertir:** Ajustes › Ayuda › "Ver recorrido" vuelve a pendiente y lo empieza de inmediato.
- **Ejemplo inventado:** el aviso y el detalle de los pasos 7 a 9 son un mensaje de ejemplo ("Banco Ejemplo…", marcado "EJEMPLO").
  Se arma a mano: no pasa por el modelo, no se guarda en el historial y no cambia el resumen de la semana. El detalle es la misma
  `MessageDetailScreen`, sin acciones.
- **Atrás** durante el recorrido lo cierra sin decidir nada (decisión mía; queda como propuesta a confirmar con el diseño).
- Quien ya usaba la app antes de #88 verá la invitación una vez, porque su estado es pendiente.
- No pide permisos, no usa la red y no registra texto de mensajes.

## Cómo está hecho (para reutilizarlo)

| Pieza | Capa | Qué hace |
|---|---|---|
| `TutorialState`, `TutorialProgress`, `TutorialFlow` | `domain.tutorial` | el estado, dónde se guarda y las reglas de qué sigue y cuándo se guarda; sin Android |
| `DataStoreTutorialProgress` | `data.settings` | guarda el estado |
| `Coachmark.kt` | `ui.designsystem.components` | **componente genérico**: `Modifier.coachmarkTarget(clave)`, `CoachmarkRegistry` y `CoachmarkOverlay(CoachmarkMessage)`. No sabe de qué trata el recorrido |
| `SmishingMotion` | `ui.designsystem.theme` | curvas y duraciones de movimiento |
| `TutorialSteps`, `TutorialController`, `TutorialUi` | `ui.tutorial` | los 9 pasos, quien los avanza y su texto |
| `ActionRow` | `ui.designsystem.components` | fila de ajustes con botón |

Para **agregar otro recorrido** u otro paso: marcar el elemento con `Modifier.coachmarkTarget(clave)`, agregar el paso a
`TutorialSteps` y sus textos a `strings.xml`. Los elementos solo se registran mientras hay un recorrido en curso.

## Movimiento

El foco y el globo se desplazan 450 ms con la curva enfatizada de Material 3 (`SmishingMotion`); el texto cambia con un fundido de
200 ms, la tarjeta entra con un escalado de 380 ms y Smishy flota 3 dp. Compose respeta "Quitar animaciones" de Android; Smishy lo
comprueba además por su cuenta. El fondo oscuro se dibuja con un relleno par-impar (sin capas fuera de pantalla) para no cargar un
teléfono de gama baja.

**No se hizo:** el fundido de 280 ms entre pantallas al cambiar de pestaña durante el recorrido (la pantalla cambia de golpe).

## Pruebas

- JVM: `TutorialFlowTest` (8 pruebas, 0 fallos): avanzar, terminar, omitir, reiniciar y cuándo se guarda cada estado.
- Compila (`compileDebugKotlin` y `testDebugUnitTest` en el Terminal de la app).
- **Falta**, a mano: aceptar y terminar, omitir en la invitación y a mitad, cerrar a mitad, reiniciar la app y revertir desde Ajustes;
  con letra grande, con TalkBack y con "Quitar animaciones"; que el historial y el resumen no cambien; y que se vea fluido en el
  teléfono de gama baja.
