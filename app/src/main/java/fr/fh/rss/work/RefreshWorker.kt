package fr.fh.rss.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import fr.fh.rss.RssApp
import java.util.concurrent.TimeUnit

/**
 * Collecte périodique optionnelle. Android impose 15 minutes de période
 * minimale pour une tâche périodique ; la valeur est donc bornée, comme le
 * relève le cahier des charges.
 */
class RefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? RssApp ?: return Result.success()
        return runCatching {
            app.container.repository.refreshAll()
            Result.success()
        }.getOrElse { Result.retry() }
    }
}

object RefreshScheduler {

    private const val NOM = "collecte-periodique"

    fun programmer(context: Context, actif: Boolean, minutes: Int, wifiUniquement: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!actif) {
            wm.cancelUniqueWork(NOM)
            return
        }
        val intervalle = minutes.coerceIn(15, 1440).toLong()
        val contraintes = Constraints.Builder()
            .setRequiredNetworkType(if (wifiUniquement) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        val requete = PeriodicWorkRequestBuilder<RefreshWorker>(intervalle, TimeUnit.MINUTES)
            .setConstraints(contraintes)
            .build()
        wm.enqueueUniquePeriodicWork(NOM, ExistingPeriodicWorkPolicy.UPDATE, requete)
    }
}
