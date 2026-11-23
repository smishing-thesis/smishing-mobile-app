package com.app.smishing.data.classification

import com.app.smishing.domain.classification.ModelInfo
import com.app.smishing.domain.classification.UnsupportedModelException
import org.json.JSONObject

/** Lee del `preprocessing_config.json` los datos del modelo que la app necesita para decidir. */
object ModelInfoParser {

    /** @throws UnsupportedModelException si la versión del modelo no es de la versión mayor que la app soporta (#35). */
    fun parse(json: String): ModelInfo {
        val root = JSONObject(json)
        val version = root.getJSONObject("model").getString("version")
        if (!ModelInfo.isSupportedVersion(version)) {
            throw UnsupportedModelException("Versión de modelo $version no soportada; la app usa la versión mayor ${ModelInfo.SUPPORTED_MAJOR}")
        }
        return ModelInfo(
            version = version,
            threshold = root.getJSONObject("threshold").getDouble("value"),
            tfliteSha256 = root.getJSONObject("export").getString("tflite_sha256"),
        )
    }
}
