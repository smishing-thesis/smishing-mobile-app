package com.app.smishing.domain.risk

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.app.smishing.ControlledMessages
import com.app.smishing.DeviceReport
import com.app.smishing.data.classification.OnDeviceClassifier
import com.app.smishing.data.signals.AndroidSignalDetectorFactory
import com.app.smishing.domain.classification.ClassificationResult
import com.app.smishing.domain.signals.SignalType
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * #9 de principio a fin en el teléfono: el modelo real (LiteRT) y las reglas de señales reales sobre los 84 mensajes
 * sintéticos del conjunto controlado, con la política de riesgo. Cada nivel debe ser el que dicen la salida del modelo y las
 * señales, y debe llevar todo lo necesario para trazarlo. Escribe un reporte sin los mensajes.
 *
 * Qué tan bien separan los niveles las dos clases es una **medición**, no una exigencia: se registra, y no se ajusta la
 * política a estos mensajes.
 */
@RunWith(AndroidJUnit4::class)
class RiskAssessmentInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun cases(): List<ControlledMessages.Case> =
        ControlledMessages.parse(
            InstrumentationRegistry.getInstrumentation().context.assets.open("controlled_messages.csv")
                .bufferedReader(Charsets.UTF_8).use { it.readText() },
        )

    /** El nivel que corresponde a la salida del modelo y a las señales, calculado aparte de la política. */
    private fun expectedLevel(modelFlags: Boolean, types: Set<SignalType>): RiskLevel {
        val backing = types.count { it == SignalType.Urgency || it == SignalType.Impersonation || it == SignalType.DataRequest }
        return when {
            modelFlags && backing >= 1 -> RiskLevel.High
            modelFlags -> RiskLevel.Medium
            SignalType.Impersonation in types || backing >= 2 -> RiskLevel.Medium
            else -> RiskLevel.Low
        }
    }

    @Test
    fun theModelAndTheSignalsProduceATraceableLevelForEveryControlledMessage() {
        val cases = cases()
        val perCategory = sortedMapOf<String, MutableMap<String, Int>>()
        var assessed = 0
        var notAssessed = 0
        val legitimateAboveLow = mutableListOf<String>()
        val smishingLow = mutableListOf<String>()
        var modelFlagsLegitimate = 0
        var modelMissesSmishing = 0
        var modelRows = 0
        val results = JSONArray()

        OnDeviceClassifier.create(context).use { classifier ->
            val assessor = RiskAssessor(classifier, AndroidSignalDetectorFactory.create(context))
            for (case in cases) {
                val key = when (val result = assessor.assess(case.message)) {
                    is RiskResult.NotAssessed -> {
                        notAssessed++
                        assertEquals("Solo se deja sin nivel por ser muy corto: ${case.id}", ClassificationResult.NotClassified.Reason.TOO_SHORT, result.reason)
                        "NotAssessed"
                    }
                    is RiskResult.Assessed -> {
                        assessed++
                        val a = result.assessment
                        // Trazabilidad: todo lo que explica y repite la decisión.
                        assertEquals("1.0.0", a.model.modelVersion)
                        assertEquals("1.1.0", a.model.preprocessingVersion)
                        assertEquals(0.9034745693206787, a.model.threshold, 0.0)
                        assertEquals("1.1.0", a.signals.rulesVersion)
                        assertEquals(RiskPolicy.VERSION, a.policyVersion)
                        assertEquals(a.rule.level, a.level)
                        assertTrue(a.rule.id.startsWith("risk."))
                        assertEquals(a.model.isSmishing, a.model.score.toDouble() > a.model.threshold)
                        assertTrue(a.evidence.all { it in RiskPolicy.EVIDENCE_TYPES })
                        // El nivel es el que dicen el modelo y las señales.
                        assertEquals(case.id, expectedLevel(a.model.isSmishing, a.signals.types), a.level)

                        modelRows++
                        if (case.label == "legitimate" && a.model.isSmishing) modelFlagsLegitimate++
                        if (case.label == "smishing" && !a.model.isSmishing) modelMissesSmishing++
                        if (case.label == "legitimate" && a.level != RiskLevel.Low) legitimateAboveLow += "${case.id}:${a.level}"
                        if (case.label == "smishing" && a.level == RiskLevel.Low) smishingLow += case.id
                        results.put(
                            JSONObject().put("id", case.id).put("category", case.category).put("label", case.label)
                                .put("level", a.level.name).put("rule", a.rule.id).put("score", a.model.score.toDouble())
                                .put("signals", JSONArray(a.signals.types.map { it.name })),
                        )
                        a.level.name
                    }
                }
                perCategory.getOrPut(case.category) { sortedMapOf() }.merge(key, 1, Int::plus)
            }
        }

        assertEquals(cases.size, assessed + notAssessed)
        assertTrue("Debía haber mensajes con nivel", assessed > 0)

        val report = JSONObject()
            .put("issue", 9)
            .put("generatedAt", Instant.now().toString())
            .put("device", DeviceReport.deviceInfo())
            .put("policyVersion", RiskPolicy.VERSION)
            .put("messages", cases.size)
            .put("assessed", assessed)
            .put("notAssessed", notAssessed)
            .put("modelFlagsLegitimate", modelFlagsLegitimate)
            .put("modelMissesSmishing", modelMissesSmishing)
            .put("legitimateAboveLow", JSONArray(legitimateAboveLow))
            .put("smishingAtLow", JSONArray(smishingLow))
            .put("levelsPerCategory", JSONObject(perCategory.mapValues { JSONObject(it.value as Map<*, *>) }))
            .put("cases", results)
        DeviceReport.write(context, "risk-level-controlled.json", report, "RiskLevel")
    }
}
