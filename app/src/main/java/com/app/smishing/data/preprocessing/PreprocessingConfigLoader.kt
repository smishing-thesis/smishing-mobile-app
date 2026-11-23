package com.app.smishing.data.preprocessing

import android.content.Context
import com.app.smishing.data.classification.ModelInfoParser
import com.app.smishing.domain.classification.ActiveVersions
import com.app.smishing.domain.classification.ModelInfo
import com.app.smishing.domain.preprocessing.PreprocessingConfig

/** Carga el `preprocessing_config.json` empaquetado en los assets de la app. */
class PreprocessingConfigLoader(private val context: Context) {

    fun load(): PreprocessingConfig = PreprocessingConfigParser.parse(readJson())

    fun loadModelInfo(): ModelInfo = ModelInfoParser.parse(readJson())

    /**
     * Las versiones empaquetadas, ya comprobadas: lanza lo mismo que [load] y [loadModelInfo] si la app no las acepta, y entonces no
     * hay versión activa.
     */
    fun activeVersions(): ActiveVersions {
        val json = readJson()
        return ActiveVersions(model = ModelInfoParser.parse(json).version, preprocessing = PreprocessingConfigParser.parse(json).version)
    }

    private fun readJson(): String =
        context.assets.open(ASSET_NAME).bufferedReader(Charsets.UTF_8).use { it.readText() }

    companion object {
        const val ASSET_NAME = "preprocessing_config.json"
    }
}
