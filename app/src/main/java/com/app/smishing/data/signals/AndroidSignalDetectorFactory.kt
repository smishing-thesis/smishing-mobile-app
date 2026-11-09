package com.app.smishing.data.signals

import android.content.Context
import com.app.smishing.data.preprocessing.PreprocessingConfigLoader
import com.app.smishing.domain.signals.RuleBasedSignalDetector
import com.app.smishing.domain.signals.toHomoglyphs

/** Arma el detector de señales con las reglas empaquetadas en los assets y los homoglifos del modelo. */
object AndroidSignalDetectorFactory {

    const val RULES_ASSET = "signal_rules.json"

    fun create(context: Context): RuleBasedSignalDetector {
        val json = context.assets.open(RULES_ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val homoglyphs = PreprocessingConfigLoader(context).load().confusables.toHomoglyphs()
        return RuleBasedSignalDetector(SignalRulesParser.parse(json), homoglyphs)
    }
}
