package com.app.smishing.data.classification

import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.app.smishing.data.preprocessing.PreprocessingConfigLoader
import com.app.smishing.domain.preprocessing.TextPreprocessor
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
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
            .put("device", deviceInfo())
            .put("environment", environmentInfo())
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
        writeReport(report)

        assertEquals("Decisiones distintas a Keras en el dispositivo", 0, decisionMismatches)
    }

    private fun deviceInfo() = JSONObject()
        .put("manufacturer", Build.MANUFACTURER)
        .put("model", Build.MODEL)
        .put("androidRelease", Build.VERSION.RELEASE)
        .put("sdkInt", Build.VERSION.SDK_INT)
        .put("abis", JSONArray(Build.SUPPORTED_ABIS.toList()))

    private fun environmentInfo(): JSONObject {
        val permissions = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        return JSONObject()
            .put("airplaneModeOn", Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1)
            .put("declaresInternetPermission", "android.permission.INTERNET" in permissions)
    }

    // AGP recoge additionalTestOutputDir tras la prueba; si no existe, queda en la carpeta externa de la app.
    private fun writeReport(report: JSONObject) {
        val dir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File)
            ?: checkNotNull(context.getExternalFilesDir(null))
        dir.mkdirs()
        File(dir, REPORT_NAME).writeText(report.toString(2))
        Log.i(TAG, "Reporte escrito en ${File(dir, REPORT_NAME)}")
    }

    private companion object {
        const val TAG = "LiteRtParity"
        const val REPORT_NAME = "parity_device_report.json"
    }
}
