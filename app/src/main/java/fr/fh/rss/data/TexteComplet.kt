package fr.fh.rss.data

/**
 * Règles du téléchargement du texte complet d'un article (lecture hors
 * connexion). Sans dépendance à Android ni au réseau : vérifiables sur JVM.
 */
object TexteComplet {

    /** Ce que devient le texte d'un article après un téléchargement réussi. */
    enum class Issue {
        /** Le texte téléchargé est nettement plus long : il remplace l'existant. */
        REMPLACER,

        /** Page lisible mais pas nettement plus longue : l'existant est gardé, sans alerte. */
        CONSERVER,

        /** Rien d'exploitable (page vide, mur de cookies ou de paywall) : l'existant est gardé, on le signale. */
        ECHEC,
    }

    /** Un texte extrait plus court que ça n'est pas un article : mur de cookies, erreur, page vide. */
    const val SEUIL_MINIMAL = 400

    /** Au-delà, le texte du flux est déjà complet : aucun téléchargement. */
    const val LONGUEUR_SUFFISANTE = 1200

    /** Le nouveau texte doit valoir au moins 1,5 fois l'existant pour le remplacer. */
    private const val NUMERATEUR = 3
    private const val DENOMINATEUR = 2

    /** Délai avant de retenter un téléchargement qui a échoué (3 h). */
    const val DELAI_NOUVEL_ESSAI_MS = 3L * 60 * 60 * 1000

    /** Favoris complétés au plus à chaque collecte : la collecte ne doit pas s'éterniser. */
    const val FAVORIS_PAR_COLLECTE = 10

    /**
     * Décide du sort du texte. Le texte existant (souvent le résumé du flux)
     * ne cède la place qu'à un texte d'au moins [SEUIL_MINIMAL] caractères ET
     * nettement plus long : un mur de cookies de 100 caractères ne doit jamais
     * effacer un résumé de 1 100 caractères.
     */
    fun issue(longueurActuelle: Int, longueurNouvelle: Int): Issue = when {
        longueurNouvelle < SEUIL_MINIMAL -> Issue.ECHEC
        longueurNouvelle.toLong() * DENOMINATEUR > longueurActuelle.toLong() * NUMERATEUR -> Issue.REMPLACER
        else -> Issue.CONSERVER
    }

    /** Vrai si le nouveau texte doit remplacer l'existant. */
    fun doitRemplacer(longueurActuelle: Int, longueurNouvelle: Int): Boolean =
        issue(longueurActuelle, longueurNouvelle) == Issue.REMPLACER

    /**
     * Faut-il lancer un téléchargement maintenant ?
     *
     * - texte déjà long : non ;
     * - jamais tenté : oui ;
     * - tenté avec succès : non (inutile de recharger à chaque ouverture) ;
     * - échec : seulement une fois [DELAI_NOUVEL_ESSAI_MS] écoulé.
     */
    fun doitTenter(
        longueurActuelle: Int,
        essaiLe: Long?,
        echec: Boolean,
        maintenant: Long,
    ): Boolean = when {
        longueurActuelle > LONGUEUR_SUFFISANTE -> false
        essaiLe == null -> true
        !echec -> false
        else -> maintenant - essaiLe >= DELAI_NOUVEL_ESSAI_MS
    }
}
