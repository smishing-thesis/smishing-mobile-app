package com.app.smishing.domain.risk

import com.app.smishing.domain.signals.SignalType

/**
 * Qué se le recomienda a la persona ante un mensaje de riesgo medio o alto. Depende de **lo que se encontró en el mensaje**, no es
 * siempre el mismo texto: "No abra el enlace" solo tiene sentido si el mensaje trae un enlace.
 *
 * - [LinkAndData]: trae un enlace y además pide datos o claves.
 * - [Link]: trae un enlace.
 * - [Data]: pide datos o claves.
 * - [General]: ninguna de las dos (por ejemplo, el modelo lo marcó pero no se encontró nada concreto): un consejo que sirve siempre.
 */
enum class Recommendation {
    LinkAndData, Link, Data, General;

    companion object {
        /** La recomendación para un mensaje cuyas señales detectadas son [types]. */
        fun of(types: Set<SignalType>): Recommendation {
            val link = SignalType.Link in types
            val data = SignalType.DataRequest in types
            return when {
                link && data -> LinkAndData
                link -> Link
                data -> Data
                else -> General
            }
        }
    }
}
