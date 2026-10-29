package fr.fh.rss.data

/** Construction de la requête plein texte, isolée pour être testable seule. */
object Fts {

    /**
     * Chaque mot est mis entre guillemets : un caractère spécial (`*`, `-`, `:`,
     * `"`) saisi par l'utilisateur casserait sinon l'analyseur SQLite.
     */
    fun query(saisie: String): String {
        val mots = saisie.lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotBlank() }
            .take(8)
        if (mots.isEmpty()) return "\"\""
        return mots.joinToString(" ") { "\"$it\"" }
    }
}
