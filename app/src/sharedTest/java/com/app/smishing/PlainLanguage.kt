package com.app.smishing

/**
 * Las reglas de lenguaje del detalle y de la alerta (#11): palabras corrientes, y la sospecha nunca como certeza. Las comparten
 * la prueba de las cadenas (JVM) y la funcional del dispositivo, que revisa lo que de verdad se ve en pantalla.
 *
 * Son reglas mínimas y mecánicas: **no prueban que el texto se entienda**. Eso lo evalúa #23 con personas.
 */
object PlainLanguage {

    /** Frases que presentan la sospecha como un hecho o la probabilidad como certeza. */
    val CERTAINTY = listOf(
        Regex("peligros", RegexOption.IGNORE_CASE) to "«peligroso» presenta la sospecha como un hecho",
        Regex("\\bes una estafa\\b", RegexOption.IGNORE_CASE) to "«es una estafa» afirma lo que no se sabe",
        Regex("\\bfinge\\b|\\bfingen\\b", RegexOption.IGNORE_CASE) to "«finge» afirma una intención que no se conoce",
        Regex("\\bengañ", RegexOption.IGNORE_CASE) to "«engaño» afirma una intención",
        Regex("con certeza|sin duda|seguramente|definitiv|\\bcien por ciento\\b", RegexOption.IGNORE_CASE) to "expresa certeza",
        Regex("\\bes seguro\\b|totalmente seguro|mensaje seguro", RegexOption.IGNORE_CASE) to "dice que el mensaje es seguro",
        Regex("\\d+\\s?%|\\d+[.,]\\d+") to "muestra un porcentaje o un puntaje",
    )

    /** Palabras de la técnica que la persona no tiene por qué conocer ("sin conocer el modelo"). */
    val TECHNICAL = listOf(
        "modelo", "algoritmo", "umbral", "puntaje", "probabilidad", "porcentaje", "inteligencia artificial",
        "neuronal", "score", "regla", "redes neuronales",
    )

    /** Cuántas palabras como máximo en una oración: las largas se leen mal en una pantalla pequeña. */
    const val MAX_WORDS_PER_SENTENCE = 25

    /** Lo que incumple el [text]; una lista vacía significa que cumple. */
    fun violations(text: String): List<String> {
        val found = mutableListOf<String>()
        for ((regex, why) in CERTAINTY) if (regex.containsMatchIn(text)) found += "«$text»: $why"
        val lower = text.lowercase()
        for (word in TECHNICAL) if (Regex("\\b${Regex.escape(word)}").containsMatchIn(lower)) found += "«$text»: usa «$word», que es jerga técnica"
        for (sentence in text.split(Regex("[.!?:]+"))) {
            val words = sentence.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.size > MAX_WORDS_PER_SENTENCE) found += "«$text»: una oración de ${words.size} palabras"
        }
        return found
    }
}
