package com.app.smishing.data.classification

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.app.smishing.DeviceReport
import com.app.smishing.domain.classification.ClassificationResult
import com.app.smishing.domain.classification.ClassificationResult.Classified
import com.app.smishing.domain.classification.ClassificationResult.NotClassified
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * #7: clasificar un SMS de principio a fin en el teléfono, sin red. Usa los 52 mensajes sintéticos del reporte
 * de Keras: la decisión debe coincidir con la de Keras y la política de entrada debe frenar los mensajes cortos.
 * Escribe un reporte sin los mensajes.
 */
@RunWith(AndroidJUnit4::class)
class OnDeviceClassifierInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private class Case(
        val id: String,
        val category: String,
        val message: String,
        val kerasSmishing: Boolean,
        val shouldBeClassified: Boolean,
    )

    @Test
    fun endToEndClassificationMatchesKerasAndTheInputPolicy() {
        val cases = loadCases()
        var classified = 0
        var notClassified = 0
        var decisionMismatches = 0
        var policyMismatches = 0
        var modelVersion = ""
        var preprocessingVersion = ""
        val results = JSONArray()

        OnDeviceClassifier.create(context).use { classifier ->
            for (case in cases) {
                val entry = JSONObject().put("id", case.id).put("category", case.category)
                when (val result = classifier.classify(case.message)) {
                    is Classified -> {
                        classified++
                        modelVersion = result.modelVersion
                        preprocessingVersion = result.preprocessingVersion
                        val match = result.isSmishing == case.kerasSmishing
                        if (!match) decisionMismatches++
                        if (!case.shouldBeClassified) policyMismatches++
                        entry.put("outcome", "classified")
                            .put("score", result.score.toDouble())
                            .put("smishing", result.isSmishing)
                            .put("kerasSmishing", case.kerasSmishing)
                            .put("decisionMatches", match)
                    }
                    is NotClassified -> {
                        notClassified++
                        if (case.shouldBeClassified) policyMismatches++
                        entry.put("outcome", "not_classified").put("reason", result.reason.name)
                    }
                }
                results.put(entry)
            }
        }

        val report = JSONObject()
            .put("issue", 7)
            .put("generatedAt", Instant.now().toString())
            .put("device", DeviceReport.deviceInfo())
            .put("environment", DeviceReport.environmentInfo(context))
            .put("modelVersion", modelVersion)
            .put("preprocessingVersion", preprocessingVersion)
            .put(
                "summary",
                JSONObject()
                    .put("cases", cases.size)
                    .put("classified", classified)
                    .put("notClassified", notClassified)
                    .put("decisionMismatchesVsKeras", decisionMismatches)
                    .put("inputPolicyMismatches", policyMismatches),
            )
            .put("cases", results)
        DeviceReport.write(context, REPORT_NAME, report, TAG)

        assertEquals("Versión del modelo en el resultado", "1.0.0", modelVersion)
        assertEquals("Versión del preprocesamiento en el resultado", "1.1.0", preprocessingVersion)
        assertEquals("Mensajes que la política de entrada trató distinto a lo esperado", 0, policyMismatches)
        assertEquals("Decisiones distintas a Keras", 0, decisionMismatches)
    }

    @Test
    fun concurrentCallsGiveTheSameResultsAsSequentialOnes() {
        val cases = loadCases()
        OnDeviceClassifier.create(context).use { classifier ->
            val expected = cases.associate { it.id to classifier.classify(it.message) }

            val pool = Executors.newFixedThreadPool(4)
            try {
                val tasks = (1..3).flatMap { cases.shuffled() }.map { case ->
                    Callable<Pair<String, ClassificationResult>> { case.id to classifier.classify(case.message) }
                }
                val different = pool.invokeAll(tasks).map { it.get() }.filter { (id, result) -> result != expected.getValue(id) }
                assertTrue("${different.size} resultados distintos bajo llamadas simultáneas", different.isEmpty())
            } finally {
                pool.shutdownNow()
            }
        }
    }

    // Los 52 casos del reporte de Keras. La política de entrada viene de los vectores; los 14 casos que solo
    // están en el reporte son mensajes largos y deben clasificarse.
    private fun loadCases(): List<Case> {
        val parity = JSONObject(readAsset("parity_report.json")).getJSONArray("cases")
        val vectors = JSONObject(readAsset("preprocessing_vectors.json")).getJSONArray("cases")
        val shouldClassify = (0 until vectors.length()).associate {
            val vector = vectors.getJSONObject(it)
            vector.getString("id") to vector.getBoolean("classify")
        }
        return List(parity.length()) {
            val case = parity.getJSONObject(it)
            Case(
                id = case.getString("id"),
                category = case.getString("category"),
                message = case.getString("message"),
                kerasSmishing = case.getBoolean("keras_smishing"),
                shouldBeClassified = shouldClassify[case.getString("id")] ?: true,
            )
        }
    }

    private fun readAsset(name: String): String =
        instrumentation.context.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }

    private companion object {
        const val TAG = "OnDeviceClassifier"
        const val REPORT_NAME = "local_classification_report.json"
    }
}
