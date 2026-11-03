package com.app.smishing

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prueba funcional del entorno (issue #1): la app declara minSdk 29 y el runtime de LiteRT
 * está disponible en el dispositivo. No carga ningún modelo; eso corresponde a #34.
 */
@RunWith(AndroidJUnit4::class)
class LiteRtEnvironmentTest {

    @Test
    fun appDeclaresMinSdk29() {
        val info = InstrumentationRegistry.getInstrumentation().targetContext.applicationInfo
        assertEquals(29, info.minSdkVersion)
    }

    @Test
    fun interpreterApiIsOnClasspath() {
        Class.forName("org.tensorflow.lite.Interpreter")
    }

    @Test
    fun nativeRuntimeLoads() {
        System.loadLibrary("LiteRt")
    }
}
