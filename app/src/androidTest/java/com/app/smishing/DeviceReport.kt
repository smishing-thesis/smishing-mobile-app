package com.app.smishing

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Datos del dispositivo y escritura de reportes de las pruebas instrumentadas. Los reportes no llevan mensajes. */
object DeviceReport {

    fun deviceInfo(): JSONObject = JSONObject()
        .put("manufacturer", Build.MANUFACTURER)
        .put("model", Build.MODEL)
        .put("androidRelease", Build.VERSION.RELEASE)
        .put("sdkInt", Build.VERSION.SDK_INT)
        .put("abis", JSONArray(Build.SUPPORTED_ABIS.toList()))

    /** Modo avión y permiso de internet: el respaldo de "sin conexión". Se registran, no se exigen. */
    fun environmentInfo(context: Context): JSONObject {
        val permissions = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        return JSONObject()
            .put("airplaneModeOn", Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1)
            .put("declaresInternetPermission", "android.permission.INTERNET" in permissions)
    }

    // AGP recoge additionalTestOutputDir tras la prueba; si no existe, queda en la carpeta externa de la app.
    fun write(context: Context, fileName: String, report: JSONObject, tag: String) {
        val dir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File)
            ?: checkNotNull(context.getExternalFilesDir(null))
        dir.mkdirs()
        File(dir, fileName).writeText(report.toString(2))
        Log.i(tag, "Reporte escrito en ${File(dir, fileName)}")
    }
}
