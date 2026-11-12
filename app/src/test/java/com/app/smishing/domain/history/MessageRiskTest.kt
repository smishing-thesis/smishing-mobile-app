package com.app.smishing.domain.history

import com.app.smishing.domain.risk.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Qué se avisa de un mensaje en la lista y en la conversación (#60). */
class MessageRiskTest {

    private val body = "Texto sintético del mensaje"

    private fun risk(level: RiskLevel, markedSafe: Boolean = false, text: String = body) =
        MessageRisk(messageId = 7, recordId = 1, level = level, markedSafe = markedSafe, body = text)

    private fun notice(vararg risks: MessageRisk, messageId: Long = 7, text: String = body) =
        riskNotice(risks.associateBy { it.messageId }, messageId, text)

    @Test
    fun aHighOrMediumRiskMessageIsNoticed() {
        assertEquals(RiskLevel.High, notice(risk(RiskLevel.High)))
        assertEquals(RiskLevel.Medium, notice(risk(RiskLevel.Medium)))
    }

    @Test
    fun aLowRiskMessageIsNeverNoticedSoNoMarkIsNotReadAsLegitimate() {
        assertNull(notice(risk(RiskLevel.Low)))
    }

    @Test
    fun aMessageThePersonMarkedAsSafeIsNoLongerNoticed() {
        assertNull(notice(risk(RiskLevel.High, markedSafe = true)))
    }

    @Test
    fun aMessageWithoutAnalysisIsNotNoticed() {
        assertNull(notice(risk(RiskLevel.High), messageId = 99))
        assertNull(riskNotice(emptyMap(), 7, body))
    }

    @Test
    fun aRecordOfAnotherMessageWithTheSameIdIsIgnored() {
        // Si el almacenamiento de SMS se borra, los ids empiezan de nuevo: el texto debe coincidir.
        assertNull(notice(risk(RiskLevel.High, text = "Otro mensaje"), text = body))
    }

    @Test
    fun theStringFormNeverShowsTheText() {
        assertEquals(false, body in risk(RiskLevel.High).toString())
    }
}
