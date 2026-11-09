package com.app.smishing.data.analysis

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.app.smishing.data.classification.OnDeviceClassifier
import com.app.smishing.data.mitigation.LoggingMitigator
import com.app.smishing.data.signals.AndroidSignalDetectorFactory
import com.app.smishing.data.sms.AndroidSmsPermissionRepository
import com.app.smishing.data.sms.AndroidSmsRoleRepository
import com.app.smishing.domain.classification.GatedMessageClassifier
import com.app.smishing.domain.mitigation.Mitigator
import com.app.smishing.domain.risk.RiskAssessor
import com.app.smishing.domain.sms.RoleAndPermissionsGate

/**
 * El análisis de un SMS en el teléfono, armado una sola vez por proceso: el modelo (LiteRT) con la compuerta del rol y los
 * permisos, y las reglas de señales. Quien reciba o muestre un mensaje usa este [RiskAssessor] y no el clasificador directo,
 * para que quitar el rol o un permiso suspenda el análisis (#3).
 *
 * El modelo y las reglas viven mientras viva el proceso: pesan poco (el `.tflite` es de unos 120 KB) y cargarlos en cada
 * mensaje sería más lento.
 */
object AnalysisPipeline {

    @Volatile
    private var assessor: RiskAssessor? = null

    @Volatile
    private var mitigator: Mitigator = LoggingMitigator()

    /** Quien ejecuta lo que decide el modo de mitigación (#13). Hoy solo anota la decisión; #14 y #15 le agregan las acciones. */
    fun mitigator(): Mitigator = mitigator

    /** Solo para pruebas: cambia el ejecutor para ver qué decisiones le llegan. */
    @VisibleForTesting
    fun replaceMitigator(replacement: Mitigator) {
        mitigator = replacement
    }

    fun assessor(context: Context): RiskAssessor =
        assessor ?: synchronized(this) { assessor ?: build(context.applicationContext).also { assessor = it } }

    private fun build(context: Context): RiskAssessor {
        val gate = RoleAndPermissionsGate(AndroidSmsRoleRepository(context), AndroidSmsPermissionRepository(context))
        val classifier = GatedMessageClassifier(OnDeviceClassifier.create(context), gate)
        return RiskAssessor(classifier, AndroidSignalDetectorFactory.create(context))
    }
}
