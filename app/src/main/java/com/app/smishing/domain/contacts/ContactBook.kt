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
class ContactBook(
    private val names: Map<String, String> = emptyMap(),
    /** El número tal como se guardó, por la misma clave que [names]: sirve para escribirlo en "Nuevo mensaje". */
    private val numbers: Map<String, String> = emptyMap(),
    /** La foto de cada contacto (un URI del sistema), por la misma clave; solo los que tienen foto. */
    private val photos: Map<String, String> = emptyMap(),
) {

    /** El nombre del contacto con ese número, o `null` si no hay (o no hay permiso, y la libreta está vacía). */
    fun nameOf(address: String): String? = keyOf(address)?.let(names::get)

    /**
     * Los nombres de los contactos, sin repetir. Solo para quitarlos de un texto antes de compartirlo (#19); no deben mostrarse ni
     * guardarse.
     */
    fun displayNames(): List<String> = names.values.distinct()

    /** El URI de la foto del contacto con ese número, o `null` si no hay contacto o no tiene foto. */
    fun photoOf(address: String): String? = keyOf(address)?.let(photos::get)

    /** El nombre del contacto o, si no lo hay, el remitente tal cual, como hoy. */
    fun display(address: String): String = nameOf(address) ?: address

    /**
     * Los contactos cuyo nombre contiene [query] (sin distinguir mayúsculas ni tildes), como (nombre, número), para sugerirlos al
     * escribir el destinatario. Solo busca con 2 letras o más; no pasa de [limit].
     */
    fun search(query: String, limit: Int = 5): List<Pair<String, String>> {
        val q = foldForSearch(query.trim())
        if (q.length < 2) return emptyList()
        return names.entries.filter { foldForSearch(it.value).contains(q) }
            .sortedBy { foldForSearch(it.value) }
            .take(limit)
            .mapNotNull { (key, name) -> numbers[key]?.let { name to it } }
    }

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
        fun of(entries: List<Pair<String, String>>): ContactBook = ofContacts(entries.map { ContactEntry(it.first, it.second, null) })

        /** Como [of], con la foto de cada contacto si la tiene. */
        fun ofContacts(entries: List<ContactEntry>): ContactBook {
            val names = LinkedHashMap<String, String>()
            val numbers = LinkedHashMap<String, String>()
            val photos = LinkedHashMap<String, String>()
            for (entry in entries) {
                val key = keyOf(entry.number) ?: continue
                if (entry.name.isNotBlank() && names.putIfAbsent(key, entry.name.trim()) == null) {
                    numbers[key] = entry.number.trim()
                    entry.photoUri?.takeIf { it.isNotBlank() }?.let { photos[key] = it }
                }
            }
            return ContactBook(names, numbers, photos)
        }
    }
}

/** El texto en minúsculas y sin tildes, para comparar al buscar: "María" y "maria" son lo mismo. */
fun foldForSearch(text: String): String =
    java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD).filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }.lowercase()

/** Una fila de contactos: número, nombre y foto. Es de la persona: `toString` no lo muestra. */
class ContactEntry(val number: String, val name: String, val photoUri: String?) {
    override fun toString() = "ContactEntry(<oculto>)"
}
