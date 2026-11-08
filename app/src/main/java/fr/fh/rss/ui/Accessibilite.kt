package fr.fh.rss.ui

import androidx.compose.ui.unit.dp

/**
 * Textes d'accessibilité des cartes d'article, sans dépendance à Compose pour
 * rester vérifiables sur JVM. TalkBack lit ces libellés : l'état (favori, lu)
 * ne doit jamais reposer sur une couleur ou une icône seule.
 */
object TextesAccessibilite {

    /** Description du bouton étoile : l'action qu'il déclenche, selon l'état actuel. */
    fun favori(estFavori: Boolean): String =
        if (estFavori) "Retirer de Lire plus tard" else "Ajouter à Lire plus tard"

    /** État annoncé pour une carte : lue ou non lue. */
    fun etatLecture(estLu: Boolean): String = if (estLu) "Lu" else "Non lu"

    /** Action d'accessibilité équivalente au balayage de la liste. */
    fun actionLecture(estLu: Boolean): String =
        if (estLu) "Marquer comme non lu" else "Marquer comme lu"

    /** Annonce du bandeau de collecte (région vive). */
    fun collecte(faits: Int?, total: Int?): String =
        if (faits == null || total == null || total <= 0) "Collecte en cours"
        else "Collecte ${faits.coerceAtMost(total)} sources sur $total"
}

/** Taille minimale d'une cible tactile (recommandation Material et WCAG). */
internal val CIBLE_TACTILE_MIN = 48.dp
