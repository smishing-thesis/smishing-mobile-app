package com.app.smishing.data.sharing

import com.app.smishing.domain.sharing.Contribution
import com.app.smishing.domain.sharing.ContributionLabel
import com.app.smishing.domain.sharing.ContributionSendResult
import com.app.smishing.domain.sharing.ItemOutcome
import com.app.smishing.domain.sharing.ItemResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** El envío al backend con un transporte falso: lo que se pide, a dónde y con qué cabeceras, y cómo reacciona a cada fallo. Sin red. */
class HttpContributionSenderTest {

    private val batch = listOf(Contribution("Mensaje sintético", "1.3.0", ContributionLabel.Smishing, "1.0.0"))
    private val config = BackendConfig("https://localhost:8443/", "clave-de-prueba")

    private class FakeTransport(private val answer: () -> HttpResponse) : HttpTransport {
        var calls = 0
        var url: String? = null
        var headers: Map<String, String> = emptyMap()
        var body: ByteArray = ByteArray(0)
        override fun post(url: String, headers: Map<String, String>, body: ByteArray): HttpResponse {
            calls++
            this.url = url
            this.headers = headers
            this.body = body
            return answer()
        }
    }

    private fun ok() = HttpResponse(200, """{"results":[{"index":0,"status":"accepted"}]}""", null)

    @Test
    fun itPostsTheBatchToTheBatchRouteOfTheBackendWithTheKeyAndJson() {
        val transport = FakeTransport(::ok)
        val result = HttpContributionSender(config, transport).send(batch)
        assertEquals(ContributionSendResult.Delivered(listOf(ItemResult(ItemOutcome.Accepted))), result)
        // La barra final de la dirección no duplica la del camino.
        assertEquals("https://localhost:8443/api/v1/contributions/batch", transport.url)
        assertEquals("clave-de-prueba", transport.headers["X-API-Key"])
        assertEquals("application/json; charset=utf-8", transport.headers["Content-Type"])
        assertTrue(String(transport.body, Charsets.UTF_8).contains("\"model_version\":\"1.0.0\""))
    }

    @Test
    fun withoutAddressOrKeyOrWithoutHttpsNothingIsSentAndNothingIsAskedFromTheNetwork() {
        for (config in listOf(
            BackendConfig("", "clave"),
            BackendConfig("https://localhost:8443", ""),
            BackendConfig("http://localhost:8443", "clave"),
        )) {
            val transport = FakeTransport(::ok)
            assertEquals(ContributionSendResult.NotConfigured, HttpContributionSender(config, transport).send(batch))
            assertEquals(0, transport.calls)
        }
    }

    @Test
    fun noConnectionOrACertificateProblemIsRetried() {
        val transport = FakeTransport { throw IOException("sin conexión") }
        assertEquals(ContributionSendResult.Retry(), HttpContributionSender(config, transport).send(batch))
    }

    @Test
    fun somethingUnexpectedIsNotRetriedForever() {
        val transport = FakeTransport { throw IllegalStateException("inesperado") }
        assertEquals(ContributionSendResult.Failed, HttpContributionSender(config, transport).send(batch))
    }

    @Test
    fun theStatusOfTheAnswerDecidesWhatHappens() {
        assertEquals(ContributionSendResult.Unauthorized, HttpContributionSender(config, FakeTransport { HttpResponse(401, "", null) }).send(batch))
        assertEquals(ContributionSendResult.Retry(10), HttpContributionSender(config, FakeTransport { HttpResponse(429, "", "10") }).send(batch))
        assertEquals(ContributionSendResult.Retry(), HttpContributionSender(config, FakeTransport { HttpResponse(503, "", null) }).send(batch))
    }

    @Test
    fun theKeyAndTheTextNeverShowInTheStringForms() {
        assertFalse("clave-de-prueba" in config.toString())
        assertFalse("clave-de-prueba" in HttpResponse(200, "cuerpo con texto", null).toString())
        assertFalse("cuerpo con texto" in HttpResponse(200, "cuerpo con texto", null).toString())
        assertFalse("Mensaje sintético" in batch.single().toString())
    }
}
