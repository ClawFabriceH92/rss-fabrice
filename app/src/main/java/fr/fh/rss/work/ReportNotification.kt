package fr.fh.rss.work

/**
 * Décision de notification après une collecte, sans dépendance à Android.
 *
 * Le silence de nuit ne fait plus perdre d'articles : tant que la nuit dure, la
 * date de dernière notification (le « repère ») ne bouge pas. À la première
 * collecte hors silence, tous les articles non lus reçus depuis le repère sont
 * annoncés d'un coup, avec leur nombre réel.
 */
object ReportNotification {

    enum class Decision {
        /** Rien de neuf depuis le repère : pas de notification. */
        RIEN,

        /** Des articles attendent, mais c'est la nuit : on garde le repère, ils seront annoncés plus tard. */
        REPORTER,

        /** Publier la notification, puis avancer le repère. */
        NOTIFIER,

        /** Notifications coupées ou permission refusée : rien à publier, le repère avance pour ne pas tout annoncer plus tard. */
        ABANDONNER,
    }

    /** Silence de nuit : 22 h → 7 h. */
    fun heureSilencieuse(heure: Int): Boolean = heure >= 22 || heure < 7

    /**
     * @param enAttente articles non lus reçus depuis le repère.
     */
    fun decider(
        notifierActif: Boolean,
        silenceNuit: Boolean,
        heure: Int,
        permissionAccordee: Boolean,
        enAttente: Int,
    ): Decision = when {
        !notifierActif || !permissionAccordee -> Decision.ABANDONNER
        enAttente <= 0 -> Decision.RIEN
        silenceNuit && heureSilencieuse(heure) -> Decision.REPORTER
        else -> Decision.NOTIFIER
    }

    /** Nouveau repère après la décision : il n'avance que si les articles ont été traités. */
    fun nouveauRepere(decision: Decision, repere: Long, maintenant: Long): Long = when (decision) {
        Decision.NOTIFIER, Decision.ABANDONNER -> maintenant
        Decision.RIEN, Decision.REPORTER -> repere
    }
}
