package fr.fh.rss.update

/**
 * Cadence des vérifications automatiques. Une vérification qui n'a pas atteint
 * GitHub ne consomme pas le créneau horaire : l'appelant remet le compteur à
 * zéro en cas d'échec.
 */
internal object UpdateSchedule {

    const val MIN_INTERVAL_MS = 60 * 60_000L
    const val NEVER = 0L

    fun isDue(manual: Boolean, lastCheckAt: Long, now: Long): Boolean =
        manual || lastCheckAt == NEVER || now - lastCheckAt >= MIN_INTERVAL_MS
}
