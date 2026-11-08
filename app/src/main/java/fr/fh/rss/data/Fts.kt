package fr.fh.rss.data

import java.text.Normalizer

private val NON_ALPHANUMERIQUE = Regex("[^\\p{L}\\p{N}]+")

/** Construction de la requête plein texte, isolée pour être testable seule. */
object Fts {

    /** Nombre de mots pris en compte : au-delà, la requête n'apporte rien. */
    const val MAX_MOTS = 8

    /**
     * Chaque mot est mis entre guillemets : un caractère spécial (`*`, `-`, `:`,
     * `"`) saisi par l'utilisateur casserait sinon l'analyseur SQLite.
     *
     * Le dernier mot est traité en préfixe pour que « inflat » trouve
     * « inflation » pendant la frappe. Avec FTS4, l'étoile doit rester DANS les
     * guillemets (`"mot*"`) : la forme `"mot"*` ne trouve rien (vérifié sur
     * SQLite 3.46). Les mots ne contiennent que des lettres et des chiffres,
     * l'étoile ajoutée est donc la seule.
     *
     * Les accents et les majuscules sont repliés par le tokenizer unicode61 de
     * l'index, à l'indexation comme à la requête : inutile de les retirer ici.
     */
    fun query(saisie: String): String {
        // Forme composée : une lettre accentuée décomposée (e + accent combinant)
        // ne doit pas couper le mot en deux.
        val mots = Normalizer.normalize(saisie, Normalizer.Form.NFC)
            .lowercase()
            .split(NON_ALPHANUMERIQUE)
            .filter { it.isNotBlank() }
            .take(MAX_MOTS)
        if (mots.isEmpty()) return "\"\""
        val dernier = mots.lastIndex
        return mots.withIndex().joinToString(" ") { (rang, mot) ->
            if (rang == dernier) "\"$mot*\"" else "\"$mot\""
        }
    }
}
