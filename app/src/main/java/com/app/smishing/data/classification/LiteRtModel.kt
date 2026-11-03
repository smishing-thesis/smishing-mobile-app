package com.app.smishing.data.classification

import android.content.Context
import com.app.smishing.domain.classification.ModelInfo
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/** El `.tflite` empaquetado no es el que declara el JSON: no se carga. */
class ModelIntegrityException(message: String) : IllegalStateException(message)

/** Cómo quedó cargado el modelo; sirve para el reporte de verificación en dispositivo. */
data class ModelDescription(
    val sizeBytes: Int,
    val sha256: String,
    val inputType: String,
    val inputShape: List<Int>,
    val outputType: String,
    val outputShape: List<Int>,
)

/**
 * Carga `smishing_charcnn.tflite` con LiteRT, sin red, y devuelve el puntaje de una entrada ya preprocesada.
 * La decisión (puntaje contra umbral) es de [ModelInfo.isSmishing].
 */
class LiteRtModel private constructor(
    private val interpreter: Interpreter,
    private val inputType: DataType,
    private val inputBytes: Int,
    private val outputBytes: Int,
    val description: ModelDescription,
) : AutoCloseable {

    fun score(inputIds: List<Int>): Float {
        val input = ByteBuffer.allocateDirect(inputBytes).order(ByteOrder.nativeOrder())
        when (inputType) {
            DataType.INT32 -> inputIds.forEach { input.putInt(it) }
            else -> inputIds.forEach { input.putFloat(it.toFloat()) }
        }
        check(input.position() == inputBytes) {
            "La entrada tiene ${inputIds.size} valores y el modelo espera $inputBytes bytes de $inputType"
        }
        input.rewind()

        val output = ByteBuffer.allocateDirect(outputBytes).order(ByteOrder.nativeOrder())
        interpreter.run(input, output)
        output.rewind()
        return output.asFloatBuffer().get(0)
    }

    override fun close() = interpreter.close()

    companion object {
        const val ASSET_NAME = "smishing_charcnn.tflite"

        fun load(context: Context, info: ModelInfo): LiteRtModel {
            val bytes = context.assets.open(ASSET_NAME).use { it.readBytes() }
            val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            if (sha256 != info.tfliteSha256) {
                throw ModelIntegrityException("El hash del modelo empaquetado no coincide con el del JSON")
            }

            val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
            buffer.put(bytes)
            buffer.rewind()

            val interpreter = Interpreter(buffer)
            try {
                val input = interpreter.getInputTensor(0)
                val output = interpreter.getOutputTensor(0)
                check(input.dataType() == DataType.INT32 || input.dataType() == DataType.FLOAT32) {
                    "Tipo de entrada no soportado: ${input.dataType()}"
                }
                check(output.dataType() == DataType.FLOAT32) { "Tipo de salida no soportado: ${output.dataType()}" }
                return LiteRtModel(
                    interpreter = interpreter,
                    inputType = input.dataType(),
                    inputBytes = input.numBytes(),
                    outputBytes = output.numBytes(),
                    description = ModelDescription(
                        sizeBytes = bytes.size,
                        sha256 = sha256,
                        inputType = input.dataType().name,
                        inputShape = input.shape().toList(),
                        outputType = output.dataType().name,
                        outputShape = output.shape().toList(),
                    ),
                )
            } catch (e: Throwable) {
                interpreter.close()
                throw e
            }
        }
    }
}
