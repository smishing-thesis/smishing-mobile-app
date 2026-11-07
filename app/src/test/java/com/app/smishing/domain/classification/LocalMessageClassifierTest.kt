package com.app.smishing.domain.classification

import com.app.smishing.data.classification.ModelInfoParser
import com.app.smishing.data.preprocessing.PreprocessingConfigParser
import com.app.smishing.domain.classification.ClassificationResult.Classified
import com.app.smishing.domain.classification.ClassificationResult.NotClassified
import com.app.smishing.domain.preprocessing.TextPreprocessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lógica de #7 sin LiteRT: política de entrada, decisión con el umbral y versiones del resultado. */
class LocalMessageClassifierTest {

    private val configJson = checkNotNull(javaClass.classLoader?.getResourceAsStream("preprocessing_config.json"))
        .bufferedReader(Charsets.UTF_8).use { it.readText() }
    private val config = PreprocessingConfigParser.parse(configJson)
    private val info = ModelInfoParser.parse(configJson)

    private class FakeScorer(private val score: Float) : ModelScorer {
        val inputs = mutableListOf<List<Int>>()

        override fun score(inputIds: List<Int>): Float {
            inputs += inputIds
            return score
        }
    }

    private fun classifier(scorer: ModelScorer) = LocalMessageClassifier(TextPreprocessor(config), scorer, info)

    @Test
    fun shortMessageIsNotClassifiedAndTheModelIsNotCalled() {
        val scorer = FakeScorer(0.99f)
        val result = classifier(scorer).classify("hola")

        assertEquals(NotClassified(NotClassified.Reason.TOO_SHORT), result)
        assertTrue("El modelo no debe recibir mensajes por debajo del mínimo", scorer.inputs.isEmpty())
    }

    @Test
    fun emptyMessageIsNotClassified() {
        val scorer = FakeScorer(0.99f)

        assertEquals(NotClassified(NotClassified.Reason.TOO_SHORT), classifier(scorer).classify(""))
        assertTrue(scorer.inputs.isEmpty())
    }

    @Test
    fun theMinimumLengthComesFromTheJson() {
        val scorer = FakeScorer(0.5f)
        val atMinimum = "a".repeat(config.minCleanedChars)
        val belowMinimum = "a".repeat(config.minCleanedChars - 1)

        assertTrue(classifier(scorer).classify(atMinimum) is Classified)
        assertTrue(classifier(scorer).classify(belowMinimum) is NotClassified)
        assertEquals(1, scorer.inputs.size)
    }

    @Test
    fun aboveTheThresholdIsSmishing() {
        val result = classifier(FakeScorer(0.95f)).classify(LONG_MESSAGE) as Classified
        assertTrue(result.isSmishing)
    }

    @Test
    fun belowTheThresholdIsNotSmishingEvenAboveHalf() {
        val result = classifier(FakeScorer(0.60f)).classify(LONG_MESSAGE) as Classified
        assertFalse(result.isSmishing)
    }

    @Test
    fun scoreEqualToTheThresholdIsNotSmishing() {
        val result = classifier(FakeScorer(info.threshold.toFloat())).classify(LONG_MESSAGE) as Classified
        assertFalse(result.isSmishing)
    }

    @Test
    fun resultCarriesTheScoreAndBothVersions() {
        val result = classifier(FakeScorer(0.42f)).classify(LONG_MESSAGE) as Classified

        assertEquals(0.42f, result.score, 0f)
        assertEquals("1.0.0", result.modelVersion)
        assertEquals("1.1.0", result.preprocessingVersion)
    }

    @Test
    fun modelReceivesTheSamePreprocessedInputAsThePreprocessor() {
        val scorer = FakeScorer(0.5f)
        classifier(scorer).classify(LONG_MESSAGE)

        val expected = TextPreprocessor(config).preprocess(LONG_MESSAGE).inputIds
        assertEquals(listOf(expected), scorer.inputs)
        assertEquals(config.maxLength, expected.size)
    }

    private companion object {
        const val LONG_MESSAGE = "Tu codigo de verificacion es 458920. No lo compartas con nadie."
    }
}
