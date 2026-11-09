package com.app.smishing.domain.signals

/**
 * Identifica las señales de un SMS (#8) con las reglas de `signal_rules.json`.
 *
 * Las reglas se aplican sobre una copia del mensaje del **mismo largo**, en minúsculas, sin tildes y con las sustituciones
 * comunes de quien ofusca ("8CP", "Y4pe"), de modo que cada posición del texto normalizado corresponde a la misma posición
 * del original. Así el fragmento que se muestra es siempre un trozo del mensaje tal como llegó.
 *
 * [homoglyphs] son los caracteres parecidos (cirílico, griego) que el preprocesamiento del modelo ya convierte en letras
 * latinas: se reutilizan para que "Ѕunat" se lea como "sunat".
 */
class RuleBasedSignalDetector(
    private val rules: SignalRules,
    private val homoglyphs: Map<Char, Char> = emptyMap(),
) : SignalDetector {

    private val linkRegexes: List<Regex> = buildLinkRegexes(rules.linkTlds)
    private val entityPatterns: List<Pair<EntityRule, Regex>> = rules.entities.map { it to entityRegex(it) }

    override fun detect(message: String): SignalAnalysis {
        val plain = foldText(message, leet = false, homoglyphs = homoglyphs)
        val leet = foldText(message, leet = true, homoglyphs = homoglyphs)

        val links = findLinks(plain)
        val urgency = firstMatches(rules.urgency, leet, message, SignalType.Urgency, "urgency")
        val dataRequest = firstMatches(rules.dataRequest, leet, message, SignalType.DataRequest, "data_request")
        val impersonation = findImpersonation(leet, message, links, hasDataRequest = dataRequest.isNotEmpty())
        val linkSignals = links.map { DetectedSignal(SignalType.Link, LINK_RULE_ID, message.substring(it.start, it.end)) }

        return SignalAnalysis(rules.version, urgency + impersonation + linkSignals + dataRequest)
    }

    /** La primera coincidencia de cada regla: una señal por regla, no una por cada vez que aparece. */
    private fun firstMatches(patterns: List<PatternRule>, folded: String, original: String, type: SignalType, prefix: String): List<DetectedSignal> =
        patterns.mapNotNull { rule ->
            rule.regex.find(folded)?.let { DetectedSignal(type, "$prefix.${rule.id}", original.substring(it.range.first, it.range.last + 1)) }
        }

    private fun findImpersonation(leet: String, original: String, links: List<Link>, hasDataRequest: Boolean): List<DetectedSignal> =
        entityPatterns.mapNotNull { (entity, regex) ->
            val mention = regex.findAll(leet).firstOrNull { match ->
                !entity.ambiguous || followedByColon(leet, match.range.last + 1) || links.any { match.range.first >= it.start && match.range.last < it.end }
            } ?: return@mapNotNull null
            // Nombrar una entidad no es suplantarla: lo es si el mensaje lleva a otro sitio o pide datos.
            val leadsElsewhere = links.any { link -> entity.officialDomains.none { link.isFrom(it) } }
            if (!leadsElsewhere && !hasDataRequest) return@mapNotNull null
            DetectedSignal(SignalType.Impersonation, "impersonation.${entity.id}", original.substring(mention.range.first, mention.range.last + 1))
        }

    private fun followedByColon(text: String, from: Int): Boolean {
        var i = from
        while (i < text.length && text[i].isWhitespace()) i++
        return i < text.length && text[i] == ':'
    }

    private class Link(val start: Int, val end: Int, val host: String) {
        fun isFrom(domain: String) = host == domain || host.endsWith(".$domain")
    }

    /** Los enlaces del texto, sin los que se solapan: se queda el más largo. Las posiciones son las del mensaje original. */
    private fun findLinks(plain: String): List<Link> {
        val spans = linkRegexes.flatMap { regex -> regex.findAll(plain).map { it.range.first to trimEnd(plain, it.range.first, it.range.last + 1) }.toList() }
            .filter { it.second > it.first }
            .sortedWith(compareBy({ it.first }, { -(it.second - it.first) }))
        val result = mutableListOf<Link>()
        var lastEnd = -1
        for ((start, end) in spans) {
            if (start < lastEnd) continue
            result += Link(start, end, hostOf(plain.substring(start, end)))
            lastEnd = end
        }
        return result
    }

    /** Quita la puntuación que cierra la frase ("…ejemplo.com." o "(…)"), que no es parte del enlace. */
    private fun trimEnd(text: String, start: Int, end: Int): Int {
        var e = end
        while (e > start && text[e - 1] in TRAILING_PUNCTUATION) e--
        return e
    }

    private fun hostOf(url: String): String {
        val withoutScheme = url.substringAfter("://", url)
        val authority = withoutScheme.takeWhile { it != '/' && it != '?' && it != '#' }
        return authority.substringAfterLast('@').substringBefore(':')
    }

    private companion object {
        const val LINK_RULE_ID = "link.url"
        const val TRAILING_PUNCTUATION = ".,;:!?)]}'\"»"

        fun buildLinkRegexes(tlds: Set<String>): List<Regex> {
            val tldAlternation = tlds.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
            return listOf(
                // Con esquema o con "www.": cualquier cosa hasta el siguiente espacio.
                Regex("(?:https?://|www\\.)[^\\s]+"),
                // Un dominio suelto ("bit.ly/3kfj2", "interbank.pe"), que no sea parte de un correo ni de otra palabra.
                Regex("(?<![@\\w.-])(?:[a-z0-9][a-z0-9-]*\\.)+(?:$tldAlternation)(?![a-z0-9-])(?:[/?#][^\\s]*)?"),
            )
        }

        /** Un nombre de entidad como patrón. Los nombres largos también se reconocen separados ("I n t e r b a n k"). */
        fun entityRegex(entity: EntityRule): Regex {
            val variants = entity.names.flatMap { name ->
                val words = name.split(' ').joinToString("\\s+") { Regex.escape(it) }
                val spaced = if (!name.contains(' ') && name.length >= 5) listOf(name.map { Regex.escape(it.toString()) }.joinToString("[\\s.*_-]{0,2}")) else emptyList()
                listOf(words) + spaced
            }
            return Regex("(?<![a-z0-9])(?:${variants.joinToString("|")})(?![a-z0-9])")
        }
    }
}

private val ACCENTS = mapOf(
    'á' to 'a', 'à' to 'a', 'â' to 'a', 'ä' to 'a',
    'é' to 'e', 'è' to 'e', 'ê' to 'e', 'ë' to 'e',
    'í' to 'i', 'ì' to 'i', 'î' to 'i', 'ï' to 'i',
    'ó' to 'o', 'ò' to 'o', 'ô' to 'o', 'ö' to 'o',
    'ú' to 'u', 'ù' to 'u', 'û' to 'u', 'ü' to 'u',
    'ñ' to 'n',
)

/** Lo que alguien que ofusca pone en lugar de una letra: "cu3nta", "bl0queada", "8CP", "Y4pe". */
private val LEET = mapOf('0' to 'o', '3' to 'e', '4' to 'a', '5' to 's', '7' to 't', '8' to 'b', '@' to 'a', '$' to 's')

/** Minúsculas, sin tildes, con los homoglifos pasados a letras latinas y, si [leet], con las sustituciones comunes. Mismo largo. */
internal fun foldText(text: String, leet: Boolean, homoglyphs: Map<Char, Char>): String {
    val out = StringBuilder(text.length)
    for (c in text) {
        var ch = (homoglyphs[c] ?: c).lowercaseChar()
        ch = ACCENTS[ch] ?: ch
        if (leet) ch = LEET[ch] ?: ch
        out.append(ch)
    }
    return out.toString()
}

/** Los homoglifos de un solo carácter de la tabla del modelo (`preprocessing_config.json`), para [RuleBasedSignalDetector]. */
fun Map<String, String>.toHomoglyphs(): Map<Char, Char> =
    entries.filter { it.key.length == 1 && it.value.length == 1 }.associate { it.key[0] to it.value[0] }
