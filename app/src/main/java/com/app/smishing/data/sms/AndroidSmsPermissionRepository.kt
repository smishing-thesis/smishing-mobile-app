package com.app.smishing.data.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.app.smishing.domain.sms.SmsPermissionRepository
import com.app.smishing.domain.sms.SmsPermissionState

/**
 * Permisos de SMS de la app. Son solo los que el análisis necesita:
 * - `READ_SMS`: leer los mensajes (el "permiso de lectura" de la pantalla del paso 3).
 * - `RECEIVE_SMS`: sin él, Android no entrega a la app los SMS entrantes (comprobado en API 29: el sistema omite el receptor).
 * `SEND_SMS` no se pide aquí: es de #6.
 */
class AndroidSmsPermissionRepository(private val context: Context) : SmsPermissionRepository {

    override fun state(): SmsPermissionState =
        if (missing().isEmpty()) SmsPermissionState.Granted else SmsPermissionState.NotGranted

    /** Los permisos que faltan, para pedirlos. */
    fun missing(): List<String> = REQUIRED.filter {
        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
    }

    companion object {
        val REQUIRED = listOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
    }
}
