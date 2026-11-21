package com.app.smishing.data.sharing

import android.content.Context
import java.net.HttpURLConnection

/** En `qa` y `release` no hay certificados de prueba: se confía solo en las autoridades del sistema. La versión de depuración confía además en el del servidor local. */
object BackendTls {
    @Suppress("UNUSED_PARAMETER")
    fun apply(context: Context, connection: HttpURLConnection) = Unit
}
