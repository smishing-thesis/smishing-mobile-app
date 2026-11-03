package com.app.smishing.data.classification

import com.app.smishing.domain.classification.ModelInfo
import org.json.JSONObject

/** Lee del `preprocessing_config.json` los datos del modelo que la app necesita para decidir. */
object ModelInfoParser {

    fun parse(json: String): ModelInfo {
        val root = JSONObject(json)
        return ModelInfo(
            version = root.getJSONObject("model").getString("version"),
            threshold = root.getJSONObject("threshold").getDouble("value"),
            tfliteSha256 = root.getJSONObject("export").getString("tflite_sha256"),
        )
    }
}
