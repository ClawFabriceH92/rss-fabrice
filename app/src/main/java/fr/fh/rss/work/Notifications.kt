package fr.fh.rss.work

import android.Manifest
import android.app.NotificationChannel
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import fr.fh.rss.MainActivity
import fr.fh.rss.R
import java.util.Calendar

/**
 * Notification des nouveaux articles après une collecte.
 *
 * Une seule notification, remplacée à chaque collecte : pas d'empilement. La
 * décision (seuil, silence de nuit 22 h - 7 h, report) vient de
 * [ReportNotification] ; la permission Android 13 est revérifiée ici.
 */
object Notifications {

    const val CANAL = "nouveaux-articles"
    private const val ID = 1001

    fun creerCanal(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val canal = NotificationChannel(
            CANAL,
            "Nouveaux articles",
            android.app.NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Résumé des articles rapportés par une collecte"
        }
        NotificationManagerCompat.from(context).createNotificationChannel(canal)
    }

    fun permissionAccordee(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    /** Heure courante (0 à 23), pour [ReportNotification.decider]. */
    fun heureActuelle(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

    /** Retire la notification : l'utilisateur ouvre l'application, il voit les articles. */
    fun annuler(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(ID) }
    }

    /**
     * Publie (ou remplace) la notification de collecte. Le seuil, le silence de
     * nuit et la permission sont décidés avant, par [ReportNotification.decider].
     *
     * @param nombre articles non lus reçus depuis la dernière notification.
     * @param titres titres de ces articles, du plus récent au plus ancien.
     * @return vrai si la notification est partie.
     */
    fun notifierNouveaux(
        context: Context,
        nombre: Int,
        titres: List<String>,
    ): Boolean {
        if (nombre <= 0 || !permissionAccordee(context)) return false

        creerCanal(context)
        val ouverture = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val apercu = titres.take(3).joinToString("\n")
        val notification = NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(R.drawable.ic_stat_rss)
            .setContentTitle(
                if (nombre == 1) "1 nouvel article" else "$nombre nouveaux articles",
            )
            .setContentText(titres.firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(apercu))
            .setAutoCancel(true)
            .setContentIntent(ouverture)
            .setOnlyAlertOnce(true)
            .build()
        return runCatching { NotificationManagerCompat.from(context).notify(ID, notification) }.isSuccess
    }
}
