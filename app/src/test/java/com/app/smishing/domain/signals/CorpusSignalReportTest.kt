package com.app.smishing.domain.signals

import com.app.smishing.data.preprocessing.PreprocessingConfigParser
import com.app.smishing.data.signals.SignalRulesParser
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Mide las señales sobre el corpus real anonimizado (`sms-anonymizer/data/labeled/final.csv`). **No corre por defecto**: solo
 * si la variable de entorno `SMISHING_CORPUS_CSV` apunta al CSV (columnas `text,label`).
 *
 * El corpus real nunca va al repositorio y este informe tampoco lleva texto: escribe solo conteos y, por mensaje, su índice y
 * qué señales y reglas se activaron (`app/build/reports/corpus-signals.json`, que no se versiona). El corpus ya viene
 * anonimizado: sus enlaces son `<URL>`, los nombres, `<NAMED_ENTITY>`, y los teléfonos, `<PHONE_NUMBER>`. Aquí se
 * reemplazan por un enlace, una palabra neutra y un número, para que las reglas vean un mensaje parecido al original. Eso
 * significa que **los dominios y los nombres de entidad no se pueden medir con este corpus**.
 */
class CorpusSignalReportTest {

    @Test
    fun reportSignalsOverTheCorpus() {
        val path = System.getenv("SMISHING_CORPUS_CSV")
        assumeTrue("Define SMISHING_CORPUS_CSV para medir con el corpus real", !path.isNullOrBlank())

        val detector = detector()
        val messages = uniqueMessages(File(path!!).readText(Charsets.UTF_8).removePrefix("﻿"))

        val report = StringBuilder("[\n")
        val summary = linkedMapOf<String, Int>()
        messages.forEachIndexed { index, (text, label) ->
            val analysis = detector.detect(restoreAnonymized(text))
            val name = if (label == "1") "scam" else "ham"
            summary.merge("$name.n", 1, Int::plus)
            for (type in analysis.types) summary.merge("$name.${type.name}", 1, Int::plus)
            if ((analysis.types - SignalType.Link).isNotEmpty()) summary.merge("$name.AnyBesidesLink", 1, Int::plus)
            for (signal in analysis.signals.map { it.ruleId }.toSet()) summary.merge("$name.rule.$signal", 1, Int::plus)
            report.append("""  {"i":$index,"label":"$label","types":[${analysis.types.joinToString(",") { "\"${it.name}\"" }}],"rules":[${analysis.signals.map { it.ruleId }.distinct().joinToString(",") { "\"$it\"" }}]}""")
            report.append(if (index < messages.size - 1) ",\n" else "\n")
        }
        report.append("]\n")

        val dir = File("build/reports").apply { mkdirs() }
        File(dir, "corpus-signals.json").writeText(report.toString())
        File(dir, "corpus-signals-summary.txt").writeText(summary.entries.joinToString("\n") { "${it.key}=${it.value}" })
        summary.forEach { println("CORPUS ${it.key}=${it.value}") }
    }

    private fun detector(): RuleBasedSignalDetector {
        val homoglyphs = PreprocessingConfigParser.parse(resource("preprocessing_config.json")).confusables.toHomoglyphs()
        return RuleBasedSignalDetector(SignalRulesParser.parse(resource("signal_rules.json")), homoglyphs)
    }

    /** Los mensajes sin repetir (como en la doc del modelo), en el orden del CSV, con su etiqueta. */
    private fun uniqueMessages(csv: String): List<Pair<String, String>> {
        val seen = HashSet<String>()
        return parseCsv(csv).drop(1).filter { it.size >= 2 }.map { it[0] to it[1] }.filter { seen.add(it.first) }
    }

    private fun restoreAnonymized(text: String) = text
        .replace("<URL>", "http://enlace.example.test/a")
        .replace("<PHONE_NUMBER>", "987654321")
        .replace("<NAMED_ENTITY>", "xxxx")
        .replace("<EMAIL_ADDRESS>", "correo-x")
        .replace("<ID_NUMBER>", "12345678")

    private fun resource(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) { "Falta el recurso $name" }.bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                !inQuotes && c == ',' -> { row.add(field.toString()); field.clear() }
                !inQuotes && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row.add(field.toString()); field.clear()
                    if (row.any { it.isNotEmpty() }) rows.add(row)
                    row = mutableListOf()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); rows.add(row) }
        return rows
    }
}
