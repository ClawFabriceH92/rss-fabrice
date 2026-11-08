package fr.fh.rss.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import fr.fh.rss.data.model.FeedUnread
import fr.fh.rss.data.model.FolderUnread
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {

    @Insert
    suspend fun insert(folder: FolderEntity): Long

    @Query("SELECT * FROM folders ORDER BY position, name COLLATE NOCASE")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders ORDER BY position, name COLLATE NOCASE")
    suspend fun all(): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE name = :name LIMIT 1")
    suspend fun byName(name: String): FolderEntity?

    @Query("UPDATE folders SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE feeds SET folderId = NULL WHERE folderId = :id")
    suspend fun detachFeeds(id: Long)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM folders")
    suspend fun count(): Int
}

@Dao
interface FeedDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(feed: FeedEntity): Long

    @Query("SELECT * FROM feeds ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<FeedEntity>>

    @Query("SELECT * FROM feeds ORDER BY title COLLATE NOCASE")
    suspend fun all(): List<FeedEntity>

    @Query("SELECT * FROM feeds WHERE id = :id")
    suspend fun byId(id: Long): FeedEntity?

    @Query("SELECT * FROM feeds WHERE url = :url LIMIT 1")
    suspend fun byUrl(url: String): FeedEntity?

    @Query("SELECT COUNT(*) FROM feeds")
    suspend fun count(): Int

    @Query(
        "UPDATE feeds SET etag = :etag, lastModified = :lastModified, " +
            "lastFetchAt = :at, lastError = :error WHERE id = :id",
    )
    suspend fun updateFetchState(
        id: Long,
        etag: String?,
        lastModified: String?,
        at: Long,
        error: String?,
    )

    /** Collecte réussie : la série d'échecs en cours est effacée. */
    @Query(
        "UPDATE feeds SET etag = :etag, lastModified = :lastModified, lastFetchAt = :at, " +
            "lastError = NULL, errorSince = NULL, errorCount = 0 WHERE id = :id",
    )
    suspend fun marquerSucces(id: Long, etag: String?, lastModified: String?, at: Long)

    /** Collecte en échec : on note le premier échec de la série et on compte. */
    @Query(
        "UPDATE feeds SET lastFetchAt = :at, lastError = :error, " +
            "errorSince = COALESCE(errorSince, :at), errorCount = errorCount + 1 WHERE id = :id",
    )
    suspend fun marquerEchec(id: Long, at: Long, error: String)

    /** Nouvelle adresse d'un flux après une redirection permanente. */
    @Query("UPDATE feeds SET url = :url WHERE id = :id")
    suspend fun updateUrl(id: Long, url: String)

    @Query("UPDATE feeds SET title = :title, siteUrl = :siteUrl WHERE id = :id")
    suspend fun updateMeta(id: Long, title: String, siteUrl: String?)

    @Query("UPDATE feeds SET folderId = :folderId WHERE id = :id")
    suspend fun setFolder(id: Long, folderId: Long?)

    @Query("DELETE FROM feeds WHERE id = :id")
    suspend fun delete(id: Long)

    /** Retire les articles d'une source, sauf ses favoris (« Lire plus tard »), qui sont conservés. */
    @Query("DELETE FROM articles WHERE feedId = :feedId AND isStarred = 0")
    suspend fun deleteArticles(feedId: Long)

    @Query("SELECT feedId AS feedId, COUNT(*) AS nombre FROM articles WHERE isRead = 0 GROUP BY feedId")
    fun observeUnread(): Flow<List<FeedUnread>>

    @Query(
        "SELECT COALESCE(f.folderId, -1) AS folderId, COUNT(*) AS nombre FROM articles a " +
            "JOIN feeds f ON f.id = a.feedId WHERE a.isRead = 0 GROUP BY COALESCE(f.folderId, -1)",
    )
    fun observeUnreadByFolder(): Flow<List<FolderUnread>>
}

@Dao
interface ArticleDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(article: ArticleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFts(row: ArticleFts)

    @Query("DELETE FROM articles_fts WHERE rowid = :id")
    suspend fun deleteFts(id: Long)

    @Query("DELETE FROM articles_fts WHERE rowid NOT IN (SELECT id FROM articles)")
    suspend fun pruneFts()

    /** Guid déjà en base pour un flux : évite d'analyser le HTML d'un article connu. */
    @Query("SELECT guid FROM articles WHERE feedId = :feedId")
    suspend fun guids(feedId: Long): List<String>

    // Les listes ne ramènent pas les corps : projection [ArticleLigne].

    @Query(
        "SELECT id, feedId, title, url, author, publishedAt, imageUrl, isRead, isStarred, " +
            "substr(COALESCE(textContent, summaryHtml, ''), 1, 400) AS extrait " +
            "FROM articles ORDER BY publishedAt DESC LIMIT :limit",
    )
    fun observeAll(limit: Int): Flow<List<ArticleLigne>>

    @Query(
        "SELECT id, feedId, title, url, author, publishedAt, imageUrl, isRead, isStarred, " +
            "substr(COALESCE(textContent, summaryHtml, ''), 1, 400) AS extrait " +
            "FROM articles WHERE isRead = 0 ORDER BY publishedAt DESC LIMIT :limit",
    )
    fun observeUnread(limit: Int): Flow<List<ArticleLigne>>

    @Query(
        "SELECT id, feedId, title, url, author, publishedAt, imageUrl, isRead, isStarred, " +
            "substr(COALESCE(textContent, summaryHtml, ''), 1, 400) AS extrait " +
            "FROM articles WHERE isStarred = 1 ORDER BY publishedAt DESC LIMIT :limit",
    )
    fun observeStarred(limit: Int): Flow<List<ArticleLigne>>

    @Query(
        "SELECT id, feedId, title, url, author, publishedAt, imageUrl, isRead, isStarred, " +
            "substr(COALESCE(textContent, summaryHtml, ''), 1, 400) AS extrait " +
            "FROM articles WHERE feedId = :feedId ORDER BY publishedAt DESC LIMIT :limit",
    )
    fun observeByFeed(feedId: Long, limit: Int): Flow<List<ArticleLigne>>

    @Query(
        "SELECT a.id, a.feedId, a.title, a.url, a.author, a.publishedAt, a.imageUrl, " +
            "a.isRead, a.isStarred, " +
            "substr(COALESCE(a.textContent, a.summaryHtml, ''), 1, 400) AS extrait " +
            "FROM articles a JOIN feeds f ON f.id = a.feedId " +
            "WHERE f.folderId = :folderId ORDER BY a.publishedAt DESC LIMIT :limit",
    )
    fun observeByFolder(folderId: Long, limit: Int): Flow<List<ArticleLigne>>

    @Query(
        "SELECT id, feedId, title, url, author, publishedAt, imageUrl, isRead, isStarred, " +
            "substr(COALESCE(textContent, summaryHtml, ''), 1, 400) AS extrait " +
            "FROM articles WHERE id IN " +
            "(SELECT rowid FROM articles_fts WHERE articles_fts MATCH :query) " +
            "ORDER BY publishedAt DESC LIMIT :limit",
    )
    fun observeSearch(query: String, limit: Int): Flow<List<ArticleLigne>>

    @Query("SELECT * FROM articles WHERE id = :id")
    fun observeById(id: Long): Flow<ArticleEntity?>

    @Query("SELECT * FROM articles WHERE id = :id")
    suspend fun byId(id: Long): ArticleEntity?

    @Query("UPDATE articles SET isRead = :read WHERE id = :id")
    suspend fun setRead(id: Long, read: Boolean)

    @Query("UPDATE articles SET isStarred = :starred WHERE id = :id")
    suspend fun setStarred(id: Long, starred: Boolean)

    @Query("UPDATE articles SET isRead = 1 WHERE feedId = :feedId AND isRead = 0")
    suspend fun markFeedRead(feedId: Long)

    @Query("UPDATE articles SET isRead = 1 WHERE isRead = 0")
    suspend fun markAllRead()

    // Marquage « lu » d'une sélection : on garde les identifiants touchés pour pouvoir annuler.

    @Query("SELECT id FROM articles WHERE isRead = 0")
    suspend fun idsNonLus(): List<Long>

    @Query("SELECT id FROM articles WHERE isRead = 0 AND isStarred = 1")
    suspend fun idsNonLusFavoris(): List<Long>

    @Query("SELECT id FROM articles WHERE isRead = 0 AND feedId = :feedId")
    suspend fun idsNonLusDeLaSource(feedId: Long): List<Long>

    @Query(
        "SELECT a.id FROM articles a JOIN feeds f ON f.id = a.feedId " +
            "WHERE a.isRead = 0 AND f.folderId = :folderId",
    )
    suspend fun idsNonLusDuDossier(folderId: Long): List<Long>

    @Query("UPDATE articles SET isRead = :lu WHERE id IN (:ids)")
    suspend fun setReadPourIds(ids: List<Long>, lu: Boolean)

    /** Favoris d'une source : ils survivent à sa suppression. */
    @Query("SELECT COUNT(*) FROM articles WHERE feedId = :feedId AND isStarred = 1")
    suspend fun nombreFavoris(feedId: Long): Int

    /** Le texte complet remplace l'existant : la tentative est réussie, l'échec éventuel est effacé. */
    @Query(
        "UPDATE articles SET contentHtml = :html, textContent = :text, " +
            "texteCompletEssaiLe = :at, texteCompletEchec = 0 WHERE id = :id",
    )
    suspend fun setContent(id: Long, html: String?, text: String?, at: Long)

    /** Mémorise une tentative sans remplacement (échec, ou texte pas assez complet). */
    @Query("UPDATE articles SET texteCompletEssaiLe = :at, texteCompletEchec = :echec WHERE id = :id")
    suspend fun marquerEssaiTexteComplet(id: Long, at: Long, echec: Boolean)

    /**
     * Favoris à compléter pour la lecture hors connexion : texte court, adresse
     * connue, jamais tentés ou dont l'échec est assez ancien pour être retenté.
     */
    @Query(
        "SELECT id FROM articles WHERE isStarred = 1 AND url IS NOT NULL " +
            "AND length(COALESCE(textContent, '')) <= :seuil " +
            "AND (texteCompletEssaiLe IS NULL OR (texteCompletEchec = 1 AND texteCompletEssaiLe < :avant)) " +
            "ORDER BY publishedAt DESC LIMIT :limite",
    )
    suspend fun favorisACompleter(seuil: Int, avant: Long, limite: Int): List<Long>

    /**
     * Non lus réduits à de quoi dédoublonner : sert à compter ce que la liste
     * affiche. Même ordre que les listes, car le dédoublonnage garde la
     * première occurrence.
     */
    @Query("SELECT id, feedId, title, url FROM articles WHERE isRead = 0 ORDER BY publishedAt DESC")
    fun observeNonLusCles(): Flow<List<ArticleCle>>

    @Query("SELECT COUNT(*) FROM articles WHERE isRead = 0")
    fun observeUnreadTotal(): Flow<Int>

    @Query("SELECT COUNT(*) FROM articles WHERE isRead = 0")
    suspend fun unreadTotal(): Int

    @Query("SELECT COUNT(*) FROM articles")
    suspend fun total(): Int

    /** Articles non lus reçus (enregistrés) après [depuis] : ce qu'annonce la notification. */
    @Query("SELECT COUNT(*) FROM articles WHERE isRead = 0 AND fetchedAt > :depuis")
    suspend fun compterNonLusDepuis(depuis: Long): Int

    /** Titres de ces articles, les derniers reçus d'abord : aperçu de la notification. */
    @Query(
        "SELECT title FROM articles WHERE isRead = 0 AND fetchedAt > :depuis " +
            "ORDER BY fetchedAt DESC, publishedAt DESC LIMIT :limite",
    )
    suspend fun titresNonLusDepuis(depuis: Long, limite: Int): List<String>

    /** Purge d'archive : conserve les [keep] plus récents, jamais les favoris. */
    @Query(
        "DELETE FROM articles WHERE isStarred = 0 AND id NOT IN " +
            "(SELECT id FROM articles ORDER BY publishedAt DESC LIMIT :keep)",
    )
    suspend fun trim(keep: Int): Int

    /** Articles que [trim] supprimerait pour la limite [keep] : aperçu avant de réduire l'archive. */
    @Query(
        "SELECT COUNT(*) FROM articles WHERE isStarred = 0 AND id NOT IN " +
            "(SELECT id FROM articles ORDER BY publishedAt DESC LIMIT :keep)",
    )
    suspend fun compterPurge(keep: Int): Int

    /** Parmi eux, ceux qui ne sont pas encore lus. */
    @Query(
        "SELECT COUNT(*) FROM articles WHERE isStarred = 0 AND isRead = 0 AND id NOT IN " +
            "(SELECT id FROM articles ORDER BY publishedAt DESC LIMIT :keep)",
    )
    suspend fun compterPurgeNonLus(keep: Int): Int

    @Query("DELETE FROM articles WHERE feedId = :feedId AND isStarred = 0")
    suspend fun clearFeed(feedId: Long)
}
