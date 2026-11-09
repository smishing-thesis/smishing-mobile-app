package com.app.smishing.domain.signals

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.app.smishing.data.signals.AndroidSignalDetectorFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * #8 en el dispositivo: el mismo contrato con el motor de expresiones regulares de Android y las reglas empaquetadas en la
 * app, que es lo que corre en el teléfono.
 */
@RunWith(AndroidJUnit4::class)
class SignalDetectorInstrumentedTest : SignalDetectorContract() {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    override fun rulesJson() = read(instrumentation.targetContext.assets.open("signal_rules.json"))
    override fun preprocessingJson() = read(instrumentation.targetContext.assets.open("preprocessing_config.json"))
    override fun controlledCsv() = read(instrumentation.context.assets.open("controlled_messages.csv"))

    private fun read(stream: java.io.InputStream) = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }

    @Test
    fun theFactoryBuildsTheDetectorFromThePackagedRules() {
        val detector = AndroidSignalDetectorFactory.create(instrumentation.targetContext)
        val analysis = detector.detect("BCP: Su cuenta será bloqueada. Actualice sus datos en http://bcp-seguro.info")
        assertEquals("1.1.0", analysis.rulesVersion)
        assertEquals(
            setOf(SignalType.Urgency, SignalType.Impersonation, SignalType.Link, SignalType.DataRequest),
            analysis.types,
        )
    }
}
