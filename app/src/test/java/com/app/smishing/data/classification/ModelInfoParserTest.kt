package com.app.smishing.data.classification

import com.app.smishing.domain.classification.ModelInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/** Datos del modelo que el JSON declara, y comprobación de que el `.tflite` empaquetado es ese modelo. */
class ModelInfoParserTest {

    private val info: ModelInfo = ModelInfoParser.parse(resource("preprocessing_config.json").decodeToString())

    @Test
    fun readsVersionThresholdAndHashFromTheJson() {
        assertEquals("1.0.0", info.version)
        assertEquals(0.9034745693206787, info.threshold, 0.0)
        assertEquals("dfe3fa4148942c1fdbfc7df416bc1bac0521f4dc80bb9d9f356eb95b542dc380", info.tfliteSha256)
    }

    @Test
    fun packagedModelHasTheHashDeclaredInTheJson() {
        val model = resource(LiteRtModel.ASSET_NAME)
        val sha256 = MessageDigest.getInstance("SHA-256").digest(model).joinToString("") { "%02x".format(it) }
        assertEquals(info.tfliteSha256, sha256)
        assertEquals(123544, model.size)
    }

    @Test
    fun decidesWithTheThresholdAndNotWithHalf() {
        assertTrue(info.isSmishing(0.95f))
        assertFalse(info.isSmishing(0.90f)) // justo por debajo del umbral
        assertFalse(info.isSmishing(0.60f)) // con 0.5 como corte sería sospechoso
    }

    @Test
    fun scoreEqualToTheThresholdIsNotSmishing() {
        assertFalse(info.isSmishing(info.threshold.toFloat()))
    }

    private fun resource(name: String): ByteArray =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) { "Falta el recurso $name" }
            .use { it.readBytes() }
}
