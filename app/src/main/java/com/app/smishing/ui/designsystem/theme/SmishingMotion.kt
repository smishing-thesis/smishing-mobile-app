package com.app.smishing.ui.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing

/**
 * Curvas y duraciones de los movimientos de la app. Son las "enfatizadas" de Material 3: arrancan con decisión y frenan despacio, que
 * es lo que hace que un desplazamiento se vea suave y no brusco. Compose respeta "Quitar animaciones" de Android: con la escala en 0
 * estos movimientos terminan al instante.
 */
object SmishingMotion {
    /** Para lo que se mueve de un lugar a otro de la pantalla. */
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Para lo que aparece: llega rápido y se asienta. */
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Desplazarse a otro lugar de la pantalla, en milisegundos. */
    const val MoveMillis = 450

    /** Aparecer con un leve escalado, en milisegundos. */
    const val EnterMillis = 380

    /** Cambiar un texto por otro, en milisegundos. */
    const val FadeMillis = 200
}
