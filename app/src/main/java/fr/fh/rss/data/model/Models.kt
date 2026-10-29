package fr.fh.rss.data.model

/** Un article tel qu'il sort de l'analyseur, avant enregistrement. */
data class ParsedItem(
    val guid: String,
    val url: String?,
    val title: String,
    val author: String?,
    val publishedAt: Long,
    val summaryHtml: String?,
    val contentHtml: String?,
    val imageUrl: String?,
)

/** Un flux analysé : RSS, Atom ou JSON Feed. */
data class ParsedFeed(
    val title: String,
    val siteUrl: String?,
    val kind: String,
    val items: List<ParsedItem>,
) {
    val count: Int get() = items.size
}

/** Compteur de non-lus par source. */
data class FeedUnread(val feedId: Long, val nombre: Int)

/** Compteur de non-lus par dossier (clé -1 = sans dossier). */
data class FolderUnread(val folderId: Long, val nombre: Int)

/** Résultat d'un ajout de flux. */
sealed interface AddResult {
    data class Added(val title: String, val count: Int, val url: String) : AddResult
    data class Already(val title: String) : AddResult
    data class Failed(val message: String) : AddResult
}

/** Bilan d'une collecte. */
data class RefreshSummary(
    val sources: Int = 0,
    val nouveaux: Int = 0,
    val echecs: List<Pair<String, String>> = emptyList(),
)
