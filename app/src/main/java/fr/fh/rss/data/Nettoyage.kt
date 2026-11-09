package fr.fh.rss.data

import fr.fh.rss.data.db.FeedEntity

/**
 * Propositions de suppression de sources, pour l'onglet « Nettoyage » du
 * paramétrage.
 *
 * Trois cas, du plus sûr au plus discutable :
 * 1. la source échoue depuis longtemps (le serveur ne répond plus) ;
 * 2. la source répond mais n'a publié aucun article depuis plus d'un an
 *    (site à l'arrêt) : son flux existe encore, donc l'onglet « Flux en
 *    erreur » ne la voit pas ;
 * 3. la source n'a jamais rapporté un seul article depuis son ajout.
 *
 * Rien n'est supprimé ici : l'utilisateur coche, puis confirme.
 */
object Nettoyage {

    /** Une source qui échoue depuis ce nombre de jours est proposée au retrait. */
    const val SEUIL_ECHEC_JOURS = 15

    /** Une source sans nouvel article depuis ce nombre de jours est proposée au retrait. */
    const val SEUIL_MUETTE_JOURS = 365

    /** Une source ajoutée depuis ce nombre de jours sans aucun article est proposée. */
    const val SEUIL_JAMAIS_JOURS = 30

    private const val JOUR_MS = 24L * 60 * 60 * 1000

    enum class Raison { EN_ERREUR, MUETTE, JAMAIS_D_ARTICLE }

    data class Proposition(
        val id: Long,
        val titre: String,
        val url: String,
        val raison: Raison,
        /** Ce qui justifie la proposition, en clair : « en échec depuis 32 jours (HTTP 404) ». */
        val detail: String,
        /** Ancienneté du problème en jours : sert à trier du plus oublié au plus récent. */
        val retardJours: Long,
    )

    fun propositions(
        sources: List<FeedEntity>,
        dernierArticle: Map<Long, Long>,
        maintenant: Long = System.currentTimeMillis(),
        seuilEchecJours: Int = SEUIL_ECHEC_JOURS,
        seuilMuetteJours: Int = SEUIL_MUETTE_JOURS,
        seuilJamaisJours: Int = SEUIL_JAMAIS_JOURS,
    ): List<Proposition> {
        val propositions = mutableListOf<Proposition>()
        for (source in sources) {
            val dernier = dernierArticle[source.id] ?: 0L
            val echecDepuis = source.errorSince
            val enEchec = !source.lastError.isNullOrBlank() && echecDepuis != null &&
                joursDepuis(echecDepuis, maintenant) >= seuilEchecJours

            if (enEchec) {
                val jours = joursDepuis(echecDepuis!!, maintenant)
                propositions += Proposition(
                    id = source.id,
                    titre = source.title,
                    url = source.url,
                    raison = Raison.EN_ERREUR,
                    detail = "en échec depuis $jours jour(s) (${source.lastError})",
                    retardJours = jours,
                )
                continue
            }

            if (dernier <= 0L) {
                // Aucun article connu : la source n'a jamais rien rapporté.
                val jours = joursDepuis(source.addedAt, maintenant)
                if (jours >= seuilJamaisJours) {
                    propositions += Proposition(
                        id = source.id,
                        titre = source.title,
                        url = source.url,
                        raison = Raison.JAMAIS_D_ARTICLE,
                        detail = "aucun article depuis son ajout, il y a $jours jour(s)",
                        retardJours = jours,
                    )
                }
                continue
            }

            val jours = joursDepuis(dernier, maintenant)
            if (jours >= seuilMuetteJours) {
                propositions += Proposition(
                    id = source.id,
                    titre = source.title,
                    url = source.url,
                    raison = Raison.MUETTE,
                    detail = "aucun article depuis $jours jour(s)",
                    retardJours = jours,
                )
            }
        }
        return propositions.sortedByDescending { it.retardJours }
    }

    fun joursDepuis(instant: Long, maintenant: Long): Long =
        if (instant <= 0L) 0L else ((maintenant - instant) / JOUR_MS).coerceAtLeast(0L)

    /** Bilan affiché en tête de l'onglet. */
    fun resume(propositions: List<Proposition>): String = when {
        propositions.isEmpty() -> "Aucune source à retirer : toutes publient ou répondent."
        else -> {
            val erreurs = propositions.count { it.raison == Raison.EN_ERREUR }
            val muettes = propositions.count { it.raison == Raison.MUETTE }
            val jamais = propositions.count { it.raison == Raison.JAMAIS_D_ARTICLE }
            buildList {
                if (erreurs > 0) add("$erreurs en échec")
                if (muettes > 0) add("$muettes sans article depuis plus d'un an")
                if (jamais > 0) add("$jamais sans aucun article")
            }.joinToString(", ") + " : à toi de décider."
        }
    }
}
