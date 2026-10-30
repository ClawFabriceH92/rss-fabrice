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
import fr.fh.rss.data.Reglages
import java.util.Calendar

/**
 * Notification des nouveaux articles après une collecte.
 *
 * Une seule notification, remplacée à chaque collecte : pas d'empilement. Le
 * seuil est d'un article, le silence de nuit (22 h - 7 h) et la permission
 * Android 13 sont respectés.
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

    /** Silence de nuit : 22 h → 7 h. */
    fun heureSilencieuse(heure: Int): Boolean = heure >= 22 || heure < 7

    /**
     * Publie (ou remplace) la notification de collecte.
     *
     * @param titres titres des articles rapportés, du plus récent au plus ancien.
     */
    fun notifierNouveaux(
        context: Context,
        reglages: Reglages,
        nombre: Int,
        titres: List<String>,
        heure: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
    ) {
        if (!reglages.notifierNouveaux || nombre <= 0) return
        if (reglages.silenceNuit && heureSilencieuse(heure)) return
        if (!permissionAccordee(context)) return

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
        runCatching { NotificationManagerCompat.from(context).notify(ID, notification) }
    }
}
