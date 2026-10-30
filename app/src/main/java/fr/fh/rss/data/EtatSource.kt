package fr.fh.rss.data

/**
 * Lecture de l'état de collecte d'une source.
 *
 * Une source en échec ne doit pas être signalée tout de suite : beaucoup de
 * sites tombent une heure puis reviennent. On laisse donc passer une série
 * d'échecs avant de proposer quoi que ce soit à l'utilisateur, et la
 * proposition ne supprime jamais rien : c'est lui qui décide.
 */
object EtatSource {

    /** Nombre de jours d'échec avant de proposer un examen de la source. */
    const val SEUIL_PROPOSITION_JOURS = 8

    /** Nombre d'échecs consécutifs avant de laisser la source au repos. */
    const val SEUIL_PAUSE_ECHECS = 3

    /** Durée du repos après le dernier échec, en heures. */
    const val PAUSE_HEURES = 6L

    private const val HEURE_MS = 60L * 60L * 1000L

    /**
     * Vrai quand la source vient d'échouer plusieurs fois de suite : inutile de
     * la réinterroger à chaque collecte, on la laisse tranquille quelques heures.
     */
    fun doitAttendre(tentatives: Int, dernierEssai: Long, maintenant: Long): Boolean {
        if (tentatives < SEUIL_PAUSE_ECHECS) return false
        if (dernierEssai <= 0L) return false
        val ecart = maintenant - dernierEssai
        return ecart >= 0L && ecart < PAUSE_HEURES * HEURE_MS
    }

    /** Minutes restantes avant le prochain essai automatique (0 si aucun repos). */
    fun minutesDePause(
        tentatives: Int,
        dernierEssai: Long,
        maintenant: Long,
    ): Int {
        if (!doitAttendre(tentatives, dernierEssai, maintenant)) return 0
        val restant = PAUSE_HEURES * HEURE_MS - (maintenant - dernierEssai)
        return ((restant + HEURE_MS / 60 - 1) / (HEURE_MS / 60)).toInt()
    }

    private const val JOUR_MS = 24L * 60L * 60L * 1000L

    /** Jours pleins écoulés depuis le premier échec de la série en cours. */
    fun joursEnEchec(errorDepuis: Long?, maintenant: Long): Int {
        if (errorDepuis == null || errorDepuis <= 0L) return 0
        val ecart = maintenant - errorDepuis
        if (ecart <= 0L) return 0
        return (ecart / JOUR_MS).toInt()
    }

    /** Vrai quand la série d'échecs atteint le seuil de proposition. */
    fun propositionDue(errorDepuis: Long?, maintenant: Long): Boolean =
        joursEnEchec(errorDepuis, maintenant) >= SEUIL_PROPOSITION_JOURS

    /**
     * Phrase affichée sous la source dans les réglages.
     *
     * @param errorDepuis date du premier échec, null si la source répond.
     * @param tentatives nombre d'échecs consécutifs.
     */
    fun resume(
        errorDepuis: Long?,
        tentatives: Int,
        maintenant: Long,
        dernierEssai: Long = 0L,
    ): String {
        if (errorDepuis == null) return "Dernière collecte réussie."
        val jours = joursEnEchec(errorDepuis, maintenant)
        val essais = if (tentatives > 1) " · $tentatives échecs consécutifs" else ""
        val minutes = minutesDePause(tentatives, dernierEssai, maintenant)
        val repos = if (minutes > 0) " · repos ${minutes} min avant nouvel essai" else ""
        val base = when {
            jours == 0 -> "En échec depuis moins d'un jour$essais"
            jours == 1 -> "En échec depuis 1 jour$essais"
            jours < SEUIL_PROPOSITION_JOURS ->
                "En échec depuis $jours jours$essais · proposition au ${SEUIL_PROPOSITION_JOURS}e jour"
            else ->
                "En échec depuis $jours jours$essais · source à examiner"
        }
        return base + repos
    }
}
