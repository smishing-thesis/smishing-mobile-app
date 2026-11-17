package com.app.smishing.ui.designsystem.components

import androidx.annotation.DrawableRes
import com.app.smishing.R
import java.time.LocalTime

/**
 * El momento del día que decide el fondo de la tarjeta de protección activa (Inicio). La hora es la del teléfono.
 *
 * - [Morning]: de 6:00 a 11:59, montañas verdes.
 * - [Midday]: de 12:00 a 16:59, montañas azules con el sol arriba al centro.
 * - [Sunset]: de 17:00 a 19:29, montañas con el sol casi oculto a la derecha.
 * - [Night]: de 19:30 a 5:59, montañas azul oscuro con estrellas y luna.
 */
enum class DayPart(@DrawableRes val scene: Int) {
    Morning(R.drawable.scene_morning),
    Midday(R.drawable.scene_midday),
    Sunset(R.drawable.scene_sunset),
    Night(R.drawable.scene_night),
    ;

    companion object {
        fun at(time: LocalTime): DayPart {
            val minutes = time.hour * 60 + time.minute
            return when {
                minutes in 6 * 60 until 12 * 60 -> Morning
                minutes in 12 * 60 until 17 * 60 -> Midday
                minutes in 17 * 60 until 19 * 60 + 30 -> Sunset
                else -> Night
            }
        }
    }
}
