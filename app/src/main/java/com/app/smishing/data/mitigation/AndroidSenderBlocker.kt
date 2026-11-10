package com.app.smishing.data.mitigation

import android.content.ContentValues
import android.content.Context
import android.provider.BlockedNumberContract
import com.app.smishing.domain.mitigation.SenderBlocker

/**
 * Bloquea remitentes con la lista de números bloqueados del sistema (`BlockedNumberContract`, #14). Solo la app de mensajería
 * predeterminada, la de teléfono o la del operador pueden escribirla: sin ese rol [isAvailable] es `false` y no se intenta.
 *
 * No pide ningún permiso de manifiesto: el acceso lo da el rol.
 */
class AndroidSenderBlocker(private val context: Context) : SenderBlocker {

    override fun isAvailable(): Boolean = BlockedNumberContract.canCurrentUserBlockNumbers(context)

    override fun isBlocked(sender: String): Boolean = BlockedNumberContract.isBlocked(context, sender)

    override fun block(sender: String): Boolean {
        val values = ContentValues().apply { put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, sender) }
        val row = context.contentResolver.insert(BlockedNumberContract.BlockedNumbers.CONTENT_URI, values)
        return row != null && isBlocked(sender)
    }

    override fun unblock(sender: String): Boolean {
        BlockedNumberContract.unblock(context, sender)
        return !isBlocked(sender)
    }
}
