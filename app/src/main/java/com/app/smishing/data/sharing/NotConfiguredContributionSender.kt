package com.app.smishing.data.sharing

import com.app.smishing.domain.sharing.Contribution
import com.app.smishing.domain.sharing.ContributionSendResult
import com.app.smishing.domain.sharing.ContributionSender

/**
 * El envío de aportes cuando la compilación no trae la dirección del backend o su clave: no envía nada y deja todo en la cola. Con ellas,
 * el [ContributionWorker] usa [HttpContributionSender] (#72).
 */
object NotConfiguredContributionSender : ContributionSender {
    override fun send(batch: List<Contribution>): ContributionSendResult = ContributionSendResult.NotConfigured
}
