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
    /** Exécute le bloc dans une transaction (Room en production, appel direct en test). */
    private val transaction: suspend (suspend () -> Int) -> Int = { bloc -> bloc() },
    /** Extraction du texte d'un corps HTML ; injectable pour vérifier qu'elle est évitée. */
    private val texteDe: (String?) -> String = FeedParser::texteSimple,
    /** Connectivité du téléphone (ConnectivityManager en production, toujours vraie en test). */
    private val reseauDisponible: () -> Boolean = { true },
) {

    /** [lues] : sources que le fichier contenait (0 = fichier sans aucune source reconnue). */
    data class ImportResult(val sources: Int, val dossiers: Int, val lues: Int = 0)

    private companion object {
        /** Plafond du texte conservé pour un article (aligné sur l'enregistrement). */
        const val MAX_TEXTE = 200_000
    }

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

    /**
     * Enregistre les items d'un flux. Les guid déjà connus sont écartés avant
     * toute analyse du HTML, puis les nouveaux articles (et leur index plein
     * texte) sont écrits en une seule transaction.
     */
    internal suspend fun enregistrer(feedId: Long, items: List<fr.fh.rss.data.model.ParsedItem>): Int {
        val connus = articleDao.guids(feedId).toHashSet()
        val aInserer = ArrayList<ArticleEntity>()
        for (item in items) {
            // add() renvoie false si le guid est déjà connu ou répété dans le même flux.
            if (!connus.add(item.guid)) continue
            val texte = texteDe(item.contentHtml ?: item.summaryHtml)
            aInserer += ArticleEntity(
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
        }
        if (aInserer.isEmpty()) return 0
        return transaction {
            var nouveaux = 0
            for (article in aInserer) {
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
            nouveaux
        }
    }

    /** Adresse à retenir après une redirection permanente, ou null si elle ne change pas. */
    internal fun adresseApresRedirection(actuelle: String, reponse: Http.Reponse): String? {
        if (!reponse.redirectionPermanente) return null
        val finale = reponse.urlFinale?.trim() ?: return null
        val http = finale.startsWith("http://") || finale.startsWith("https://")
        return finale.takeIf { http && it != actuelle }
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
        // Une page HTML (connexion, site migré) répond 200 mais n'est pas un flux :
        // sans ce garde-fou la collecte « réussirait » à vide, à jamais.
        if (!FeedDiscovery.looksLikeFeed(corps)) {
            throw IOException("ce n'est plus un flux (la page répond du HTML)")
        }
        val flux = FeedParser.parse(feed.url, corps)
        // Redirection permanente : on retient la nouvelle adresse pour les prochaines collectes.
        adresseApresRedirection(feed.url, reponse)?.let { nouvelle ->
            if (feedDao.byUrl(nouvelle) == null) feedDao.updateUrl(feed.id, nouvelle)
        }

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

    /**
     * Collecte de toutes les sources, avec parallélisme limité.
     *
     * Sans réseau, la collecte s'arrête avant de toucher aux sources : hors
     * connexion, ce n'est pas la faute des sources. Si toutes les sources
     * échouent ensemble sur une erreur purement réseau (panne locale qui
     * échappe au test de connectivité), aucune n'est marquée en échec non plus.
     *
     * [ignorerRepos] sert à l'actualisation demandée par l'utilisateur : elle
     * réinterroge aussi les sources au repos, comme [refreshById].
     *
     * [onProgres] est appelé après chaque source traitée : l'écran s'en sert
     * pour afficher une progression chiffrée (« Collecte 8/45 »), qui reste
     * lisible même quand les animations système sont désactivées.
     */
    suspend fun refreshAll(
        paralleles: Int = 4,
        ignorerRepos: Boolean = false,
        onProgres: (faits: Int, total: Int) -> Unit = { _, _ -> },
    ): RefreshSummary = coroutineScope {
        if (!reseauDisponible()) {
            return@coroutineScope RefreshSummary(horsConnexion = true)
        }
        val sources = feedDao.all()
        if (sources.isEmpty()) return@coroutineScope RefreshSummary()

        // Une source qui vient d'échouer plusieurs fois de suite est laissée au
        // repos : inutile de la réinterroger à chaque passage. Une actualisation
        // demandée par l'utilisateur ignore ce repos.
        val maintenant = System.currentTimeMillis()
        val auRepos = if (ignorerRepos) {
            emptyList()
        } else {
            sources.filter { EtatSource.doitAttendre(it.errorCount, it.lastFetchAt, maintenant) }
        }
        val aCollecter = sources.filterNot { it in auRepos }
        if (aCollecter.isEmpty()) {
            return@coroutineScope RefreshSummary(sources = sources.size, enPause = auRepos.size)
        }

        val permis = Semaphore(paralleles.coerceAtLeast(1))
        val total = java.util.concurrent.atomic.AtomicInteger(0)
        val faits = java.util.concurrent.atomic.AtomicInteger(0)
        val echecs = java.util.Collections.synchronizedList(mutableListOf<Pair<FeedEntity, Throwable>>())

        // Premier point de mesure avant tout travail : l'écran affiche
        // « 0/N » plutôt qu'un indicateur muet.
        onProgres(0, aCollecter.size)

        val resultats = aCollecter.map { feed ->
            async(Dispatchers.IO) {
                try {
                    permis.withPermit {
                        runCatching { refreshFeed(feed) }
                            .fold(
                                onSuccess = { total.addAndGet(it) },
                                onFailure = { erreur -> echecs.add(feed to erreur) },
                            )
                    }
                } finally {
                    onProgres(faits.incrementAndGet(), aCollecter.size)
                }
            }
        }
        resultats.awaitAll()

        // Le verdict tombe une fois toutes les sources passées : seul le recul
        // permet de distinguer une panne locale d'une panne de source.
        val liste = echecs.toList()
        if (EtatSource.estPanneLocale(liste.map { it.second }, aCollecter.size)) {
            return@coroutineScope RefreshSummary(
                sources = sources.size,
                enPause = auRepos.size,
                horsConnexion = true,
            )
        }
        val details = liste.map { (feed, erreur) ->
            val detail = erreur.message ?: erreur.javaClass.simpleName
            feedDao.marquerEchec(feed.id, System.currentTimeMillis(), detail)
            feed.title to detail
        }
        // Les favoris restent lisibles hors connexion : on complète leur texte.
        runCatching { completerFavoris() }
        trimArchive()
        RefreshSummary(sources.size, total.get(), details, auRepos.size)
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
        ImportResult(sources, dossiers, entrees.size)
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

    /** Article complet (corps compris), pour le lecteur et les résumés IA. */
    suspend fun article(id: Long): ArticleEntity? = articleDao.byId(id)

    /** Articles dont le téléchargement du texte complet est en cours : évite le doublon ouverture + favori. */
    private val textesEnCours = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()

    /**
     * Télécharge le texte complet d'un article (mode lecture hors connexion).
     * Renvoie vrai si l'article dispose d'un texte complet, faux sinon.
     *
     * - Sans réseau, rien n'est tenté et rien n'est mémorisé : ce n'est pas
     *   l'article qui est en cause.
     * - Le texte existant n'est remplacé que par un texte nettement plus long
     *   (voir [TexteComplet.issue]).
     * - Chaque tentative est mémorisée : un échec n'est retenté qu'après
     *   [TexteComplet.DELAI_NOUVEL_ESSAI_MS], et le lecteur peut l'afficher.
     * - L'index plein texte suit le nouveau texte, sinon il resterait introuvable.
     */
    suspend fun fetchFullContent(articleId: Long): Boolean = withContext(Dispatchers.IO) {
        val article = articleDao.byId(articleId) ?: return@withContext false
        val url = article.url ?: return@withContext false
        val actuel = article.textContent.orEmpty()
        val maintenant = System.currentTimeMillis()
        if (actuel.length > TexteComplet.LONGUEUR_SUFFISANTE) return@withContext true
        if (!TexteComplet.doitTenter(actuel.length, article.texteCompletEssaiLe, article.texteCompletEchec, maintenant)) {
            return@withContext !article.texteCompletEchec
        }
        if (!reseauDisponible()) return@withContext false
        if (!textesEnCours.add(articleId)) return@withContext false
        try {
            val page = runCatching { http.getPage(url) }.getOrNull()?.takeIf { it.ok }?.body
            val extrait = page?.let { ReaderExtractor.extract(it, url) }
            val texte = extrait?.texte?.take(MAX_TEXTE).orEmpty()
            val issue = if (extrait == null) TexteComplet.Issue.ECHEC else TexteComplet.issue(actuel.length, texte.length)
            when (issue) {
                TexteComplet.Issue.REMPLACER -> {
                    transaction {
                        articleDao.setContent(articleId, extrait?.html, texte, maintenant)
                        articleDao.deleteFts(articleId)
                        articleDao.insertFts(ArticleFts(rowId = articleId, title = article.title, body = texte))
                        0
                    }
                    true
                }
                TexteComplet.Issue.CONSERVER -> {
                    articleDao.marquerEssaiTexteComplet(articleId, maintenant, false)
                    true
                }
                TexteComplet.Issue.ECHEC -> {
                    articleDao.marquerEssaiTexteComplet(articleId, maintenant, true)
                    false
                }
            }
        } finally {
            textesEnCours.remove(articleId)
        }
    }

    /**
     * Complète les favoris dont le texte est court, pour qu'ils restent
     * lisibles hors connexion. Au plus [TexteComplet.FAVORIS_PAR_COLLECTE] par
     * passage, les plus récents d'abord ; un échec récent n'est pas retenté.
     */
    suspend fun completerFavoris(paralleles: Int = 2): Int = withContext(Dispatchers.IO) {
        if (!reseauDisponible()) return@withContext 0
        val ids = runCatching {
            articleDao.favorisACompleter(
                seuil = TexteComplet.LONGUEUR_SUFFISANTE,
                avant = System.currentTimeMillis() - TexteComplet.DELAI_NOUVEL_ESSAI_MS,
                limite = TexteComplet.FAVORIS_PAR_COLLECTE,
            )
        }.getOrDefault(emptyList())
        if (ids.isEmpty()) return@withContext 0
        val permis = Semaphore(paralleles.coerceAtLeast(1))
        ids.map { id ->
            async { permis.withPermit { runCatching { fetchFullContent(id) }.getOrDefault(false) } }
        }.awaitAll().count { it }
    }

    /** Nombre d'articles non lus reçus depuis [depuis] (millisecondes), pour la notification. */
    suspend fun nombreNonLusDepuis(depuis: Long): Int =
        runCatching { articleDao.compterNonLusDepuis(depuis) }.getOrDefault(0)

    /** Titres de ces articles (les derniers reçus d'abord), pour l'aperçu de la notification. */
    suspend fun titresNonLusDepuis(depuis: Long, limite: Int = 3): List<String> =
        runCatching { articleDao.titresNonLusDepuis(depuis, limite) }.getOrDefault(emptyList())

    suspend fun trimArchive() {
        val limite = archiveMax()
        runCatching {
            articleDao.trim(limite)
            articleDao.pruneFts()
        }
    }

    /**
     * Retire une source et ses articles, sauf les favoris : ils sont ce que
     * l'utilisateur a choisi de garder (« Lire plus tard ») et restent
     * lisibles, rattachés à une source disparue.
     */
    suspend fun deleteFeed(feedId: Long) {
        retirerSource(feedId)
        articleDao.pruneFts()
    }

    /** Retire la source et ses articles non favoris, sans toucher à l'index plein texte. */
    private suspend fun retirerSource(feedId: Long) {
        feedDao.deleteArticles(feedId)
        feedDao.delete(feedId)
    }

    /** Favoris qui survivraient à la suppression de ces sources (pour la confirmation). */
    suspend fun nombreFavoris(feedIds: Collection<Long>): Int =
        feedIds.distinct().sumOf { articleDao.nombreFavoris(it) }

    /** Ce que ferait la limite d'archive [limite] : sert à avertir avant de réduire. */
    suspend fun apercuArchive(limite: Int): ApercuArchive = ApercuArchive(
        limite = limite,
        total = articleDao.total(),
        aSupprimer = articleDao.compterPurge(limite),
        dontNonLus = articleDao.compterPurgeNonLus(limite),
    )

    /**
     * Supprime plusieurs sources d'un coup (bouton « Tout supprimer » de l'écran
     * Flux en erreur). Renvoie le nombre de sources effectivement retirées.
     */
    suspend fun deleteFeeds(feedIds: Collection<Long>): Int {
        var supprimees = 0
        for (id in feedIds.distinct()) {
            runCatching { retirerSource(id) }.onSuccess { supprimees++ }
        }
        // Une seule purge de l'index (NOT IN sur toute la table) pour tout le lot,
        // au lieu d'une par source.
        if (feedIds.isNotEmpty()) runCatching { articleDao.pruneFts() }
        return supprimees
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

    /**
     * Marque comme lus les articles non lus de la sélection [filtre] et renvoie
     * leurs identifiants, pour que l'utilisateur puisse annuler.
     */
    suspend fun marquerSelectionLue(filtre: Filtre): List<Long> {
        val ids = when (filtre) {
            Filtre.Tous, Filtre.NonLus -> articleDao.idsNonLus()
            Filtre.Favoris -> articleDao.idsNonLusFavoris()
            is Filtre.Flux -> articleDao.idsNonLusDeLaSource(filtre.id)
            is Filtre.Dossier -> articleDao.idsNonLusDuDossier(filtre.id)
        }
        changerLu(ids, true)
        return ids
    }

    /** Remet ces articles à « non lu » (annulation d'un marquage en masse). */
    suspend fun annulerMarquageLu(ids: List<Long>) = changerLu(ids, false)

    /** Par paquets : SQLite limite le nombre de paramètres d'une requête. */
    private suspend fun changerLu(ids: List<Long>, lu: Boolean) {
        for (paquet in ids.chunked(500)) articleDao.setReadPourIds(paquet, lu)
    }

    suspend fun setRead(id: Long, lu: Boolean) = articleDao.setRead(id, lu)

    /** Change l'état lu d'un article et de ses doublons masqués (autres sources), par paquets. */
    suspend fun setReadGroupe(ids: Collection<Long>, lu: Boolean) = changerLu(ids.distinct(), lu)

    suspend fun setStarred(id: Long, favori: Boolean) = articleDao.setStarred(id, favori)

    /**
     * Requête FTS : chaque mot est entre guillemets pour éviter qu'un caractère
     * spécial (`*`, `-`, `:`) ne casse l'analyseur SQLite, et le dernier mot
     * est un préfixe (voir [Fts.query]).
     */
    fun ftsQuery(saisie: String): String = Fts.query(saisie)

    // ---------------------------------------------------------- observation

    fun observeFeeds() = feedDao.observeAll()

    fun observeFolders() = folderDao.observeAll()

    fun observeUnread() = feedDao.observeUnread()

    fun observeUnreadByFolder() = feedDao.observeUnreadByFolder()

    fun observeUnreadTotal() = articleDao.observeUnreadTotal()

    fun observeNonLusCles() = articleDao.observeNonLusCles()

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
