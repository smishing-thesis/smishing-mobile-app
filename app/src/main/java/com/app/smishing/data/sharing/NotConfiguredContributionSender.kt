package com.app.smishing.data.sharing

import com.app.smishing.domain.sharing.Contribution
import com.app.smishing.domain.sharing.ContributionSendResult
import com.app.smishing.domain.sharing.ContributionSender

/**
 * El envío de aportes mientras no exista el destino (#72): no envía nada y deja todo en la cola. Se reemplaza por el envío real al
 * backend cuando se defina su esquema y su endpoint; entonces la app también necesitará el permiso `INTERNET`, que hoy no declara.
 */
object NotConfiguredContributionSender : ContributionSender {
    override fun send(batch: List<Contribution>): ContributionSendResult = ContributionSendResult.NotConfigured
}
