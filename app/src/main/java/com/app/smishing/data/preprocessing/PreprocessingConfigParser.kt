package com.app.smishing.data.preprocessing

import com.app.smishing.domain.preprocessing.CleaningStep
import com.app.smishing.domain.preprocessing.MaskingRule
import com.app.smishing.domain.preprocessing.PreprocessingConfig
import com.app.smishing.domain.preprocessing.UnsupportedPreprocessingException
import org.json.JSONObject

/** Lee `preprocessing_config.json` y rechaza versiones del preprocesamiento que la app no conoce. */
object PreprocessingConfigParser {

    fun parse(json: String): PreprocessingConfig {
        val root = JSONObject(json)

        val preprocessing = root.getJSONObject("preprocessing")
        val version = preprocessing.getString("version")
        if (version !in PreprocessingConfig.SUPPORTED_VERSIONS) {
            throw UnsupportedPreprocessingException(
                "Versión de preprocesamiento $version no soportada; la app conoce ${PreprocessingConfig.SUPPORTED_VERSIONS}",
            )
        }

        val normalization = root.getJSONObject("normalization")
        val normalizationVersion = normalization.getString("version")
        if (normalizationVersion != PreprocessingConfig.SUPPORTED_NORMALIZATION_VERSION) {
            throw UnsupportedPreprocessingException("Versión de normalización $normalizationVersion no soportada")
        }
        val form = normalization.getString("unicode_normalization_form")
        if (form != PreprocessingConfig.SUPPORTED_UNICODE_FORM) {
            throw UnsupportedPreprocessingException("Forma Unicode $form no soportada")
        }

        return PreprocessingConfig(
            version = version,
            specSha256 = preprocessing.getString("spec_sha256"),
            normalizationVersion = normalizationVersion,
            maxLength = root.getInt("maxlen"),
            padIndex = root.getInt("pad_idx"),
            unknownIndex = root.getInt("unk_idx"),
            minCleanedChars = root.getJSONObject("input_policy").getInt("min_cleaned_chars"),
            modelAlphabet = codePointStrings(normalization.getString("model_alphabet")),
            charToIndex = root.getJSONObject("char2idx").toIntMap(),
            cleaningSteps = parseCleaning(root),
            confusables = normalization.getJSONObject("mapping").toStringMap(),
            maskingRules = parseMasking(root),
        )
    }

    private fun parseCleaning(root: JSONObject): List<CleaningStep> {
        val steps = root.getJSONArray("cleaning")
        return List(steps.length()) { index ->
            val step = steps.getJSONObject(index)
            val name = step.getString("name")
            when (val type = step.getString("type")) {
                "regex" -> CleaningStep.RegexReplace(name, step.getString("pattern"), step.getString("replacement"))
                "translate" -> CleaningStep.Translate(name, step.getJSONObject("mapping").toStringMap())
                else -> throw UnsupportedPreprocessingException("Tipo de limpieza $type no soportado")
            }
        }
    }

    private fun parseMasking(root: JSONObject): List<MaskingRule> {
        val rules = root.getJSONArray("masking_rules")
        return List(rules.length()) { index ->
            val rule = rules.getJSONObject(index)
            MaskingRule(
                name = rule.getString("name"),
                pattern = if (rule.isNull("pattern")) null else rule.getString("pattern"),
                replacement = if (rule.isNull("replacement")) null else rule.getString("replacement"),
            )
        }
    }

    private fun codePointStrings(text: String): Set<String> =
        text.codePoints().toArray().mapTo(mutableSetOf()) { String(Character.toChars(it)) }

    private fun JSONObject.toIntMap(): Map<String, Int> = keys().asSequence().associateWith { getInt(it) }

    private fun JSONObject.toStringMap(): Map<String, String> = keys().asSequence().associateWith { getString(it) }
}
