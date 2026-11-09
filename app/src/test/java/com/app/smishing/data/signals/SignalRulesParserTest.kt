package com.app.smishing.data.signals

import com.app.smishing.domain.signals.InvalidSignalRulesException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SignalRulesParserTest {

    private val json = checkNotNull(javaClass.classLoader?.getResourceAsStream("signal_rules.json")) { "Falta signal_rules.json" }
        .bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun rejects(modified: String, expectedInMessage: String) {
        try {
            SignalRulesParser.parse(modified)
            fail("Debía rechazar el JSON")
        } catch (e: InvalidSignalRulesException) {
            assertTrue("El mensaje «${e.message}» debía mencionar «$expectedInMessage»", e.message!!.contains(expectedInMessage))
        }
    }

    @Test
    fun thePackagedRulesParse() {
        val rules = SignalRulesParser.parse(json)
        assertEquals("1.1.0", rules.version)
        assertTrue(rules.urgency.size >= 5)
        assertTrue(rules.dataRequest.isNotEmpty())
        assertTrue(rules.entities.any { it.id == "bcp" && "viabcp.com" in it.officialDomains })
        assertTrue("pe" in rules.linkTlds)
    }

    @Test
    fun everyRuleHasAUniqueId() {
        val rules = SignalRulesParser.parse(json)
        val ids = rules.urgency.map { it.id } + rules.dataRequest.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(rules.entities.size, rules.entities.map { it.id }.toSet().size)
    }

    @Test
    fun anUnknownVersionIsRejected() = rejects(json.replace("\"version\": \"1.1.0\"", "\"version\": \"9.9.9\""), "9.9.9")

    @Test
    fun aPatternThatDoesNotCompileIsRejectedNamingTheRule() =
        rejects(json.replace("\"will_be_cancelled\",\n      \"pattern\": \"\\\\bsera", "\"will_be_cancelled\",\n      \"pattern\": \"(\\\\bsera"), "will_be_cancelled")

    @Test
    fun malformedJsonIsRejected() = rejects("{ no es json", "mal formado")

    @Test
    fun aMissingSectionIsRejected() = rejects(json.replace("\"urgency\"", "\"urgencia\""), "mal formado")

    @Test
    fun anEmptyListOfLinkDomainsIsRejected() =
        rejects(json.replace(Regex("\"tlds\": \\[[^\\]]*\\]"), "\"tlds\": []"), "vacía")

    @Test
    fun twoRulesWithTheSameIdAreRejected() =
        rejects(json.replace("\"will_be_cancelled\"", "\"status_threat\""), "mismo id")
}
