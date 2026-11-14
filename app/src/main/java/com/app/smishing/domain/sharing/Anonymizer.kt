package com.app.smishing.domain.sharing

import java.text.Normalizer
import java.util.regex.Pattern

/**
 * Las reglas de desidentificación (#19), **las mismas del repo `sms-anonymizer`** (`rules.py`, versión [VERSION]), que son el contrato que
 * esta implementación debe cumplir: `anonymization_vectors.json` se genera con el anonimizador de referencia y las pruebas de la JVM y del
 * dispositivo comprueban que la app da lo mismo. Si cambia una regla allá, se sube la versión, se regeneran los vectores y se ajusta esto.
 *
 * **Lo que no está:** el reconocimiento de nombres de persona con un modelo de lenguaje (NER, `person_name` de la referencia) no cabe en un
 * teléfono de gama baja. En su lugar, los nombres se quitan con la lista de nombres que se le da al [Anonymizer] (la app usa los nombres de
 * los contactos). **Un nombre que no esté en esa lista no se detecta**, y ninguna comprobación puede saberlo.
 */
object AnonymizationRules {
    /** Se sube cada vez que cambia el patrón o el marcador de una regla; debe ser la de `sms-anonymizer`. */
    const val VERSION = "1.3.0"

    const val NAMED_ENTITY = "<NAMED_ENTITY>"
    const val KNOWN_NAMES_PRIORITY = 3

    /** Un nombre más corto que esto no se usa: dañaría el texto corriente ("de", "la"). Igual que la referencia. */
    const val MIN_NAME_LENGTH = 3

    /**
     * Compila como lo hace Python (`re.IGNORECASE` sobre `str` es Unicode, y `\w`, `\d` y `\b` también). En la JVM hay que pedirlo con
     * `UNICODE_CHARACTER_CLASS`; **Android no admite ese indicador** (lanza «Unsupported flags: 256») porque su motor ya usa Unicode por
     * defecto, así que allí se compila sin él. Lo comprueba la prueba de vectores en la JVM y en el dispositivo.
     */
    fun compileLikePython(regex: String): Pattern {
        val base = Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
        return try {
            Pattern.compile(regex, base or Pattern.UNICODE_CHARACTER_CLASS)
        } catch (_: IllegalArgumentException) {
            Pattern.compile(regex, base)
        }
    }

    class Rule(val name: String, val placeholder: String, val priority: Int, regex: String, val valueGroup: String? = null) {
        val pattern: Pattern = compileLikePython(regex)
    }

    /** En el orden de la referencia: así los empates entre reglas se resuelven igual. */
    val REGEX_RULES: List<Rule> = listOf(
        Rule("url", "<URL>", 2, """https?://\S+|\b(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.)+[a-zA-Z]{2,}(?:/\S*)?"""),
        Rule("email", "<EMAIL_ADDRESS>", 3, """[\w.+-]+@[\w-]+\.[\w.-]+"""),
        Rule("phone_pe", "<PHONE_NUMBER>", 2, """(?:\+?51[\s.-]?)?9\d{2}[\s.-]?\d{3}[\s.-]?\d{3}\b"""),
        // Solo se reemplazan los dígitos (grupo "value"); la palabra RUC o DNI se queda.
        Rule("id_number_pe", "<ID_NUMBER>", 3, """\b(?:ruc|dni)\b\W{0,3}(?:n(?:ro|°|º|\.)?\W{0,3})?(?<value>\d{11}|\d{8})\b""", valueGroup = "value"),
    )
}

/** Lo que hace falta para desidentificar un texto y comprobar el resultado (#19). Es lo que usa [PrepareContribution]. */
interface TextAnonymizer {
    fun anonymize(text: String): Anonymized

    /** Si en [text] ya no queda nada que una regla reconozca. */
    fun isAnonymized(text: String): Boolean
}

/** El resultado de desidentificar un texto. Lleva el texto: no debe escribirse en logs. */
data class Anonymized(val text: String, val rulesVersion: String, val replacements: Int) {
    override fun toString() = "Anonymized(rulesVersion=$rulesVersion, replacements=$replacements)"
}

/**
 * Quita de un SMS los identificadores directos, en el teléfono, antes de que pueda compartirse (#19): enlaces, correos, teléfonos de Perú,
 * RUC o DNI escritos tras la palabra, y los nombres de [knownNames] (la app da los de los contactos). Cada uno se cambia por su marcador
 * (`<URL>`, `<EMAIL_ADDRESS>`, `<PHONE_NUMBER>`, `<ID_NUMBER>`, `<NAMED_ENTITY>`). Cuando dos reglas se pisan, gana la de mayor prioridad.
 */
class Anonymizer(knownNames: Collection<String> = emptyList()) : TextAnonymizer {

    private val namesPattern: Pattern? = compileKnownNames(knownNames)

    override fun anonymize(text: String): Anonymized {
        val spans = spansOf(text)
        if (spans.isEmpty()) return Anonymized(text, AnonymizationRules.VERSION, 0)
        val out = StringBuilder()
        var cursor = 0
        for (span in spans) {
            out.append(text, cursor, span.start).append(span.placeholder)
            cursor = span.end
        }
        out.append(text, cursor, text.length)
        return Anonymized(out.toString(), AnonymizationRules.VERSION, spans.size)
    }

    /**
     * Si en [text] ya no queda nada que una regla reconozca. Es la comprobación que rechaza un ejemplo no desidentificado: se corre sobre el
     * resultado, así que también atrapa un fallo de [anonymize]. No puede saber de un nombre que no esté en la lista.
     */
    override fun isAnonymized(text: String): Boolean = spansOf(text).isEmpty()

    private class Span(val start: Int, val end: Int, val placeholder: String, val priority: Int)

    private fun spansOf(text: String): List<Span> {
        val found = mutableListOf<Span>()
        for (rule in AnonymizationRules.REGEX_RULES) {
            val matcher = rule.pattern.matcher(text)
            while (matcher.find()) {
                val group = rule.valueGroup
                val start = if (group == null) matcher.start() else matcher.start(group)
                val end = if (group == null) matcher.end() else matcher.end(group)
                found += Span(start, end, rule.placeholder, rule.priority)
            }
        }
        namesPattern?.matcher(text)?.let { matcher ->
            while (matcher.find()) {
                found += Span(matcher.start(), matcher.end(), AnonymizationRules.NAMED_ENTITY, AnonymizationRules.KNOWN_NAMES_PRIORITY)
            }
        }
        return mergeSpans(found)
    }

    /** Los de más prioridad reclaman su rango primero; uno de menos prioridad que lo pise se descarta. Igual que la referencia. */
    private fun mergeSpans(spans: List<Span>): List<Span> {
        val accepted = mutableListOf<Span>()
        for (span in spans.sortedWith(compareBy<Span>({ -it.priority }, { it.start }))) {
            if (accepted.none { span.start < it.end && it.start < span.end }) accepted += span
        }
        return accepted.sortedBy { it.start }
    }

    companion object {
        /**
         * Los nombres de contacto como lista de nombres sueltos para quitar: cada palabra de [displayNames] de al menos
         * [AnonymizationRules.MIN_NAME_LENGTH] letras. Quita de más antes que de menos (un contacto llamado "Casa" borra también "casa").
         */
        fun fromContactNames(displayNames: Collection<String>): Anonymizer =
            Anonymizer(displayNames.flatMap { it.split(Regex("\\s+")) }.filter { it.length >= AnonymizationRules.MIN_NAME_LENGTH }.distinct())

        // Cada vocal se vuelve una clase para comparar sin acentos sin cambiar el largo del texto (como la referencia).
        private val ACCENT_CLASSES = mapOf('a' to "[aáàâä]", 'e' to "[eéèêë]", 'i' to "[iíìîï]", 'o' to "[oóòôö]", 'u' to "[uúùûü]")

        private fun stripAccents(text: String) =
            Normalizer.normalize(text, Normalizer.Form.NFD).filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }

        private fun namePattern(name: String) =
            stripAccents(name).lowercase().map { ACCENT_CLASSES[it] ?: Pattern.quote(it.toString()) }.joinToString("")

        /** Palabra entera, sin distinguir mayúsculas ni acentos; varios nombres seguidos separados por espacios forman un solo tramo. */
        private fun compileKnownNames(names: Collection<String>): Pattern? {
            val unique = names.map { it.trim() }.filter { it.length >= AnonymizationRules.MIN_NAME_LENGTH }.distinct().sortedByDescending { it.length }
            if (unique.isEmpty()) return null
            val alternatives = unique.joinToString("|") { namePattern(it) }
            return AnonymizationRules.compileLikePython("(?<!\\w)(?:$alternatives)(?:[ \\t]+(?:$alternatives))*(?!\\w)")
        }
    }
}
