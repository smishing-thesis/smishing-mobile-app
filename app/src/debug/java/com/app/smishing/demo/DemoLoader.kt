package com.app.smishing.demo

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import com.app.smishing.data.alerts.AndroidMessageNotifier
import com.app.smishing.data.history.HistoryDatabase
import com.app.smishing.data.sms.TelephonySmsInbox
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
        val m = pool[index]
        val result = ReceiveIncomingSms(TelephonySmsInbox(context)).receive(listOf(SmsPart(m.sender, m.body, nowMillis)))
        val id = result.messageId
        val message = result.message
        check(result.outcome == ReceiveOutcome.Stored && id != null && message != null) { "El mensaje no se pudo entregar" }
        remember(id)
        saveTurns(turns() + (label to turn + 1))
        analyzeIncoming(context, id, message)
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

    private fun requireDefaultSmsApp() {
        check(Telephony.Sms.getDefaultSmsPackage(context) == context.packageName) { "La app no es la de SMS predeterminada" }
    }
}
