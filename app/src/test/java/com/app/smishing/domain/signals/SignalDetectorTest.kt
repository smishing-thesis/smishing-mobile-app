package com.app.smishing.domain.signals

/** #8 en la JVM: el contrato del detector con los archivos como recursos. */
class SignalDetectorTest : SignalDetectorContract() {

    override fun rulesJson() = resource("signal_rules.json")
    override fun preprocessingJson() = resource("preprocessing_config.json")
    override fun controlledCsv() = resource("controlled_messages.csv")

    private fun resource(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) { "Falta el recurso $name" }
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
}
