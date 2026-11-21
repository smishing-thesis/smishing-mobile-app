package com.app.smishing.data.sharing

import com.app.smishing.domain.sharing.Contribution
import com.app.smishing.domain.sharing.ContributionLabel
import com.app.smishing.domain.sharing.ContributionSendResult
import com.app.smishing.domain.sharing.ItemOutcome
import com.app.smishing.domain.sharing.ItemResult
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

/** El contrato del envío por lotes con el backend: el cuerpo que se arma y cómo se lee cada respuesta. Textos sintéticos. */
class BackendContractTest {

    private fun contribution(text: String = "Mensaje sintético") = Contribution(text, "1.3.0", ContributionLabel.Smishing, "1.0.0")

    // --- El cuerpo ---

    @Test
    fun theBodyCarriesOnlyTheTextTheLabelAndTheModelVersionOfEachContribution() {
        val body = JSONObject(String(BackendContract.encode(listOf(contribution("uno"), contribution("dos"))), Charsets.UTF_8))
        assertEquals(setOf("contributions"), body.keys().asSequence().toSet())
        val items = body.getJSONArray("contributions")
        assertEquals(2, items.length())
        val first = items.getJSONObject(0)
        // Nada del dispositivo ni la versión de las reglas de desidentificación: el backend no la acepta.
        assertEquals(setOf("text", "label", "model_version"), first.keys().asSequence().toSet())
        assertEquals("uno", first.getString("text"))
        assertEquals("smishing", first.getString("label"))
        assertEquals("1.0.0", first.getString("model_version"))
    }

    @Test
    fun quotesNewlinesAndAccentsSurviveTheTrip() {
        val text = "Dijo: \"llame ya\"\nSu cuenta está bloqueada ñ ¿?"
        val body = JSONObject(String(BackendContract.encode(listOf(contribution(text))), Charsets.UTF_8))
        assertEquals(text, body.getJSONArray("contributions").getJSONObject(0).getString("text"))
    }

    @Test
    fun aBatchHasOneToTwentyContributions() {
        assertThrows(IllegalArgumentException::class.java) { BackendContract.encode(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { BackendContract.encode(List(21) { contribution() }) }
        BackendContract.encode(List(20) { contribution() })
    }

    // --- Lo que contesta el backend ---

    private fun results(vararg items: String) = """{"results":[${items.joinToString(",")}]}"""

    @Test
    fun aSuccessfulAnswerGivesOneResultPerContributionInOrder() {
        val body = results(
            """{"index":0,"status":"accepted"}""",
            """{"index":1,"status":"duplicate"}""",
            """{"index":2,"status":"rejected","reason":"not_anonymized"}""",
        )
        assertEquals(
            ContributionSendResult.Delivered(
                listOf(
                    ItemResult(ItemOutcome.Accepted),
                    ItemResult(ItemOutcome.Duplicate),
                    ItemResult(ItemOutcome.Rejected, "not_anonymized"),
                ),
            ),
            BackendContract.interpret(200, body, null, expected = 3),
        )
    }

    @Test
    fun ifTheAnswerDoesNotMatchTheBatchItIsNotTrusted() {
        val one = """{"index":0,"status":"accepted"}"""
        // Menos resultados que aportes.
        assertEquals(ContributionSendResult.Failed, BackendContract.interpret(200, results(one), null, expected = 2))
        // Un índice que no es el de su lugar.
        assertEquals(
            ContributionSendResult.Failed,
            BackendContract.interpret(200, results(one, """{"index":5,"status":"accepted"}"""), null, expected = 2),
        )
        // Un estado que el contrato no tiene.
        assertEquals(
            ContributionSendResult.Failed,
            BackendContract.interpret(200, results("""{"index":0,"status":"quizas"}"""), null, expected = 1),
        )
        // Algo que no es JSON, o que no trae "results".
        assertEquals(ContributionSendResult.Failed, BackendContract.interpret(200, "<html>", null, expected = 1))
        assertEquals(ContributionSendResult.Failed, BackendContract.interpret(200, "{}", null, expected = 1))
    }

    @Test
    fun aKeyThatDoesNotWorkIsNotRetried() {
        assertEquals(ContributionSendResult.Unauthorized, BackendContract.interpret(401, "", null, expected = 1))
    }

    @Test
    fun tooManyRequestsAreRetriedAfterWhatTheDestinationAsks() {
        assertEquals(ContributionSendResult.Retry(30), BackendContract.interpret(429, "", "30", expected = 1))
        assertEquals(ContributionSendResult.Retry(null), BackendContract.interpret(429, "", null, expected = 1))
        assertEquals(ContributionSendResult.Retry(null), BackendContract.interpret(429, "", "mañana", expected = 1))
        // Una espera exagerada no deja pendientes durante horas.
        assertEquals(ContributionSendResult.Retry(3_600), BackendContract.interpret(429, "", "999999", expected = 1))
    }

    @Test
    fun serverErrorsAreRetriedBecauseSendingAgainIsSafe() {
        assertEquals(ContributionSendResult.Retry(), BackendContract.interpret(503, "", null, expected = 1))
        assertEquals(ContributionSendResult.Retry(), BackendContract.interpret(500, "", null, expected = 1))
    }

    @Test
    fun aBatchThatBreaksTheContractIsNotRetriedTheSameWay() {
        for (status in listOf(400, 403, 404, 413, 422)) {
            assertEquals("estado $status", ContributionSendResult.Failed, BackendContract.interpret(status, "", null, expected = 1))
        }
    }

    @Test
    fun theReasonOfARejectionIsKeptOnlyWhenThereIsOne() {
        val body = results("""{"index":0,"status":"rejected"}""", """{"index":1,"status":"rejected","reason":"unknown_model_version"}""")
        val delivered = BackendContract.interpret(200, body, null, expected = 2) as ContributionSendResult.Delivered
        assertEquals(null, delivered.results[0].reason)
        assertEquals("unknown_model_version", delivered.results[1].reason)
        assertFalse(delivered.results.any { it.outcome != ItemOutcome.Rejected })
    }
}
