package fr.fh.rss.data

/**
 * Textes des confirmations d'actions irréversibles, sans dépendance à Android
 * pour rester vérifiables sur JVM.
 */
object Avertissements {

    /**
     * Phrase de confirmation avant de retirer une ou plusieurs sources.
     *
     * @param titre nom de la source quand il n'y en a qu'une, sinon null.
     * @param nombre nombre de sources retirées.
     * @param favoris nombre d'articles favoris qui resteront dans l'application.
     */
    fun suppressionSources(titre: String?, nombre: Int, favoris: Int): String {
        val cible = when {
            nombre <= 1 && !titre.isNullOrBlank() -> "« $titre » et ses articles"
            nombre <= 1 -> "La source et ses articles"
            else -> "$nombre sources et leurs articles"
        }
        val qualif = if (favoris > 0) " non favoris" else ""
        val base = "$cible$qualif déjà téléchargés seront retirés de l'application. " +
            "Les sites des flux ne sont pas modifiés."
        val reste = when {
            favoris <= 0 -> " Cette action est définitive."
            favoris == 1 ->
                " 1 favori (Lire plus tard) sera conservé, sous la mention « Source supprimée »."
            else ->
                " $favoris favoris (Lire plus tard) seront conservés, sous la mention « Source supprimée »."
        }
        return base + reste
    }

    /** Libellé affiché pour un article dont la source n'existe plus (favori orphelin). */
    const val SOURCE_SUPPRIMEE = "Source supprimée"

    /** Indicateur discret du lecteur quand le texte complet n'a pas pu être téléchargé. */
    const val TEXTE_COMPLET_INDISPONIBLE = "Texte complet indisponible"

    /** « Ouvrir dans le navigateur » sur un article dont le flux ne donne aucune adresse. */
    const val ARTICLE_SANS_ADRESSE = "Cet article n'a pas d'adresse à ouvrir."

    /** « Ouvrir dans le navigateur » quand aucune application ne sait ouvrir le lien. */
    const val AUCUN_NAVIGATEUR = "Aucune application ne peut ouvrir ce lien."

    /** Import OPML : le fichier n'a pas pu être lu (inaccessible, trop gros ou vide). */
    const val OPML_ILLISIBLE = "Import impossible : le fichier est illisible."

    /** Import OPML : fichier lu, mais sans aucune source reconnue. */
    const val OPML_SANS_SOURCE = "Import impossible : aucune source trouvée dans ce fichier OPML."
}

/**
 * Ce que ferait une nouvelle limite d'archive, avant de l'appliquer.
 *
 * @param limite nombre d'articles que l'on veut conserver.
 * @param total articles actuellement en base.
 * @param aSupprimer articles non favoris qui dépassent la limite (supprimés à la
 * prochaine collecte).
 * @param dontNonLus part de ces articles qui n'est pas encore lue.
 */
data class ApercuArchive(
    val limite: Int,
    val total: Int,
    val aSupprimer: Int,
    val dontNonLus: Int,
) {
    /** Une limite inférieure au nombre d'articles actuel entraîne une purge : on demande. */
    val exigeConfirmation: Boolean get() = limite < total

    fun message(): String {
        val pluriel = if (aSupprimer > 1) "articles" else "article"
        val nonLus = when (dontNonLus) {
            0 -> "aucun n'est non lu"
            1 -> "dont 1 non lu"
            else -> "dont $dontNonLus non lus"
        }
        return "L'archive contient $total articles. Avec une limite de $limite, " +
            "$aSupprimer $pluriel ($nonLus) seront supprimés à la prochaine actualisation. " +
            "Les favoris sont toujours conservés. Cette action est définitive."
    }
}
