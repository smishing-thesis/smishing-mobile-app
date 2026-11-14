package com.app.smishing.data.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.app.smishing.domain.contacts.ContactBook

/**
 * Lee los contactos del teléfono (#53), con `READ_CONTACTS`. Sin el permiso, o si falla la lectura, devuelve una libreta vacía y la
 * app muestra los números, como antes. No se registra ningún nombre ni número.
 */
class AndroidContacts(private val context: Context) {

    /** Una libreta con todos los números de los contactos. Lee en el momento: no usar desde el hilo principal. */
    fun book(): ContactBook {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return ContactBook()
        }
        return try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),
                null,
                null,
                // Contactos con nombre primero y en orden estable: si un número está repetido, gana siempre el mismo.
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} ASC",
            )?.use { cursor ->
                ContactBook.of(
                    generateSequence { if (cursor.moveToNext()) cursor.getString(0).orEmpty() to cursor.getString(1).orEmpty() else null }.toList(),
                )
            } ?: ContactBook()
        } catch (_: SecurityException) {
            ContactBook()
        } catch (_: RuntimeException) {
            ContactBook()
        }
    }

    /**
     * El número del contacto elegido en el selector del sistema (el "+" de "Nuevo mensaje"). El selector entrega un permiso temporal
     * sobre esa fila, así que esto funciona **sin** `READ_CONTACTS`.
     */
    fun numberOf(pickedRow: Uri): String? = try {
        context.contentResolver.query(pickedRow, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    } catch (_: RuntimeException) {
        null
    }
}
