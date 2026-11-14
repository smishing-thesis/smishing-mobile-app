package com.app.smishing.domain.contacts

/**
 * Los nombres de los contactos del teléfono, por número (#53). Solo sirve para **mostrar**: los nombres no se guardan en el historial
 * ni en ninguna base de la app, y no salen del teléfono. Se arma al leer los contactos y se descarta. El contenido es de la persona:
 * `toString` no lo muestra.
 *
 * Dos números son el mismo si coinciden en sus últimos 9 dígitos: así "+51 987 654 321" y "987654321" son la misma persona, sea cual
 * sea el código de país con que se guardó. Un remitente sin al menos 7 dígitos (un código corto como 88800 o un nombre como "ONPE")
 * no se busca en los contactos: no es un número de persona.
 */
class ContactBook(private val names: Map<String, String> = emptyMap()) {

    /** El nombre del contacto con ese número, o `null` si no hay (o no hay permiso, y la libreta está vacía). */
    fun nameOf(address: String): String? = keyOf(address)?.let(names::get)

    /** El nombre del contacto o, si no lo hay, el remitente tal cual, como hoy. */
    fun display(address: String): String = nameOf(address) ?: address

    override fun toString() = "ContactBook(<oculto>)"

    companion object {
        private const val MIN_DIGITS = 7
        private const val KEY_DIGITS = 9

        /** La clave con que se comparan los números, o `null` si el remitente no parece un número de persona. */
        fun keyOf(address: String): String? {
            val digits = address.filter(Char::isDigit)
            return if (digits.length < MIN_DIGITS) null else digits.takeLast(KEY_DIGITS)
        }

        /** Arma la libreta con pares (número, nombre). Si un número está en más de un contacto, gana el primero. */
        fun of(entries: List<Pair<String, String>>): ContactBook {
            val names = LinkedHashMap<String, String>()
            for ((number, name) in entries) {
                val key = keyOf(number) ?: continue
                if (name.isNotBlank()) names.putIfAbsent(key, name.trim())
            }
            return ContactBook(names)
        }
    }
}
