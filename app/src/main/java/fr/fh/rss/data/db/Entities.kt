package fr.fh.rss.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val position: Int = 0,
)

@Entity(
    tableName = "feeds",
    indices = [Index(value = ["url"], unique = true), Index("folderId")],
)
data class FeedEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val siteUrl: String? = null,
    /** rss | atom | json */
    val kind: String = "rss",
    val folderId: Long? = null,
    val etag: String? = null,
    val lastModified: String? = null,
    val lastFetchAt: Long = 0L,
    val lastError: String? = null,
    /** Début de la série d'échecs en cours, null dès qu'une collecte réussit. */
    val errorSince: Long? = null,
    /** Nombre d'échecs consécutifs de la série en cours. */
    val errorCount: Int = 0,
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "articles",
    indices = [
        Index(value = ["feedId", "guid"], unique = true),
        Index("publishedAt"),
        Index("isRead"),
        Index(value = ["feedId", "publishedAt"]),
    ],
)
data class ArticleEntity(
    @PrimaryKey(autoGenerate = true) override val id: Long = 0,
    override val feedId: Long,
    val guid: String,
    override val url: String? = null,
    override val title: String,
    val author: String? = null,
    val publishedAt: Long = 0L,
    val fetchedAt: Long = System.currentTimeMillis(),
    val summaryHtml: String? = null,
    val contentHtml: String? = null,
    val textContent: String? = null,
    val imageUrl: String? = null,
    val isRead: Boolean = false,
    val isStarred: Boolean = false,
    val dedupHash: String = "",
) : ArticleRegroupable {
    @get:Ignore
    override val texteApercu: String get() = textContent.orEmpty().take(600)
}

/** Ce dont le regroupement (doublons, « vu aussi sur », thèmes) a besoin d'un article. */
interface ArticleRegroupable {
    val id: Long
    val feedId: Long
    val title: String
    val url: String?

    /** Début du texte, assez long pour deviner le thème. */
    val texteApercu: String
}

/**
 * Projection légère pour les listes : tout sauf les corps (HTML et texte
 * complet). [extrait] est limité à 400 caractères par la requête ; l'article
 * complet se lit avec [ArticleEntity] (lecteur, résumé IA).
 * [url] n'est pas dans la liste affichée, mais le dédoublonnage et « vu aussi
 * sur » comparent les adresses.
 */
data class ArticleLigne(
    override val id: Long,
    override val feedId: Long,
    override val title: String,
    override val url: String?,
    val author: String?,
    val publishedAt: Long,
    val imageUrl: String?,
    val isRead: Boolean,
    val isStarred: Boolean,
    val extrait: String,
) : ArticleRegroupable {
    override val texteApercu: String get() = extrait
}

/**
 * Index plein texte. Alimenté et nettoyé par le dépôt : l'identifiant de
 * l'article est la clé `rowid` de la table FTS.
 */
@Fts4
@Entity(tableName = "articles_fts")
data class ArticleFts(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long,
    val title: String,
    val body: String,
)
