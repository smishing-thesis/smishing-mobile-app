package com.app.smishing.domain.signals

/**
 * Las señales que la app identifica en un SMS (#8), en el orden en que el diseño las lista en "¿Por qué es riesgoso?":
 * - [Urgency]: presiona a actuar ya (plazos, amenazas de bloqueo o multa, "ahora mismo").
 * - [Impersonation]: dice venir de una entidad conocida y trae un enlace que no es de ella o pide datos.
 * - [Link]: contiene un enlace (una URL o un dominio).
 * - [DataRequest]: pide datos o claves ("ingrese", "confirme", "actualice" junto a "clave", "datos"...).
 */
enum class SignalType { Urgency, Impersonation, Link, DataRequest }

/**
 * Una señal detectada: qué es, qué regla la detectó (para poder rastrearla) y el fragmento del mensaje que la motivó.
 * El fragmento es texto del usuario: no se muestra en `toString`, para que no salga en logs.
 */
data class DetectedSignal(val type: SignalType, val ruleId: String, val fragment: String) {
    override fun toString() = "DetectedSignal(type=$type, ruleId=$ruleId)"
}

/** Las señales de un mensaje y la versión de las reglas que las produjo (trazabilidad). */
data class SignalAnalysis(val rulesVersion: String, val signals: List<DetectedSignal>) {
    val types: Set<SignalType> get() = signals.mapTo(LinkedHashSet()) { it.type }

    fun has(type: SignalType): Boolean = signals.any { it.type == type }
}

interface SignalDetector {
    fun detect(message: String): SignalAnalysis
}
