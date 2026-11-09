package com.app.smishing.domain.mitigation

import com.app.smishing.domain.history.AnalysisRecord
import com.app.smishing.domain.risk.RiskLevel

/**
 * Qué hace la app con un mensaje de riesgo alto (#13), según el diseño de Ajustes:
 * - [Automatic] "Protegerme automáticamente": bloquea el número y retira el mensaje del buzón.
 * - [Ask] "Preguntarme antes de actuar": muestra las opciones para que la persona decida.
 * - [Disabled] "Solo avisarme": avisa pero no toma ninguna acción.
 *
 * El [id] es lo que se guarda en las preferencias: es estable aunque cambien los textos.
 */
enum class MitigationMode(val id: String) {
    Automatic("automatic"),
    Ask("ask"),
    Disabled("disabled");

    companion object {
        /**
         * El modo mientras la persona no elige uno. Es *preguntar*: el automático retira mensajes del buzón y no debe activarse
         * sin que la persona lo elija.
         */
        val DEFAULT = Ask

        /** El modo guardado con ese id; si falta o no se reconoce, el [DEFAULT]. */
        fun fromId(id: String?): MitigationMode = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/** Lo que corresponde hacer con un mensaje, una vez conocido el modo. */
enum class MitigationDecision {
    /** Actuar sin preguntar: bloquear el número y retirar el mensaje. */
    Act,

    /** Mostrar las opciones y dejar que la persona decida. */
    AskUser,

    /** No tomar ninguna acción (solo se avisa). */
    DoNothing,
}

/** La regla que une el nivel de riesgo y el modo (#13). Función pura. */
object MitigationPolicy {

    /** Los niveles sobre los que la app puede actuar. Solo *alto*; medio y bajo nunca disparan una acción, sea cual sea el modo. */
    val ACTIONABLE_LEVELS: Set<RiskLevel> = setOf(RiskLevel.High)

    fun decide(level: RiskLevel, mode: MitigationMode): MitigationDecision = when {
        level !in ACTIONABLE_LEVELS -> MitigationDecision.DoNothing
        mode == MitigationMode.Automatic -> MitigationDecision.Act
        mode == MitigationMode.Ask -> MitigationDecision.AskUser
        else -> MitigationDecision.DoNothing
    }
}

/** Las preferencias de mitigación de la persona, guardadas en el teléfono. */
interface MitigationSettings {
    /** El modo elegido, o [MitigationMode.DEFAULT] si no eligió. Puede bloquear: no se llama desde el hilo principal. */
    fun mode(): MitigationMode

    fun setMode(mode: MitigationMode)

    /** Avisa cuando el modo cambia. Devuelve algo que se cierra para dejar de avisar. */
    fun onChange(listener: () -> Unit): AutoCloseable = AutoCloseable {}
}

/**
 * Ejecuta la decisión sobre un mensaje. Solo se llama cuando hay algo que hacer ([MitigationDecision.Act] o
 * [MitigationDecision.AskUser]): el modo ya se consultó. Bloquear (#14) y retirar (#15) se agregan aquí.
 */
fun interface Mitigator {
    fun apply(decision: MitigationDecision, record: AnalysisRecord)
}
