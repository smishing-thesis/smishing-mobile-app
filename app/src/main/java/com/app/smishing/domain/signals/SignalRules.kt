package com.app.smishing.domain.signals

/** Una regla de urgencia o de solicitud de datos: un patrón que se busca en el texto ya normalizado. */
data class PatternRule(val id: String, val regex: Regex)

/**
 * Una entidad que un mensaje puede suplantar. [officialDomains] son los dominios que sí le pertenecen (un enlace a ellos
 * no es sospechoso; `gob.pe` cubre a los subdominios del Estado). Si [ambiguous], su nombre también es una palabra
 * común ("claro", "sat") y solo cuenta cuando va seguido de ":" o aparece dentro de un enlace.
 */
data class EntityRule(
    val id: String,
    val names: List<String>,
    val ambiguous: Boolean,
    val officialDomains: List<String>,
)

/**
 * Las reglas de `signal_rules.json`. Viven en el JSON, no en el código: cambiar una lista de palabras no debe exigir tocar
 * Kotlin.
 */
data class SignalRules(
    val version: String,
    val linkTlds: Set<String>,
    val urgency: List<PatternRule>,
    val dataRequest: List<PatternRule>,
    val entities: List<EntityRule>,
) {
    companion object {
        /** Versiones de las reglas que esta app sabe interpretar. */
        val SUPPORTED_VERSIONS = setOf("1.0.0", "1.1.0")
    }
}

/** El JSON de reglas está mal formado, tiene un patrón inválido o declara una versión desconocida. */
class InvalidSignalRulesException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
