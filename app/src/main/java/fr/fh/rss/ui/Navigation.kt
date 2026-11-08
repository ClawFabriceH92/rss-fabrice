package fr.fh.rss.ui

import androidx.compose.runtime.saveable.Saver

/*
 * Règles de navigation de l'écran principal, sans dépendance à Android pour
 * rester vérifiables sur JVM.
 */

private const val CODE_LISTE = "liste"
private const val CODE_LECTEUR = "lecteur"
private const val CODE_IA = "ia"
private const val CODE_REGLAGES = "reglages"

private fun Ecran.code(): String = when (this) {
    Ecran.Liste -> CODE_LISTE
    is Ecran.Lecteur -> CODE_LECTEUR
    Ecran.Ia -> CODE_IA
    Ecran.Reglages -> CODE_REGLAGES
}

/** Forme sauvegardable d'un écran : type, puis identifiant d'article et origine pour le lecteur. */
internal fun Ecran.sauvegarder(): List<Any> = when (this) {
    is Ecran.Lecteur -> listOf(CODE_LECTEUR, articleId, origine.code())
    else -> listOf(code())
}

/** Inverse de [sauvegarder] ; une valeur illisible redonne la liste plutôt qu'un plantage. */
internal fun restaurerEcran(valeurs: List<Any>): Ecran = when (valeurs.firstOrNull()) {
    CODE_LECTEUR -> {
        val id = (valeurs.getOrNull(1) as? Number)?.toLong()
        val origine = if (valeurs.getOrNull(2) == CODE_IA) Ecran.Ia else Ecran.Liste
        if (id == null) Ecran.Liste else Ecran.Lecteur(id, origine)
    }
    CODE_IA -> Ecran.Ia
    CODE_REGLAGES -> Ecran.Reglages
    else -> Ecran.Liste
}

/** Saver de l'écran courant : survit à la rotation et à la recréation de l'activité. */
val SaverEcran: Saver<Ecran, List<Any>> = Saver(
    save = { it.sauvegarder() },
    restore = { restaurerEcran(it) },
)

/**
 * Écran où mène « Retour » : le lecteur revient à son écran d'origine (liste
 * ou IA), tous les autres à la liste.
 */
internal fun Ecran.retour(): Ecran = when (this) {
    is Ecran.Lecteur -> origine
    else -> Ecran.Liste
}

/** La barre de recherche n'a de sens que sur la liste : ailleurs, elle doublerait la barre de l'écran. */
internal fun barreRechercheVisible(ecran: Ecran, rechercheOuverte: Boolean): Boolean =
    rechercheOuverte && ecran == Ecran.Liste

/** « Retour » a quelque chose à faire hors de la liste, ou pour fermer la recherche sur la liste. */
internal fun retourActif(ecran: Ecran, rechercheOuverte: Boolean): Boolean =
    ecran != Ecran.Liste || rechercheOuverte
