package com.app.smishing.data.sharing

import com.app.smishing.domain.sharing.Contribution
import com.app.smishing.domain.sharing.ContributionSendResult
import com.app.smishing.domain.sharing.ContributionSender
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Dónde está el backend y con qué clave se le habla. [baseUrl] tiene que ser `https://`: sin eso, o sin clave, no se envía nada. Se arma
 * al compilar (ver `docs/aporte-al-corpus.md`); la clave no va en Git. La clave embebida en una app se puede extraer: no es la única
 * defensa del backend, y por eso no se escribe en ningún log ([toString] no la muestra).
 */
class BackendConfig(baseUrl: String, val apiKey: String) {
    val baseUrl: String = baseUrl.trim().trimEnd('/')
    val isConfigured: Boolean get() = this.baseUrl.startsWith("https://") && apiKey.isNotBlank()
    override fun toString() = "BackendConfig(configured=$isConfigured)"
}

/** Lo que contestó el servidor. [body] puede traer datos: no debe escribirse en logs. */
class HttpResponse(val status: Int, val body: String, val retryAfter: String?) {
    override fun toString() = "HttpResponse(status=$status)"
}

/** Hace una solicitud POST. Lanza [IOException] si no pudo (sin conexión, certificado no confiable, tiempo agotado). */
fun interface HttpTransport {
    @Throws(IOException::class)
    fun post(url: String, headers: Map<String, String>, body: ByteArray): HttpResponse
}

/**
 * El transporte con `HttpURLConnection`, sin librerías extra. No sigue redirecciones (un redireccionamiento llevaría la clave a otro
 * lado). [prepare] deja ajustar la conexión antes de usarla: solo lo usa la versión de depuración, para confiar en el certificado del
 * servidor local.
 */
class UrlConnectionTransport(private val prepare: (HttpURLConnection) -> Unit = {}) : HttpTransport {

    override fun post(url: String, headers: Map<String, String>, body: ByteArray): HttpResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.doOutput = true
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            prepare(connection)
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.use(::readLimited).orEmpty()
            return HttpResponse(status, text, connection.getHeaderField("Retry-After"))
        } finally {
            connection.disconnect()
        }
    }

    /** Lee a lo sumo [MAX_RESPONSE_BYTES]: una respuesta de este contrato es pequeña, y lo demás no se espera. */
    private fun readLimited(input: InputStream): String {
        val out = ByteArrayOutputStream()
        val chunk = ByteArray(4_096)
        while (out.size() < MAX_RESPONSE_BYTES) {
            val read = input.read(chunk)
            if (read < 0) break
            out.write(chunk, 0, read)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val MAX_RESPONSE_BYTES = 64 * 1024
    }
}

/**
 * El envío real de los aportes al backend (#72): un lote por solicitud (`POST /api/v1/contributions/batch`, HTTPS y `X-API-Key`). Sin
 * destino configurado no envía nada y todo se conserva. Una falla de red o del servidor se reintenta; una clave que no sirve o un lote
 * que no cumple el contrato, no (ver [BackendContract.interpret]).
 */
class HttpContributionSender(
    private val config: BackendConfig,
    private val transport: HttpTransport,
) : ContributionSender {

    override fun send(batch: List<Contribution>): ContributionSendResult {
        if (!config.isConfigured) return ContributionSendResult.NotConfigured
        val body = BackendContract.encode(batch)
        val headers = mapOf(
            "Content-Type" to "application/json; charset=utf-8",
            "Accept" to "application/json",
            "X-API-Key" to config.apiKey,
        )
        val response = try {
            transport.post(config.baseUrl + BATCH_PATH, headers, body)
        } catch (_: IOException) {
            return ContributionSendResult.Retry()
        } catch (_: RuntimeException) {
            // Algo inesperado al armar o hacer la solicitud: no se reintenta sin fin. El mensaje de la excepción no se registra.
            return ContributionSendResult.Failed
        }
        return BackendContract.interpret(response.status, response.body, response.retryAfter, batch.size)
    }

    companion object {
        const val BATCH_PATH = "/api/v1/contributions/batch"
    }
}
