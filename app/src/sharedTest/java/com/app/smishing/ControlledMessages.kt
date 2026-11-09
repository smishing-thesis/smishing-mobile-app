package com.app.smishing

/**
 * El conjunto controlado de mensajes sintéticos (`controlled_messages.csv`, de `tests/fixtures` del repo del modelo): 84
 * mensajes con categoría y etiqueta ("legitimate" o "smishing"). Lo usan las pruebas de la JVM y las del dispositivo.
 */
object ControlledMessages {

    data class Case(val id: String, val category: String, val label: String, val message: String)

    fun parse(csv: String): List<Case> =
        parseCsv(csv.removePrefix("﻿")).drop(1).filter { it.size >= 4 }.map { Case(it[0], it[1], it[2], it[3]) }

    /** Un CSV con comillas, comas y comillas dobles escapadas; las filas pueden traer saltos de línea entre comillas. */
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
