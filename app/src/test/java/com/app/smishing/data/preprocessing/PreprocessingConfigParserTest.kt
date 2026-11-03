package com.app.smishing.data.preprocessing

import com.app.smishing.domain.preprocessing.UnsupportedPreprocessingException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** El JSON declara la versión del preprocesamiento: la app la registra y rechaza las que no conoce. */
class PreprocessingConfigParserTest {

    private val configJson: String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("preprocessing_config.json"))
            .bufferedReader(Charsets.UTF_8).use { it.readText() }

    @Test
    fun readsTheVersionFromTheJson() {
        val config = PreprocessingConfigParser.parse(configJson)
        assertEquals("1.1.0", config.version)
        assertEquals("2", config.normalizationVersion)
        assertEquals(160, config.maxLength)
        assertEquals(15, config.minCleanedChars)
    }

    @Test
    fun rejectsAnUnknownPreprocessingVersion() {
        val json = JSONObject(configJson).apply { getJSONObject("preprocessing").put("version", "9.9.9") }
        assertThrows(UnsupportedPreprocessingException::class.java) {
            PreprocessingConfigParser.parse(json.toString())
        }
    }

    @Test
    fun rejectsAnUnknownNormalizationVersion() {
        val json = JSONObject(configJson).apply { getJSONObject("normalization").put("version", "3") }
        assertThrows(UnsupportedPreprocessingException::class.java) {
            PreprocessingConfigParser.parse(json.toString())
        }
    }
}
