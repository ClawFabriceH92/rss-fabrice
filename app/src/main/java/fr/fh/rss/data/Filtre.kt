package fr.fh.rss.data

/** Filtre d'affichage de la liste d'articles. */
sealed interface Filtre {
    data object Tous : Filtre
    data object NonLus : Filtre
    data object Favoris : Filtre
    data class Flux(val id: Long) : Filtre
    data class Dossier(val id: Long) : Filtre
}
