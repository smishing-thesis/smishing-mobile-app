package com.app.smishing.domain.risk

import com.app.smishing.domain.classification.ClassificationResult
import com.app.smishing.domain.signals.SignalAnalysis
import com.app.smishing.domain.signals.SignalType

/** El nivel de riesgo de un SMS (#9). */
enum class RiskLevel { Low, Medium, High }

/**
 * Qué regla de la política decidió el nivel. El [id] es estable: se guarda con el resultado para poder explicar y repetir la
 * decisión.
 */
enum class RiskRule(val id: String, val level: RiskLevel) {
    /** El modelo lo marca sospechoso y al menos una señal de peso lo respalda. */
    ModelAndEvidence("risk.model_and_evidence", RiskLevel.High),

    /** El modelo lo marca sospechoso pero ninguna señal de peso lo respalda. */
    ModelOnly("risk.model_only", RiskLevel.Medium),

    /** El modelo no lo marca, pero las señales por sí solas son fuertes: suplantación, o dos o más señales de peso. */
    SignalsOnly("risk.signals_only", RiskLevel.Medium),

    /** Ni el modelo ni las señales dan motivo. */
    NoEvidence("risk.no_evidence", RiskLevel.Low),
}

/**
 * Un nivel de riesgo y todo lo necesario para explicarlo y repetirlo: la regla que lo decidió, lo que dijo el modelo (puntaje,
 * umbral y versiones), las señales con la versión de sus reglas, cuáles de ellas contaron y la versión de la política.
 *
 * Lleva fragmentos del mensaje dentro de [signals]: no debe escribirse en logs (su `toString` no los muestra).
 */
data class RiskAssessment(
    val level: RiskLevel,
    val rule: RiskRule,
    val model: ClassificationResult.Classified,
    val signals: SignalAnalysis,
    /** Los tipos de señal que contaron como respaldo (el enlace solo no cuenta). */
    val evidence: Set<SignalType>,
    val policyVersion: String,
)

/** El resultado de evaluar un mensaje: un nivel, o el motivo por el que el modelo no lo evaluó y por eso no hay nivel. */
sealed interface RiskResult {
    data class Assessed(val assessment: RiskAssessment) : RiskResult

    /** El modelo no evaluó el mensaje (muy corto o análisis suspendido): no se alerta ni se bloquea, como pide su política de entrada. */
    data class NotAssessed(val reason: ClassificationResult.NotClassified.Reason) : RiskResult
}
