package com.app.smishing.domain.preprocessing

import java.util.regex.Pattern

/**
 * Semántica de Python 3 (`str`) que Java no tiene por defecto. El modelo se entrenó con Python,
 * así que la app debe reproducirla carácter por carácter.
 */
internal object PythonCompat {

    // Lo que `str.isspace()` y `\s` aceptan en Python, incluidos U+001C a U+001F, que Java no cuenta.
    private const val SPACE_RANGES =
        """\u0009-\u000D\u001C- \u0085   -     　"""

    // `str.isdigit()` y no `isdecimal()` (Python 3.12, Unicode 15.0): superíndices, círculos, etc.
    // Los decimales (Nd) se detectan con Character.getType.
    private val EXTRA_DIGITS = listOf(
        0xB2..0xB3, 0xB9..0xB9, 0x1369..0x1371, 0x19DA..0x19DA, 0x2070..0x2070, 0x2074..0x2079,
        0x2080..0x2089, 0x2460..0x2468, 0x2474..0x247C, 0x2488..0x2490, 0x24EA..0x24EA,
        0x24F5..0x24FD, 0x24FF..0x24FF, 0x2776..0x277E, 0x2780..0x2788, 0x278A..0x2792,
        0x10A40..0x10A43, 0x10E60..0x10E68, 0x11052..0x1105A, 0x1F100..0x1F10A,
    )

    fun isSpace(cp: Int): Boolean =
        cp in 0x09..0x0D || cp in 0x1C..0x20 || cp == 0x85 || cp == 0xA0 || cp == 0x1680 ||
            cp in 0x2000..0x200A || cp == 0x2028 || cp == 0x2029 || cp == 0x202F || cp == 0x205F || cp == 0x3000

    fun isDigit(cp: Int): Boolean =
        Character.getType(cp) == Character.DECIMAL_DIGIT_NUMBER.toInt() || EXTRA_DIGITS.any { cp in it }

    // `\w` de Python: letras y números Unicode más el guion bajo.
    private const val WORD_RANGES = """\p{L}\p{N}_"""

    // `\b` de Python: frontera entre un carácter de palabra y otro que no lo es.
    private const val WORD_BOUNDARY = """(?:(?<=[$WORD_RANGES])(?![$WORD_RANGES])|(?<![$WORD_RANGES])(?=[$WORD_RANGES]))"""

    /**
     * Compila un patrón escrito para `re` de Python. Traduce `\s`, `\S`, `\v`, `\d`, `\w`, `\W` y `\b`,
     * que significan otra cosa en Java o dependen de la plataforma: la JVM los trata como ASCII y el ICU
     * de Android como Unicode, y Android rechaza UNICODE_CHARACTER_CLASS. Con la traducción explícita
     * ambos se comportan como Python.
     */
    fun compile(pythonPattern: String): Pattern {
        val out = StringBuilder()
        var inClass = false
        var i = 0
        while (i < pythonPattern.length) {
            val c = pythonPattern[i]
            if (c == '\\' && i + 1 < pythonPattern.length) {
                val next = pythonPattern[i + 1]
                when (next) {
                    's' -> out.append(if (inClass) SPACE_RANGES else "[$SPACE_RANGES]")
                    'S' -> out.append(negated(next, inClass, "[^$SPACE_RANGES]"))
                    'd' -> out.append("\\p{Nd}")
                    'w' -> out.append(if (inClass) WORD_RANGES else "[$WORD_RANGES]")
                    'W' -> out.append(negated(next, inClass, "[^$WORD_RANGES]"))
                    'b' -> out.append(if (inClass) "\\u0008" else WORD_BOUNDARY)
                    'v' -> out.append("\\u000B")
                    else -> out.append(c).append(next)
                }
                i += 2
                continue
            }
            if (c == '[' && !inClass) inClass = true else if (c == ']' && inClass) inClass = false
            out.append(c)
            i++
        }
        return Pattern.compile(out.toString())
    }

    private fun negated(escape: Char, inClass: Boolean, outsideClass: String): String {
        require(!inClass) { "\\$escape dentro de una clase de caracteres no está soportado" }
        return outsideClass
    }
}
