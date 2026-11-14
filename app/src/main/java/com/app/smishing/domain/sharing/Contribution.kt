package com.app.smishing.domain.sharing

/**
 * El consentimiento para el aporte opcional al corpus (#18): **desactivado por defecto** y revocable. Se consulta cada vez que se va a
 * preparar un aporte, no una sola vez: revocarlo detiene los envíos futuros. El backend no guarda nada que identifique al dispositivo,
 * así que es el teléfono quien verifica el consentimiento antes de enviar.
 */
interface ContributionConsent {
    /** Si la persona lo activó y no lo ha revocado. `false` si nunca lo tocó. Puede bloquear: no se llama desde el hilo principal. */
    fun isGranted(): Boolean

    fun setGranted(granted: Boolean)
}

/** Por qué no se preparó un aporte. */
enum class ContributionRejection {
    /** La persona no lo activó o lo revocó. */
    NoConsent,

    /** Tras desidentificar no queda texto que compartir. */
    Empty,

    /** Aun desidentificado, el texto trae algo que una regla reconoce: se rechaza, no se arregla. */
    NotAnonymized,
}

sealed interface ContributionResult {
    /** El texto ya desidentificado, listo para entregarse al servicio de corpus. [text] es del mensaje: no debe escribirse en logs. */
    data class Ready(val text: String, val rulesVersion: String) : ContributionResult {
        override fun toString() = "Ready(rulesVersion=$rulesVersion)"
    }

    data class Rejected(val reason: ContributionRejection) : ContributionResult
}

/**
 * Prepara el texto de un SMS para el aporte (#18 y #19): **primero** el consentimiento, leído en este momento; después desidentifica con
 * [anonymizerProvider] (que arma el desidentificador con los nombres de contactos de ahora) y **rechaza** lo que no quedó desidentificado.
 * No envía nada: entregar el texto a un destino es de #27, que aún no tiene receptor.
 */
class PrepareContribution(
    private val consent: ContributionConsent,
    private val anonymizerProvider: () -> TextAnonymizer = { Anonymizer() },
) {

    fun prepare(body: String): ContributionResult {
        if (!consent.isGranted()) return ContributionResult.Rejected(ContributionRejection.NoConsent)
        val anonymizer = anonymizerProvider()
        val anonymized = anonymizer.anonymize(body)
        if (anonymized.text.isBlank()) return ContributionResult.Rejected(ContributionRejection.Empty)
        if (!anonymizer.isAnonymized(anonymized.text)) return ContributionResult.Rejected(ContributionRejection.NotAnonymized)
        return ContributionResult.Ready(anonymized.text, anonymized.rulesVersion)
    }
}
