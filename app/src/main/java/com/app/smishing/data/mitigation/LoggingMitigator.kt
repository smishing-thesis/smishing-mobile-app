package com.app.smishing.data.mitigation

import android.util.Log
import com.app.smishing.domain.history.AnalysisRecord
import com.app.smishing.domain.mitigation.MitigationDecision
import com.app.smishing.domain.mitigation.Mitigator

/**
 * El ejecutor provisional de #13: todavía no hay acciones que ejecutar (bloquear es #14 y retirar del buzón, #15), así que solo
 * anota la decisión. Es lo que demuestra que el modo se consultó antes de actuar. No se registra el mensaje ni el remitente.
 */
class LoggingMitigator : Mitigator {
    override fun apply(decision: MitigationDecision, record: AnalysisRecord) {
        Log.i(TAG, "Mitigación decidida: $decision (aún sin acciones)")
    }

    private companion object {
        const val TAG = "Mitigator"
    }
}
