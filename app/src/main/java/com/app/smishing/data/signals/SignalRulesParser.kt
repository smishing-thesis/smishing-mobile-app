package com.app.smishing.data.signals

import com.app.smishing.domain.signals.EntityRule
import com.app.smishing.domain.signals.InvalidSignalRulesException
import com.app.smishing.domain.signals.PatternRule
import com.app.smishing.domain.signals.SignalRules
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.regex.PatternSyntaxException

/** Lee `signal_rules.json`. Rechaza versiones desconocidas, campos que faltan y patrones que no compilan. */
object SignalRulesParser {

    fun parse(json: String): SignalRules {
        try {
            val root = JSONObject(json)
            val version = root.getString("version")
            if (version !in SignalRules.SUPPORTED_VERSIONS) {
                throw InvalidSignalRulesException("Versión de reglas desconocida: $version")
            }
            val tlds = root.getJSONObject("link").getJSONArray("tlds").toStringList().map { it.lowercase() }.toSet()
            if (tlds.isEmpty()) throw InvalidSignalRulesException("La lista de dominios de enlaces está vacía")
            return SignalRules(
                version = version,
                linkTlds = tlds,
                urgency = patternRules(root.getJSONArray("urgency")),
                dataRequest = patternRules(root.getJSONArray("dataRequest")),
                entities = entities(root.getJSONObject("impersonation").getJSONArray("entities")),
            )
        } catch (e: JSONException) {
            throw InvalidSignalRulesException("El JSON de reglas está mal formado: ${e.message}", e)
        }
    }

    private fun patternRules(array: JSONArray): List<PatternRule> =
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val id = item.getString("id")
            try {
                PatternRule(id, Regex(item.getString("pattern")))
            } catch (e: PatternSyntaxException) {
                throw InvalidSignalRulesException("La regla «$id» tiene un patrón inválido", e)
            }
        }.also { rules ->
            val duplicated = rules.groupBy { it.id }.filterValues { it.size > 1 }.keys
            if (duplicated.isNotEmpty()) throw InvalidSignalRulesException("Reglas con el mismo id: $duplicated")
        }

    private fun entities(array: JSONArray): List<EntityRule> =
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val names = item.getJSONArray("names").toStringList()
            if (names.isEmpty()) throw InvalidSignalRulesException("La entidad «${item.getString("id")}» no tiene nombres")
            EntityRule(
                id = item.getString("id"),
                names = names,
                ambiguous = item.optBoolean("ambiguous", false),
                officialDomains = item.getJSONArray("officialDomains").toStringList().map { it.lowercase() },
            )
        }

    private fun JSONArray.toStringList(): List<String> = (0 until length()).map { getString(it) }
}
