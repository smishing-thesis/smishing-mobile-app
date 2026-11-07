package com.app.smishing.data.classification

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.app.smishing.DeviceReport
import com.app.smishing.data.preprocessing.PreprocessingConfigLoader
import com.app.smishing.domain.preprocessing.TextPreprocessor
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import kotlin.math.abs

/**
 * #34: el modelo LiteRT del teléfono debe decidir igual que Keras sobre los casos de `parity_report.json`.
 * Escribe un reporte con el dispositivo, el modelo y las diferencias de puntaje; no incluye los mensajes.
 */
@RunWith(AndroidJUnit4::class)
class LiteRtParityInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val loader = PreprocessingConfigLoader(context)

    @Test
    fun modelLoadsAndResponds() {
        LiteRtModel.load(context, loader.loadModelInfo()).use { model ->
            val input = TextPreprocessor(loader.load())
                .preprocess("Tu codigo de verificacion es 458920. No lo compartas con nadie.")
            val score = model.score(input.inputIds)
            assertTrue("Puntaje fuera de [0, 1]: $score", score in 0f..1f)
        }
    }

    @Test
    fun decisionsMatchKerasOnDevice() {
        val info = loader.loadModelInfo()
        val preprocessor = TextPreprocessor(loader.load())
        val parity = JSONObject(
            instrumentation.context.assets.open("parity_report.json").bufferedReader(Charsets.UTF_8).use { it.readText() },
        )
        assertEquals("El reporte de Keras es de otra versión del modelo", info.version, parity.getString("model_version"))
        assertEquals("El reporte de Keras es de otro .tflite", info.tfliteSha256, parity.getString("tflite_sha256"))
        assertEquals(info.threshold, parity.getDouble("threshold"), 0.0)

        val cases = parity.getJSONArray("cases")
        val results = JSONArray()
        var decisionMismatches = 0
        var maxDiff = 0.0
        var sumDiff = 0.0
        var maxDiffVsTflite = 0.0

        val model: ModelDescription = LiteRtModel.load(context, info).use { litert ->
            for (index in 0 until cases.length()) {
                val case = cases.getJSONObject(index)
                val id = case.getString("id")
                val score = litert.score(preprocessor.preprocess(case.getString("message")).inputIds)
                val smishing = info.isSmishing(score)
                val diff = abs(score - case.getDouble("keras_score"))
                val match = smishing == case.getBoolean("keras_smishing")

                if (!match) decisionMismatches++
                maxDiff = maxOf(maxDiff, diff)
                sumDiff += diff
                maxDiffVsTflite = maxOf(maxDiffVsTflite, abs(score - case.getDouble("tflite_score")))
                Log.i(TAG, "$id score=$score keras=${case.getDouble("keras_score")} diff=$diff smishing=$smishing match=$match")

                results.put(
                    JSONObject()
                        .put("id", id)
                        .put("category", case.getString("category"))
                        .put("deviceScore", score.toDouble())
                        .put("kerasScore", case.getDouble("keras_score"))
                        .put("tfliteDesktopScore", case.getDouble("tflite_score"))
                        .put("absDiffVsKeras", diff)
                        .put("deviceSmishing", smishing)
                        .put("kerasSmishing", case.getBoolean("keras_smishing"))
                        .put("decisionMatches", match),
                )
            }
            litert.description
        }

        val report = JSONObject()
            .put("issue", 34)
            .put("generatedAt", Instant.now().toString())
            .put("device", DeviceReport.deviceInfo())
            .put("environment", DeviceReport.environmentInfo(context))
            .put(
                "model",
                JSONObject()
                    .put("version", info.version)
                    .put("threshold", info.threshold)
                    .put("sizeBytes", model.sizeBytes)
                    .put("sha256", model.sha256)
                    .put("input", JSONObject().put("type", model.inputType).put("shape", JSONArray(model.inputShape)))
                    .put("output", JSONObject().put("type", model.outputType).put("shape", JSONArray(model.outputShape))),
            )
            .put(
                "summary",
                JSONObject()
                    .put("cases", cases.length())
                    .put("decisionMismatchesVsKeras", decisionMismatches)
                    .put("maxAbsDiffVsKeras", maxDiff)
                    .put("meanAbsDiffVsKeras", sumDiff / cases.length())
                    .put("maxAbsDiffVsTfliteDesktop", maxDiffVsTflite),
            )
            .put("cases", results)
        DeviceReport.write(context, REPORT_NAME, report, TAG)

        assertEquals("Decisiones distintas a Keras en el dispositivo", 0, decisionMismatches)
    }

    private companion object {
        const val TAG = "LiteRtParity"
        const val REPORT_NAME = "parity_device_report.json"
    }
}
