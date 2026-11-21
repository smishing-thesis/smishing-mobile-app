package com.app.smishing.demo

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import java.io.ByteArrayOutputStream
import android.net.Uri
import android.provider.ContactsContract
import com.app.smishing.data.alerts.AndroidMessageNotifier
import com.app.smishing.data.history.HistoryDatabase
import com.app.smishing.data.sms.AndroidSmsRoleRepository
import com.app.smishing.data.sms.TelephonySmsInbox
import com.app.smishing.domain.sms.SmsRoleState
import com.app.smishing.data.sms.analyzeIncoming
import com.app.smishing.domain.sms.ReceiveIncomingSms
import com.app.smishing.domain.sms.ReceiveOutcome
import com.app.smishing.domain.sms.SmsPart
import java.io.File

/**
 * Herramientas de demostración (#65), solo en la variante `debug`. Entregan por el flujo real los mensajes de un archivo local
 * (`demo-messages.csv`, que se copia con `adb push` y no está en el repositorio) y los borran después.
 *
 * Nada de lo que lee se escribe en logs. Para guardar en el buzón la app tiene que ser la de SMS predeterminada.
 */
class DemoLoader(private val context: Context) {

    private val ids = File(context.filesDir, "demo-ids.txt")
    private val turnsFile = File(context.filesDir, "demo-turns.txt")
    private val contactsFile = File(context.filesDir, "demo-contacts.txt")

    /** Dónde se espera el archivo. */
    fun file(): File = File(context.getExternalFilesDir(null), "demo-messages.csv")

    /** Los mensajes del archivo, o el motivo por el que no se pudo leer. */
    fun read(): Result<List<DemoMessage>> = runCatching {
        val f = file()
        if (!f.exists()) throw DemoFileException("No se encontró el archivo")
        parseDemoCsv(f.readText(Charsets.UTF_8))
    }

    /** Cuántos mensajes de demo hay cargados ahora. */
    fun loaded(): Int = if (ids.exists()) ids.readLines().count { it.isNotBlank() } else 0

    /**
     * Entrega **un** mensaje de demo del tipo [label] (`smishing`, `legitimo`...) como recibido ahora, por el mismo camino que un SMS
     * real: se guarda una vez en el buzón, se analiza, se guarda en el historial y avisa o actúa según el modo de mitigación. De a uno,
     * para poder ver llegar el mensaje y su aviso. Recorre los mensajes de ese tipo en orden, sin repetir hasta dar toda la vuelta; en
     * qué mensaje va cada tipo se guarda (`demo-turns.txt`), así que sigue igual al salir de la pantalla o cerrar la app, y vuelve al
     * primero al borrar los mensajes de demo.
     */
    fun deliverNext(messages: List<DemoMessage>, label: String, nowMillis: Long = System.currentTimeMillis()) {
        requireDefaultSmsApp()
        val pool = messages.filter { it.label == label }
        check(pool.isNotEmpty()) { "El archivo no tiene mensajes de tipo $label" }
        val turn = turns()[label] ?: 0
        val index = turn % pool.size
        deliver(pool[index].sender, pool[index].body, nowMillis)
        saveTurns(turns() + (label to turn + 1))
    }

    /** Si ya se agregaron los contactos de ejemplo. */
    fun hasSampleContacts(): Boolean = contactsFile.exists()

    /**
     * Agrega al teléfono unos contactos **inventados** y entrega un mensaje (inventado, legítimo) de cada uno, para ver cómo salen los
     * nombres en la lista y en las conversaciones. Los números son de ejemplo, no de nadie. Hace falta `WRITE_CONTACTS` (solo en debug)
     * y que la app sea la de SMS predeterminada. Para ver los nombres, la app también necesita `READ_CONTACTS`.
     */
    fun addSampleContacts(nowMillis: Long = System.currentTimeMillis()) {
        requireDefaultSmsApp()
        check(!hasSampleContacts()) { "Los contactos de ejemplo ya están agregados" }
        val rawIds = ArrayList<Long>()
        for (c in SAMPLE_CONTACTS) {
            val ops = arrayListOf<ContentProviderOperation>(
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null).build(),
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, c.name).build(),
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, c.number)
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE).build(),
            )
            // Solo algunos llevan foto: así se ve la diferencia con la imagen por defecto.
            c.photoColor?.let { color ->
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, samplePhoto(c.name.first().toString(), color)).build(),
                )
            }
            val uri = context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops).first().uri
            rawIds.add(ContentUris.parseId(checkNotNull(uri)))
        }
        contactsFile.writeText(rawIds.joinToString("\n"))
        SAMPLE_CONTACTS.forEachIndexed { i, c -> deliver(c.number, c.text, nowMillis - (SAMPLE_CONTACTS.size - i) * 60_000L) }
    }

    /** Guarda un mensaje en el buzón y lo analiza, como uno real; recuerda su id para poder borrarlo. */
    private fun deliver(sender: String, body: String, nowMillis: Long) {
        val result = ReceiveIncomingSms(TelephonySmsInbox(context)).receive(listOf(SmsPart(sender, body, nowMillis)))
        val id = result.messageId
        val message = result.message
        check(result.outcome == ReceiveOutcome.Stored && id != null && message != null) { "El mensaje no se pudo entregar" }
        remember(id)
        analyzeIncoming(context, id, message)
    }

    /** Una foto de ejemplo inventada: un fondo de color con la inicial del nombre, en PNG. */
    private fun samplePhoto(initial: String, color: Int): ByteArray {
        val size = 256
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(color)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = 0xFFFFFFFF.toInt()
            textSize = 140f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(initial, size / 2f, size / 2f - (paint.descent() + paint.ascent()) / 2f, paint)
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    /** Quita los contactos de ejemplo. Sin el permiso de escribir contactos no puede, y los deja. */
    private fun deleteSampleContacts() {
        if (!contactsFile.exists()) return
        try {
            for (raw in contactsFile.readLines().mapNotNull { it.trim().toLongOrNull() }) {
                val uri = ContactsContract.RawContacts.CONTENT_URI.buildUpon()
                    .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build()
                context.contentResolver.delete(uri, "${ContactsContract.RawContacts._ID} = ?", arrayOf(raw.toString()))
            }
            contactsFile.delete()
        } catch (_: SecurityException) {
            // Sin WRITE_CONTACTS: se quedan; se pueden borrar a mano desde Contactos.
        }
    }

    /**
     * Quita del buzón los mensajes que cargó esta herramienta, con sus registros del historial (y sus acciones, que se borran en
     * cascada) y sus avisos. No toca los mensajes recibidos o enviados de verdad ni **desbloquea** los números que el modo
     * automático haya bloqueado. Devuelve cuántos quitó.
     */
    fun deleteAll(): Int {
        val loadedIds = if (ids.exists()) ids.readLines().mapNotNull { it.trim().toLongOrNull() } else emptyList()
        val dao = HistoryDatabase.get(context).dao()
        var removed = 0
        for (id in loadedIds) {
            // El modo automático puede haber analizado el mismo mensaje más de una vez; se borran todos sus registros.
            while (true) {
                val record = dao.byMessageId(id) ?: break
                dao.delete(record.message.id)
            }
            removed += context.contentResolver.delete(ContentUris.withAppendedId(Uri.parse("content://sms"), id), null, null)
        }
        AndroidMessageNotifier.dismiss(context, loadedIds)
        ids.delete()
        turnsFile.delete()
        deleteSampleContacts()
        return removed
    }

    private fun remember(id: Long) = ids.appendText("$id\n")

    /** En qué mensaje va cada tipo: cuántos se entregaron de él. */
    private fun turns(): Map<String, Int> =
        if (turnsFile.exists()) {
            turnsFile.readLines().map { it.split('=', limit = 2) }.filter { it.size == 2 }.associate { it[0] to (it[1].toIntOrNull() ?: 0) }
        } else {
            emptyMap()
        }

    private fun saveTurns(turns: Map<String, Int>) =
        turnsFile.writeText(turns.entries.joinToString("\n") { "${it.key}=${it.value}" })

    // Se pregunta por el rol igual que lo hace la app (Inicio, Ajustes): `Telephony.Sms.getDefaultSmsPackage` no coincidía en el emulador con
    // Android 15 aunque la app tuviera el rol, y la demo decía que no era la predeterminada.
    private fun requireDefaultSmsApp() {
        check(AndroidSmsRoleRepository(context).state() == SmsRoleState.Held) { "La app no es la de SMS predeterminada" }
    }
}

private class SampleContact(val name: String, val number: String, val text: String, val photoColor: Int? = null)

/** Contactos y textos **inventados**: ningún nombre, número ni texto es de una persona real. */
private val SAMPLE_CONTACTS = listOf(
    SampleContact("Carla Ríos", "+51900000101", "Hola, ¿ya llegaste a casa?", 0xFF7B1FA2.toInt()),
    SampleContact("Mateo Vargas", "+51900000102", "Te paso la dirección en un rato."),
    SampleContact("Lucía Paredes", "+51900000103", "Gracias por ayer, quedó muy rico.", 0xFF00897B.toInt()),
    SampleContact("Mamá", "+51900000104", "Avísame cuando salgas.", 0xFFE64A19.toInt()),
    SampleContact("Tío Beto", "+51900000105", "Mañana paso a recogerte a las ocho."),
)
