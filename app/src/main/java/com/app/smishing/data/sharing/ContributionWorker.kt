package com.app.smishing.data.sharing

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.app.smishing.BuildConfig
import com.app.smishing.data.contacts.AndroidContacts
import com.app.smishing.data.history.HistoryDatabase
import com.app.smishing.data.history.RoomAnalysisHistory
import com.app.smishing.data.history.RoomPendingContributions
import com.app.smishing.data.settings.DataStoreContributionConsent
import com.app.smishing.domain.history.AnalysisHistory
import com.app.smishing.domain.sharing.Anonymizer
import com.app.smishing.domain.sharing.ContributionSender
import com.app.smishing.domain.sharing.PrepareContribution
import com.app.smishing.domain.sharing.QueueingAnalysisHistory
import com.app.smishing.domain.sharing.SendPendingContributions
import com.app.smishing.domain.sharing.UploadOutcome
import java.util.concurrent.TimeUnit

/**
 * Envía los aportes pendientes (#71) al backend (#72). El sistema lo ejecuta **cuando hay conexión** (aunque la app esté cerrada) y lo
 * reintenta, con espera creciente, si el envío falla por red, por demasiadas solicitudes o por un error del servidor. Sin consentimiento,
 * vacía la cola y termina. Sin backend configurado, con una clave que el backend no reconoce o con un lote que no cumple el contrato, no
 * reintenta sin fin: se detiene y conserva los pendientes (se retoma al llegar otro aporte o al abrir la app).
 */
class ContributionWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val app = applicationContext
        val consent = DataStoreContributionConsent(app)
        val queue = RoomPendingContributions(HistoryDatabase.get(app))
        if (!consent.isGranted()) {
            queue.clear()
            return Result.success()
        }
        // Los nombres de los contactos se leen una sola vez por pasada, no por mensaje.
        val anonymizer by lazy { Anonymizer.fromContactNames(AndroidContacts(app).book().displayNames()) }
        val sender = backendSender(app)
        val outcome = SendPendingContributions(queue, PrepareContribution(consent) { anonymizer }, sender).run()
        // El resultado solo lleva cuántos se enviaron: nunca texto.
        Log.i(TAG, "Aportes pendientes: $outcome")
        return if (outcome is UploadOutcome.Retry) Result.retry() else Result.success()
    }

    private companion object {
        const val TAG = "ContributionWorker"
    }
}

/** El envío al backend con la dirección y la clave de la compilación; sin ellas (o sin HTTPS), no envía nada y todo se conserva. */
internal fun backendSender(context: Context): ContributionSender {
    val config = BackendConfig(BuildConfig.BACKEND_URL, BuildConfig.BACKEND_API_KEY)
    if (!config.isConfigured) return NotConfiguredContributionSender
    return HttpContributionSender(config, UrlConnectionTransport { BackendTls.apply(context, it) })
}

/** Programa y cancela el envío de los aportes pendientes (#71). */
object ContributionScheduler {
    private const val NAME = "contribution-upload"

    private fun request() = OneTimeWorkRequest.Builder(ContributionWorker::class.java)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
        .build()

    /** Hay algo nuevo en la cola: se envía cuando haya conexión, a continuación del envío que ya estuviera programado. */
    fun schedule(context: Context) {
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request())
    }

    /** Al abrir la app: si quedaron pendientes de antes, se retoma el envío. No duplica uno ya programado. */
    fun resume(context: Context) {
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request())
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(NAME)
    }
}

/** El historial de la app con la cola de aportes: lo usan el receptor de SMS y el análisis a pedido. */
object ContributionPipeline {
    fun history(context: Context): AnalysisHistory {
        val app = context.applicationContext
        val database = HistoryDatabase.get(app)
        return QueueingAnalysisHistory(
            delegate = RoomAnalysisHistory(database),
            consent = DataStoreContributionConsent(app),
            queue = RoomPendingContributions(database),
            onQueued = { ContributionScheduler.schedule(app) },
        )
    }
}
