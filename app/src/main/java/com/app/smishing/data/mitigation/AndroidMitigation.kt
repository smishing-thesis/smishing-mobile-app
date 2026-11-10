package com.app.smishing.data.mitigation

import android.content.Context
import com.app.smishing.data.history.HistoryDatabase
import com.app.smishing.data.history.RoomAnalysisHistory
import com.app.smishing.data.history.RoomMitigationLog
import com.app.smishing.data.settings.DataStoreMitigationSettings
import com.app.smishing.data.sms.TelephonyInboxMessages
import com.app.smishing.domain.mitigation.ApplyMitigation
import com.app.smishing.domain.mitigation.MitigationExecutor
import com.app.smishing.domain.mitigation.MitigationLog
import com.app.smishing.domain.mitigation.Mitigator

/** Arma, una sola vez por proceso, lo que hace y deshace las acciones sobre los mensajes (#14 a #16) con las piezas de Android. */
object AndroidMitigation {

    @Volatile
    private var executor: MitigationExecutor? = null

    fun executor(context: Context): MitigationExecutor =
        executor ?: synchronized(this) { executor ?: build(context.applicationContext).also { executor = it } }

    /** El ejecutor del modo automático: con la decisión de actuar bloquea y retira; con otra, no hace nada. */
    fun mitigator(context: Context): Mitigator = ApplyMitigation(executor(context))

    fun log(context: Context): MitigationLog = RoomMitigationLog(HistoryDatabase.get(context))

    private fun build(context: Context): MitigationExecutor {
        val database = HistoryDatabase.get(context)
        return MitigationExecutor(
            log = RoomMitigationLog(database),
            history = RoomAnalysisHistory(database),
            settings = DataStoreMitigationSettings(context),
            blocker = AndroidSenderBlocker(context),
            inbox = TelephonyInboxMessages(context),
        )
    }
}
