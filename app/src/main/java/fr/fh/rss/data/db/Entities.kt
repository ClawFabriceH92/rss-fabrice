package fr.fh.rss.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
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
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val feedId: Long,
    val guid: String,
    val url: String? = null,
    val title: String,
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
)

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
