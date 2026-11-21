package com.app.smishing.data.sharing

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

/**
 * Solo en depuración: el backend local usa un certificado de prueba que el emulador no conoce. Si existe el archivo
 * `dev-backend-cert.pem` (solo la parte **pública** del certificado) en la carpeta de archivos de la app, esa conexión confía en él; sin el
 * archivo, se usan las autoridades del sistema, como siempre. En `qa` y `release` esta clase no hace nada (ver docs/aporte-al-corpus.md).
 *
 * ```
 * adb push cert.pem /storage/emulated/0/Android/data/com.app.smishing/files/dev-backend-cert.pem
 * adb reverse tcp:8443 tcp:8443
 * ```
 */
object BackendTls {
    private const val CERT_FILE = "dev-backend-cert.pem"

    fun apply(context: Context, connection: HttpURLConnection) {
        if (connection !is HttpsURLConnection) return
        val file = File(context.getExternalFilesDir(null), CERT_FILE)
        if (!file.exists()) return
        val certificate = file.inputStream().use { CertificateFactory.getInstance("X.509").generateCertificate(it) }
        val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("dev-backend", certificate)
        }
        val trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(store) }
        val tls = SSLContext.getInstance("TLS").apply { init(null, trust.trustManagers, null) }
        connection.sslSocketFactory = tls.socketFactory
    }
}
