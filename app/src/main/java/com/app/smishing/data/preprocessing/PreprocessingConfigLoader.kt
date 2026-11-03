package com.app.smishing.data.preprocessing

import android.content.Context
import com.app.smishing.domain.preprocessing.PreprocessingConfig

/** Carga el `preprocessing_config.json` empaquetado en los assets de la app. */
class PreprocessingConfigLoader(private val context: Context) {

    fun load(): PreprocessingConfig =
        context.assets.open(ASSET_NAME).bufferedReader(Charsets.UTF_8).use {
            PreprocessingConfigParser.parse(it.readText())
        }

    companion object {
        const val ASSET_NAME = "preprocessing_config.json"
    }
}
