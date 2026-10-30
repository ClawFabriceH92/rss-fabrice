package fr.fh.rss.data

import fr.fh.rss.BuildConfig
import fr.fh.rss.data.db.ArticleDao
import fr.fh.rss.data.db.ArticleEntity
import fr.fh.rss.data.db.ArticleFts
import fr.fh.rss.data.db.FeedDao
import fr.fh.rss.data.db.FeedEntity
import fr.fh.rss.data.db.FolderDao
import fr.fh.rss.data.db.FolderEntity
import fr.fh.rss.data.model.AddResult
import fr.fh.rss.data.model.ParsedFeed
import fr.fh.rss.data.model.RefreshSummary
import fr.fh.rss.data.net.FeedDiscovery
import fr.fh.rss.data.net.FeedParser
import fr.fh.rss.data.net.Http
import fr.fh.rss.data.net.ReaderExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Tout ce qui touche aux sources : abonnement, collecte, OPML, archive.
 * Aucune dépendance à Android : le dépôt est testable sur JVM.
 */
class FeedRepository(
    private val folderDao: FolderDao,
    private val feedDao: FeedDao,
    private val articleDao: ArticleDao,
    private val http: Http,
    private val archiveMax: suspend () -> Int = { 20000 },
) {

    data class ImportResult(val sources: Int, val dossiers: Int)

    // ------------------------------------------------------------ ajouter

    suspend fun addFeed(saisie: String, folderId: Long? = null): AddResult =
        withContext(Dispatchers.IO) {
            val normalisee = FeedDiscovery.normalizeUrl(saisie)
            if (normalisee.isBlank()) return@withContext AddResult.Failed("adresse vide")

            feedDao.byUrl(normalisee)?.let { return@withContext AddResult.Already(it.title) }

            val candidats = mutableListOf(normalisee)
            val premier = runCatching { http.get(normalisee) }
            val premierCorps = premier.getOrNull()?.takeIf { it.ok }?.body

            var flux: ParsedFeed? = null
            var urlRetenue = normalisee

            if (premierCorps != null) {
                if (FeedDiscovery.looksLikeFeed(premierCorps)) {
                    flux = runCatching { FeedParser.parse(normalisee, premierCorps) }.getOrNull()
                } else {
                    // Page web : on cherche le flux déclaré puis les adresses habituelles.
                    candidats.clear()
                    candidats += FeedDiscovery.fromHtml(premierCorps, normalisee)
                    candidats += FeedDiscovery.candidates(normalisee)
                    for (candidat in candidats.distinct()) {
                        val corps = runCatching { http.get(candidat) }.getOrNull()
                            ?.takeIf { it.ok }?.body ?: continue
                        if (!FeedDiscovery.looksLikeFeed(corps)) continue
                        val essai = runCatching { FeedParser.parse(candidat, corps) }.getOrNull()
                        if (essai != null && essai.items.isNotEmpty()) {
                            flux = essai
                            urlRetenue = candidat
                            break
                        }
                    }
                }
            }

            val obtenu = flux
            if (obtenu == null) {
                val raison = premier.exceptionOrNull()?.let { "connexion impossible (${it.message})" }
                    ?: "aucun flux trouvé à cette adresse"
                return@withContext AddResult.Failed(raison)
            }
            if (obtenu.items.isEmpty()) {
                return@withContext AddResult.Failed("flux vide : aucun article lisible")
            }

            if (feedDao.byUrl(urlRetenue) != null) {
                return@withContext AddResult.Already(obtenu.title)
            }

            val id = feedDao.insert(
                FeedEntity(
                    url = urlRetenue,
                    title = obtenu.title,
                    siteUrl = obtenu.siteUrl,
                    kind = obtenu.kind,
                    folderId = folderId,
                    lastFetchAt = System.currentTimeMillis(),
                ),
            )
            if (id <= 0) return@withContext AddResult.Already(obtenu.title)

            val nouveaux = enregistrer(id, obtenu.items)
            AddResult.Added(obtenu.title, nouveaux, urlRetenue)
        }

    // ----------------------------------------------------------- collecter

    private suspend fun enregistrer(feedId: Long, items: List<fr.fh.rss.data.model.ParsedItem>): Int {
        var nouveaux = 0
        for (item in items) {
            val texte = FeedParser.texteSimple(item.contentHtml ?: item.summaryHtml)
            val article = ArticleEntity(
                feedId = feedId,
                guid = item.guid,
                url = item.url,
                title = item.title,
                author = item.author,
                publishedAt = item.publishedAt,
                summaryHtml = item.summaryHtml,
                contentHtml = item.contentHtml,
                textContent = texte.take(200_000),
                imageUrl = item.imageUrl,
                dedupHash = empreinte(item.title.lowercase() + "|" + (item.url ?: "")),
            )
            val id = articleDao.insert(article)
            if (id > 0) {
                articleDao.insertFts(
                    ArticleFts(
                        rowId = id,
                        title = article.title,
                        body = article.textContent.orEmpty(),
                    ),
                )
                nouveaux++
            }
        }
        return nouveaux
    }

    /** Collecte d'une source. Renvoie le nombre de nouveaux articles. */
    suspend fun refreshFeed(feed: FeedEntity): Int = withContext(Dispatchers.IO) {
        val reponse = http.get(feed.url, feed.etag, feed.lastModified)
        if (reponse.notModified) {
            feedDao.marquerSucces(feed.id, feed.etag, feed.lastModified, System.currentTimeMillis())
            return@withContext 0
        }
        if (!reponse.ok) {
            throw IOException("HTTP ${reponse.code}")
        }
        val corps = reponse.body ?: throw IOException("réponse vide")
        val flux = FeedParser.parse(feed.url, corps)

        var nouveaux = 0
        if (flux.items.isNotEmpty()) {
            nouveaux = enregistrer(feed.id, flux.items)
            if (flux.title.isNotBlank() && flux.title != feed.title) {
                feedDao.updateMeta(feed.id, flux.title, flux.siteUrl ?: feed.siteUrl)
            }
        }
        feedDao.marquerSucces(
            feed.id,
            reponse.etag ?: feed.etag,
            reponse.lastModified ?: feed.lastModified,
            System.currentTimeMillis(),
        )
        nouveaux
    }

    /** Collecte de toutes les sources, avec parallélisme limité. */
    suspend fun refreshAll(paralleles: Int = 4): RefreshSummary = coroutineScope {
        val sources = feedDao.all()
        if (sources.isEmpty()) return@coroutineScope RefreshSummary()

        // Une source qui vient d'échouer plusieurs fois de suite est laissée au
        // repos : inutile de la réinterroger à chaque passage. Un essai manuel
        // depuis l'écran des sources en échec ignore ce repos.
        val maintenant = System.currentTimeMillis()
        val auRepos = sources.filter {
            EtatSource.doitAttendre(it.errorCount, it.lastFetchAt, maintenant)
        }
        val aCollecter = sources.filterNot { it in auRepos }
        if (aCollecter.isEmpty()) {
            return@coroutineScope RefreshSummary(sources = sources.size, enPause = auRepos.size)
        }

        val permis = Semaphore(paralleles.coerceAtLeast(1))
        val total = java.util.concurrent.atomic.AtomicInteger(0)
        val echecs = java.util.Collections.synchronizedList(mutableListOf<Pair<String, String>>())

        val resultats = aCollecter.map { feed ->
            async(Dispatchers.IO) {
                permis.withPermit {
                    runCatching { refreshFeed(feed) }
                        .fold(
                            onSuccess = { total.addAndGet(it) },
                            onFailure = { erreur ->
                                val detail = erreur.message ?: erreur.javaClass.simpleName
                                feedDao.marquerEchec(feed.id, System.currentTimeMillis(), detail)
                                echecs.add(feed.title to detail)
                            },
                        )
                }
            }
        }
        resultats.awaitAll()
        trimArchive()
        RefreshSummary(sources.size, total.get(), echecs.toList(), auRepos.size)
    }

    suspend fun refreshById(feedId: Long): Int {
        val feed = feedDao.byId(feedId) ?: return 0
        return runCatching { refreshFeed(feed) }.getOrElse { erreur ->
            val detail = erreur.message ?: "échec de la collecte"
            feedDao.marquerEchec(feedId, System.currentTimeMillis(), detail)
            throw IOException(detail)
        }
    }

    /**
     * Sources dont la série d'échecs a atteint le seuil de proposition. Rien
     * n'est supprimé ici : l'utilisateur décide depuis les réglages.
     */
    suspend fun sourcesAExaminer(maintenant: Long = System.currentTimeMillis()): List<FeedEntity> =
        withContext(Dispatchers.IO) {
            feedDao.all()
                .filter { EtatSource.propositionDue(it.errorSince, maintenant) }
                .sortedBy { it.errorSince ?: Long.MAX_VALUE }
        }

    /** Sources en échec, quelle que soit l'ancienneté de la série. */
    suspend fun sourcesEnEchec(): List<FeedEntity> = withContext(Dispatchers.IO) {
        feedDao.all()
            .filter { !it.lastError.isNullOrBlank() }
            .sortedBy { it.errorSince ?: Long.MAX_VALUE }
    }

    // --------------------------------------------------------------- OPML

    /**
     * Import rapide : les sources sont enregistrées telles quelles (titre de
     * l'OPML), la collecte se fait ensuite. Un OPML de 300 flux s'importe donc
     * sans attendre 300 requêtes réseau.
     */
    suspend fun importOpml(xml: String): ImportResult = withContext(Dispatchers.IO) {
        val entrees = Opml.parse(xml)
        var sources = 0
        var dossiers = 0

        for (entree in entrees) {
            val url = FeedDiscovery.normalizeUrl(entree.xmlUrl)
            if (url.isBlank()) continue
            if (feedDao.byUrl(url) != null) continue

            var folderId: Long? = null
            if (!entree.dossier.isNullOrBlank()) {
                val nom = entree.dossier.trim()
                folderId = folderDao.byName(nom)?.id
                if (folderId == null) {
                    folderId = folderDao.insert(
                        FolderEntity(name = nom, position = folderDao.count()),
                    )
                    dossiers++
                }
            }

            val id = feedDao.insert(
                FeedEntity(
                    url = url,
                    title = entree.titre,
                    siteUrl = entree.htmlUrl,
                    folderId = folderId,
                ),
            )
            if (id > 0) sources++
        }
        ImportResult(sources, dossiers)
    }

    suspend fun exportOpml(): String = withContext(Dispatchers.IO) {
        val dossiers = folderDao.all()
        val flux = feedDao.all()
        val parDossier = flux.groupBy { it.folderId }
        val sortie = mutableListOf<Pair<String?, List<Pair<String, String>>>>()

        parDossier[null]?.let { sans ->
            sortie += null to sans.map { it.title to it.url }
        }
        for (dossier in dossiers) {
            val dedans = parDossier[dossier.id] ?: emptyList()
            sortie += dossier.name to dedans.map { it.title to it.url }
        }
        // Les flux rattachés à un dossier supprimé remontent à la racine.
        val orphelins = flux.filter { it.folderId != null && dossiers.none { d -> d.id == it.folderId } }
        if (orphelins.isNotEmpty()) {
            sortie += null to orphelins.map { it.title to it.url }
        }
        Opml.export(sortie)
    }

    // ------------------------------------------------------------ articles

    /** Télécharge le texte complet d'un article (mode lecture hors connexion). */
    suspend fun fetchFullContent(articleId: Long): Boolean = withContext(Dispatchers.IO) {
        val article = articleDao.byId(articleId) ?: return@withContext false
        val url = article.url ?: return@withContext false
        val actuel = article.textContent.orEmpty()
        if (actuel.length > 1200) return@withContext true

        val page = runCatching { http.get(url) }.getOrNull()?.takeIf { it.ok }?.body
            ?: return@withContext false
        val extrait = ReaderExtractor.extract(page, url) ?: return@withContext false
        articleDao.setContent(articleId, extrait.html, extrait.texte.take(200_000))
        true
    }

    /** Titres des derniers articles non lus, pour l'aperçu de la notification. */
    suspend fun derniersTitresNonLus(limite: Int = 3): List<String> =
        runCatching { articleDao.derniersNonLus(limite).map { it.title } }.getOrDefault(emptyList())

    suspend fun trimArchive() {
        val limite = archiveMax()
        runCatching {
            articleDao.trim(limite)
            articleDao.pruneFts()
        }
    }

    suspend fun deleteFeed(feedId: Long) {
        articleDao.clearFeed(feedId)
        feedDao.deleteArticles(feedId)
        feedDao.delete(feedId)
        articleDao.pruneFts()
    }

    suspend fun deleteFolder(folderId: Long) {
        folderDao.detachFeeds(folderId)
        folderDao.delete(folderId)
    }

    suspend fun createFolder(name: String): Long {
        val propre = name.trim()
        if (propre.isEmpty()) return -1
        folderDao.byName(propre)?.let { return it.id }
        return folderDao.insert(FolderEntity(name = propre, position = folderDao.count()))
    }

    suspend fun renameFolder(id: Long, name: String) {
        if (name.isNotBlank()) folderDao.rename(id, name.trim())
    }

    suspend fun moveFeed(feedId: Long, folderId: Long?) = feedDao.setFolder(feedId, folderId)

    suspend fun markFeedRead(feedId: Long) = articleDao.markFeedRead(feedId)

    suspend fun markAllRead() = articleDao.markAllRead()

    suspend fun setRead(id: Long, lu: Boolean) = articleDao.setRead(id, lu)

    suspend fun setStarred(id: Long, favori: Boolean) = articleDao.setStarred(id, favori)

    /**
     * Requête FTS : chaque mot est mis entre guillemets pour éviter qu'un
     * caractère spécial (`*`, `-`, `:`) ne casse l'analyseur SQLite.
     */
    fun ftsQuery(saisie: String): String = Fts.query(saisie)

    // ---------------------------------------------------------- observation

    fun observeFeeds() = feedDao.observeAll()

    fun observeFolders() = folderDao.observeAll()

    fun observeUnread() = feedDao.observeUnread()

    fun observeUnreadByFolder() = feedDao.observeUnreadByFolder()

    fun observeUnreadTotal() = articleDao.observeUnreadTotal()

    fun observeArticle(id: Long) = articleDao.observeById(id)

    fun observeSearch(saisie: String, limit: Int = 300) =
        articleDao.observeSearch(ftsQuery(saisie), limit)

    fun observeArticles(filtre: Filtre, limit: Int = 500) = when (filtre) {
        Filtre.Tous -> articleDao.observeAll(limit)
        Filtre.NonLus -> articleDao.observeUnread(limit)
        Filtre.Favoris -> articleDao.observeStarred(limit)
        is Filtre.Flux -> articleDao.observeByFeed(filtre.id, limit)
        is Filtre.Dossier -> articleDao.observeByFolder(filtre.id, limit)
    }

    suspend fun compteurs(): Pair<Int, Int> = feedDao.count() to articleDao.total()

    suspend fun echeancesParSource(): List<FeedEntity> = feedDao.all()

    fun userAgent(): String = "RSSbyFabriceHeuvrard/${BuildConfig.VERSION_NAME} (Android)"
}
