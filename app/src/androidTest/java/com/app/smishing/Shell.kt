package com.app.smishing

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry

/** Ejecuta un comando de shell durante una prueba instrumentada y devuelve su salida. */
object Shell {
    fun run(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
    }
}
