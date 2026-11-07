package com.app.smishing.data.classification

import android.content.Context
import com.app.smishing.data.preprocessing.PreprocessingConfigLoader
import com.app.smishing.domain.classification.LocalMessageClassifier
import com.app.smishing.domain.classification.MessageClassifier
import com.app.smishing.domain.preprocessing.TextPreprocessor

/**
 * Clasificador de SMS del teléfono: arma el preprocesamiento y el modelo desde los assets de la app, sin red.
 *
 * Cargar el modelo cuesta, así que se crea una sola vez y se reutiliza; es seguro usarlo desde varios hilos.
 * Quien lo cree debe llamar a [close] cuando ya no lo necesite.
 */
class OnDeviceClassifier private constructor(
    private val model: LiteRtModel,
    private val classifier: MessageClassifier,
) : MessageClassifier by classifier, AutoCloseable {

    override fun close() = model.close()

    companion object {
        fun create(context: Context): OnDeviceClassifier {
            val loader = PreprocessingConfigLoader(context)
            val info = loader.loadModelInfo()
            val model = LiteRtModel.load(context, info)
            try {
                val local = LocalMessageClassifier(TextPreprocessor(loader.load()), model, info)
                return OnDeviceClassifier(model, local)
            } catch (e: Throwable) {
                model.close()
                throw e
            }
        }
    }
}
