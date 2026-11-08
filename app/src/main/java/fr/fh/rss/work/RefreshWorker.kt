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
import kotlinx.coroutines.flow.first
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
            val depot = app.container.repository
            val parametres = app.container.settings

            // Repère : date de la dernière notification. Au tout premier passage on le
            // pose juste avant la collecte, pour ne pas annoncer tout l'existant.
            var repere = parametres.derniereNotifAt()
            if (repere <= 0L) {
                repere = System.currentTimeMillis() - 1
                parametres.setDerniereNotifAt(repere)
            }

            depot.refreshAll()

            // C'est ici que la veille se voit : on compte ce qui est arrivé depuis le
            // repère (et non seulement cette collecte), donc la nuit ne perd rien.
            val reglages = parametres.flow.first()
            val enAttente = depot.nombreNonLusDepuis(repere)
            val decision = ReportNotification.decider(
                notifierActif = reglages.notifierNouveaux,
                silenceNuit = reglages.silenceNuit,
                heure = Notifications.heureActuelle(),
                permissionAccordee = Notifications.permissionAccordee(applicationContext),
                enAttente = enAttente,
            )
            var publiee = decision != ReportNotification.Decision.NOTIFIER
            if (decision == ReportNotification.Decision.NOTIFIER) {
                publiee = Notifications.notifierNouveaux(
                    context = applicationContext,
                    nombre = enAttente,
                    titres = depot.titresNonLusDepuis(repere, 3),
                )
            }
            // Si la publication a échoué, le repère reste : le passage suivant réessaiera.
            if (publiee) {
                val nouveau = ReportNotification.nouveauRepere(decision, repere, System.currentTimeMillis())
                if (nouveau != repere) parametres.setDerniereNotifAt(nouveau)
            }
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
