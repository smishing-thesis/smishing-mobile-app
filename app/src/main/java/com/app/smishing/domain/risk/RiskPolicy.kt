package com.app.smishing.domain.risk

import com.app.smishing.domain.classification.ClassificationResult
import com.app.smishing.domain.signals.SignalAnalysis
import com.app.smishing.domain.signals.SignalType

/**
 * La política que combina el modelo y las señales en un nivel (#9). Es una función pura: el mismo puntaje y las mismas señales
 * dan siempre el mismo nivel.
 *
 * Las señales de peso ("respaldo") son urgencia, suplantación y solicitud de datos. **El enlace solo no cuenta**: lo traen
 * muchos mensajes legítimos (73,5 % de los legítimos del corpus, ver docs/senales.md).
 *
 * En este orden:
 * 1. Alto, si el modelo lo marca sospechoso **y** hay al menos un respaldo.
 * 2. Medio, si el modelo lo marca sospechoso y no hay respaldo.
 * 3. Medio, si el modelo no lo marca pero hay suplantación o dos o más respaldos.
 * 4. Bajo, en cualquier otro caso.
 *
 * Es una propuesta de diseño, no un valor validado con datos: ver docs/nivel-de-riesgo.md.
 */
object RiskPolicy {

    const val VERSION = "1.0.0"

    /** Las señales que respaldan una sospecha. */
    val EVIDENCE_TYPES: Set<SignalType> = setOf(SignalType.Urgency, SignalType.Impersonation, SignalType.DataRequest)

    /** Con cuántos respaldos las señales bastan por sí solas, sin que el modelo marque el mensaje. */
    private const val STRONG_SIGNALS_ALONE = 2

    fun decide(model: ClassificationResult.Classified, signals: SignalAnalysis): RiskAssessment {
        val evidence = signals.types.intersect(EVIDENCE_TYPES)
        val rule = when {
            model.isSmishing && evidence.isNotEmpty() -> RiskRule.ModelAndEvidence
            model.isSmishing -> RiskRule.ModelOnly
            SignalType.Impersonation in evidence || evidence.size >= STRONG_SIGNALS_ALONE -> RiskRule.SignalsOnly
            else -> RiskRule.NoEvidence
        }
        return RiskAssessment(rule.level, rule, model, signals, evidence, VERSION)
    }
}
