package com.app.smishing.data.sharing

import com.app.smishing.domain.sharing.Contribution
import com.app.smishing.domain.sharing.ContributionSendResult
import com.app.smishing.domain.sharing.ItemOutcome
import com.app.smishing.domain.sharing.ItemResult
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * El contrato del envío por lotes con el backend (`smishing-backend`, OpenAPI 1.1.0, `POST /api/v1/contributions/batch`): cómo se arma el
 * cuerpo y cómo se lee la respuesta. Son funciones puras, para poder probarlas sin red. **Nunca** escriben texto de mensajes en logs ni lo
 * repiten en errores.
 *
 * - Cuerpo: `{"contributions": [{"text", "label", "model_version"}]}`, de 1 a 20 aportes. No lleva nada del dispositivo ni la versión de las
 *   reglas de desidentificación (el backend no la acepta).
 * - Respuesta `200`: `{"results": [{"index", "status", "reason"?}]}`, un resultado por aporte y en el mismo orden.
 */
object BackendContract {

    /** El máximo de aportes que acepta el backend por solicitud. */
    const val MAX_BATCH = 20

    private const val MAX_RETRY_AFTER_SECONDS = 3_600L

    fun encode(batch: List<Contribution>): ByteArray {
        require(batch.size in 1..MAX_BATCH) { "Un lote lleva de 1 a $MAX_BATCH aportes" }
        val items = JSONArray()
        for (contribution in batch) {
            items.put(
                JSONObject()
                    .put("text", contribution.text)
                    .put("label", contribution.label.wire)
                    .put("model_version", contribution.modelVersion),
            )
        }
        return JSONObject().put("contributions", items).toString().toByteArray(Charsets.UTF_8)
    }

    /**
     * Qué hacer según lo que contestó el backend a un lote de [expected] aportes.
     *
     * - `200`: un resultado por aporte (si no coinciden en número u orden, se conserva todo y no se reintenta igual: [ContributionSendResult.Failed]).
     * - `401`: la clave no sirve; no se reintenta igual.
     * - `429`: demasiadas solicitudes; se reintenta, esperando lo que diga `Retry-After`.
     * - `5xx` (el backend dice que un fallo de almacenamiento no confirma la recepción y reenviar es seguro): se reintenta.
     * - cualquier otro (`400`, `413`, `422`…): el lote no cumple el contrato; no se reintenta igual.
     */
    fun interpret(status: Int, body: String, retryAfter: String?, expected: Int): ContributionSendResult = when {
        status == 200 -> parseResults(body, expected)
        status == 401 -> ContributionSendResult.Unauthorized
        status == 429 -> ContributionSendResult.Retry(parseRetryAfter(retryAfter))
        status in 500..599 -> ContributionSendResult.Retry()
        else -> ContributionSendResult.Failed
    }

    private fun parseResults(body: String, expected: Int): ContributionSendResult = try {
        val array = JSONObject(body).getJSONArray("results")
        if (array.length() != expected) {
            ContributionSendResult.Failed
        } else {
            val results = ArrayList<ItemResult>(expected)
            var valid = true
            for (i in 0 until expected) {
                val item = array.getJSONObject(i)
                val outcome = when (item.optString("status")) {
                    "accepted" -> ItemOutcome.Accepted
                    "duplicate" -> ItemOutcome.Duplicate
                    "rejected" -> ItemOutcome.Rejected
                    else -> null
                }
                // Si trae el índice, tiene que ser el de su lugar: así cada resultado corresponde a su aporte.
                if (outcome == null || (item.has("index") && item.optInt("index", -1) != i)) {
                    valid = false
                    break
                }
                results += ItemResult(outcome, item.optString("reason").takeIf { it.isNotBlank() })
            }
            if (valid) ContributionSendResult.Delivered(results) else ContributionSendResult.Failed
        }
    } catch (_: JSONException) {
        ContributionSendResult.Failed
    }

    private fun parseRetryAfter(value: String?): Long? = value?.trim()?.toLongOrNull()?.coerceIn(1L, MAX_RETRY_AFTER_SECONDS)
}
