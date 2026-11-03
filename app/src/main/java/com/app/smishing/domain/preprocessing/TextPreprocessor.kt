package com.app.smishing.domain.preprocessing

import java.text.Normalizer
import java.util.Locale
import java.util.regex.Matcher

/**
 * Convierte un SMS en la entrada del modelo: limpieza, normalización v2, enmascarado y codificación.
 * Replica el preprocesamiento de Python; la fuente de verdad es el JSON, no este código.
 */
class TextPreprocessor(private val config: PreprocessingConfig) {

    private val cleaningSteps: List<(String) -> String> = config.cleaningSteps.map { step ->
        when (step) {
            is CleaningStep.RegexReplace -> regexStep(step.pattern, step.replacement)
            is CleaningStep.Translate -> translateStep(step.mapping)
        }
    }

    private val maskingSteps: List<(String) -> String> = config.maskingRules.map { rule ->
        when {
            rule.pattern == null && rule.name == "lowercase" -> { text: String -> text.lowercase(Locale.ROOT) }
            rule.pattern != null && rule.replacement != null -> regexStep(rule.pattern, rule.replacement)
            else -> throw UnsupportedPreprocessingException("Regla de enmascarado desconocida: ${rule.name}")
        }
    }

    fun preprocess(message: String): PreprocessingResult {
        val cleaned = clean(message)
        val normalized = normalize(cleaned)
        val masked = mask(normalized)
        val cleanedChars = cleaned.codePointCount(0, cleaned.length)
        return PreprocessingResult(
            preprocessingVersion = config.version,
            cleaned = cleaned,
            normalized = normalized,
            masked = masked,
            cleanedChars = cleanedChars,
            classify = cleanedChars >= config.minCleanedChars,
            inputIds = encode(masked),
        )
    }

    private fun clean(text: String): String = strip(cleaningSteps.fold(text) { acc, step -> step(acc) })

    private fun normalize(text: String): String {
        val nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC)
        val mapped = mapCodePoints(nfkc) { config.confusables[it] ?: it }
        return mapCodePoints(mapped) { char -> if (isKept(char)) char else " " }
    }

    private fun mask(text: String): String = strip(maskingSteps.fold(text) { acc, step -> step(acc) })

    private fun encode(text: String): List<Int> {
        val ids = MutableList(config.maxLength) { config.padIndex }
        var position = 0
        var i = 0
        while (i < text.length && position < config.maxLength) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            ids[position++] = config.charToIndex[cpToString(cp)] ?: config.unknownIndex
        }
        return ids
    }

    // Se conserva el carácter si su minúscula está en el alfabeto, o si es dígito o espacio (como en Python).
    private fun isKept(char: String): Boolean {
        val cp = char.codePointAt(0)
        return PythonCompat.isSpace(cp) || PythonCompat.isDigit(cp) ||
            char.lowercase(Locale.ROOT) in config.modelAlphabet
    }

    private fun strip(text: String): String = text.trim { PythonCompat.isSpace(it.code) }

    private fun regexStep(pattern: String, replacement: String): (String) -> String {
        val compiled = PythonCompat.compile(pattern)
        val quoted = Matcher.quoteReplacement(replacement)
        return { text -> compiled.matcher(text).replaceAll(quoted) }
    }

    private fun translateStep(mapping: Map<String, String>): (String) -> String =
        { text -> mapCodePoints(text) { mapping[it] ?: it } }

    private fun mapCodePoints(text: String, transform: (String) -> String): String {
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            out.append(transform(cpToString(cp)))
        }
        return out.toString()
    }

    private fun cpToString(cp: Int): String = String(Character.toChars(cp))
}
