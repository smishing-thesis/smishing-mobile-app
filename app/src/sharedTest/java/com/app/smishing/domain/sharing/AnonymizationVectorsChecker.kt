package com.app.smishing.domain.sharing

import org.json.JSONObject

/**
 * Compara la desidentificación de la app con `anonymization_vectors.json`, generado con el anonimizador de referencia (repo
 * `sms-anonymizer`) por `tools/generate_anonymization_vectors.py`. Lo usan las pruebas de la JVM y las del dispositivo.
 */
object AnonymizationVectorsChecker {

    /** Devuelve las discrepancias; una lista vacía significa que todos los casos coinciden. */
    fun check(vectorsJson: String): List<String> {
        val vectors = JSONObject(vectorsJson)
        val problems = mutableListOf<String>()
        if (vectors.getString("rules_version") != AnonymizationRules.VERSION) {
            problems += "versión: vectores ${vectors.getString("rules_version")} ≠ app ${AnonymizationRules.VERSION}"
        }
        val rules = vectors.getJSONArray("rules")
        for (i in 0 until rules.length()) {
            val rule = rules.getJSONObject(i)
            val mine = AnonymizationRules.REGEX_RULES.firstOrNull { it.name == rule.getString("name") }
            when {
                mine == null -> problems += "regla ${rule.getString("name")} no está en la app"
                mine.placeholder != rule.getString("placeholder") -> problems += "regla ${mine.name}: marcador distinto"
                mine.priority != rule.getInt("priority") -> problems += "regla ${mine.name}: prioridad distinta"
            }
        }
        if (rules.length() != AnonymizationRules.REGEX_RULES.size) {
            problems += "la app tiene ${AnonymizationRules.REGEX_RULES.size} reglas y los vectores ${rules.length()}"
        }

        val names = vectors.getJSONArray("known_names").let { array -> List(array.length()) { array.getString(it) } }
        val anonymizer = Anonymizer(names)
        val cases = vectors.getJSONArray("cases")
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val actual = anonymizer.anonymize(case.getString("text")).text
            if (actual != case.getString("expected")) {
                problems += "${case.getString("id")}: esperado «${case.getString("expected")}», obtenido «$actual»"
            }
        }
        return problems
    }

    fun caseCount(vectorsJson: String): Int = JSONObject(vectorsJson).getJSONArray("cases").length()
}
