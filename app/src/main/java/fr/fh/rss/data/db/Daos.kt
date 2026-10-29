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

    @Query("UPDATE feeds SET title = :title, siteUrl = :siteUrl WHERE id = :id")
    suspend fun updateMeta(id: Long, title: String, siteUrl: String?)

    @Query("UPDATE feeds SET folderId = :folderId WHERE id = :id")
    suspend fun setFolder(id: Long, folderId: Long?)

    @Query("DELETE FROM feeds WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM articles WHERE feedId = :feedId")
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

    @Query("SELECT * FROM articles ORDER BY publishedAt DESC LIMIT :limit")
    fun observeAll(limit: Int): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE isRead = 0 ORDER BY publishedAt DESC LIMIT :limit")
    fun observeUnread(limit: Int): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE isStarred = 1 ORDER BY publishedAt DESC LIMIT :limit")
    fun observeStarred(limit: Int): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE feedId = :feedId ORDER BY publishedAt DESC LIMIT :limit")
    fun observeByFeed(feedId: Long, limit: Int): Flow<List<ArticleEntity>>

    @Query(
        "SELECT a.* FROM articles a JOIN feeds f ON f.id = a.feedId " +
            "WHERE f.folderId = :folderId ORDER BY a.publishedAt DESC LIMIT :limit",
    )
    fun observeByFolder(folderId: Long, limit: Int): Flow<List<ArticleEntity>>

    @Query(
        "SELECT * FROM articles WHERE id IN " +
            "(SELECT rowid FROM articles_fts WHERE articles_fts MATCH :query) " +
            "ORDER BY publishedAt DESC LIMIT :limit",
    )
    fun observeSearch(query: String, limit: Int): Flow<List<ArticleEntity>>

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

    @Query("UPDATE articles SET contentHtml = :html, textContent = :text WHERE id = :id")
    suspend fun setContent(id: Long, html: String?, text: String?)

    @Query("SELECT COUNT(*) FROM articles WHERE isRead = 0")
    fun observeUnreadTotal(): Flow<Int>

    @Query("SELECT COUNT(*) FROM articles WHERE isRead = 0")
    suspend fun unreadTotal(): Int

    @Query("SELECT COUNT(*) FROM articles")
    suspend fun total(): Int

    /** Purge d'archive : conserve les [keep] plus récents, jamais les favoris. */
    @Query(
        "DELETE FROM articles WHERE isStarred = 0 AND id NOT IN " +
            "(SELECT id FROM articles ORDER BY publishedAt DESC LIMIT :keep)",
    )
    suspend fun trim(keep: Int): Int

    @Query("DELETE FROM articles WHERE feedId = :feedId AND isStarred = 0")
    suspend fun clearFeed(feedId: Long)
}
