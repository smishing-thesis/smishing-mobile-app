package com.app.smishing.demo

/**
 * Un mensaje del archivo de demostración (#65). Lleva el texto de un mensaje real: no debe escribirse en logs ni en reportes, y por
 * eso su `toString` no lo muestra. [label] solo sirve para comprobar los resultados; la app no lo usa.
 */
data class DemoMessage(val sender: String, val body: String, val minutesAgo: Long, val label: String) {
    override fun toString() = "DemoMessage(minutesAgo=$minutesAgo, label=$label)"
}

/** El archivo no se pudo leer. El mensaje no incluye texto del archivo, que es privado. */
class DemoFileException(message: String) : Exception(message)

/**
 * Lee el CSV de demostración: columnas `sender`, `body`, `minutes_ago` y `label`, con comillas dobles para los textos que llevan
 * comas, saltos de línea o comillas (`""`). Lo escribió Python (UTF-8) y se lee como UTF-8, así que las tildes y la ñ se conservan.
 */
fun parseDemoCsv(text: String): List<DemoMessage> {
    val rows = ArrayList<List<String>>()
    var row = ArrayList<String>()
    val field = StringBuilder()
    var quoted = false
    var i = if (text.startsWith("\uFEFF")) 1 else 0
    while (i < text.length) {
        val c = text[i]
        when {
            quoted && c == '"' && text.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
            quoted && c == '"' -> quoted = false
            quoted -> field.append(c)
            c == '"' -> quoted = true
            c == ',' -> { row.add(field.toString()); field.setLength(0) }
            c == '\r' -> Unit
            c == '\n' -> { row.add(field.toString()); field.setLength(0); rows.add(row); row = ArrayList() }
            else -> field.append(c)
        }
        i++
    }
    if (quoted) throw DemoFileException("Hay unas comillas sin cerrar")
    if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); rows.add(row) }

    val header = rows.firstOrNull() ?: throw DemoFileException("El archivo está vacío")
    val sender = header.indexOf("sender")
    val body = header.indexOf("body")
    val minutes = header.indexOf("minutes_ago")
    val label = header.indexOf("label")
    if (sender < 0 || body < 0 || minutes < 0) throw DemoFileException("Faltan columnas: sender, body y minutes_ago")
    return rows.drop(1).filter { it.isNotEmpty() && it.any(String::isNotBlank) }.mapIndexed { index, r ->
        val age = r.getOrNull(minutes)?.trim()?.toLongOrNull() ?: throw DemoFileException("Fila ${index + 2}: minutes_ago no es un número")
        val from = r.getOrNull(sender)?.trim().orEmpty()
        val text = r.getOrNull(body).orEmpty()
        if (from.isEmpty() || text.isEmpty()) throw DemoFileException("Fila ${index + 2}: falta el remitente o el texto")
        DemoMessage(from, text, age, r.getOrNull(label)?.trim().orEmpty())
    }
}
