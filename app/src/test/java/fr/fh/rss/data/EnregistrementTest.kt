package fr.fh.rss.data

import fr.fh.rss.data.db.ArticleDao
import fr.fh.rss.data.db.ArticleEntity
import fr.fh.rss.data.db.ArticleFts
import fr.fh.rss.data.db.FeedDao
import fr.fh.rss.data.db.FeedEntity
import fr.fh.rss.data.db.FolderDao
import fr.fh.rss.data.model.ParsedItem
import fr.fh.rss.data.net.FeedParser
import fr.fh.rss.data.net.Http
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * Enregistrement des articles d'un flux : pas d'analyse HTML pour un article
 * déjà connu, une seule transaction par flux. Les DAO sont simulés en mémoire.
 */
class EnregistrementTest {

    /** DAO d'articles en mémoire : seules les méthodes utilisées par enregistrer(). */
    private class ArticlesMemoire {
        val lignes = mutableListOf<ArticleEntity>()
        val fts = mutableListOf<ArticleFts>()
        var appelsGuids = 0

        val dao: ArticleDao = Proxy.newProxyInstance(
            ArticleDao::class.java.classLoader,
            arrayOf(ArticleDao::class.java),
        ) { _, methode, args ->
            when (methode.name) {
                "guids" -> {
                    appelsGuids++
                    val feedId = args[0] as Long
                    lignes.filter { it.feedId == feedId }.map { it.guid }
                }
                "insert" -> {
                    val article = args[0] as ArticleEntity
                    // Index unique (feedId, guid) avec insertion IGNORE.
                    if (lignes.any { it.feedId == article.feedId && it.guid == article.guid }) {
                        -1L
                    } else {
                        val id = lignes.size + 1L
                        lignes += article.copy(id = id)
                        id
                    }
                }
                "insertFts" -> {
                    fts += args[0] as ArticleFts
                    Unit
                }
                else -> throw UnsupportedOperationException(methode.name)
            }
        } as ArticleDao
    }

    private fun aucun(): Nothing = throw UnsupportedOperationException()

    private val feedDao = Proxy.newProxyInstance(
        FeedDao::class.java.classLoader,
        arrayOf(FeedDao::class.java),
    ) { _, _, _ -> aucun() } as FeedDao

    private val folderDao = Proxy.newProxyInstance(
        FolderDao::class.java.classLoader,
        arrayOf(FolderDao::class.java),
    ) { _, _, _ -> aucun() } as FolderDao

    private fun item(guid: String) = ParsedItem(
        guid = guid,
        url = "https://exemple.fr/$guid",
        title = "Titre $guid",
        author = null,
        publishedAt = 1_800_000_000_000L,
        summaryHtml = null,
        contentHtml = "<p>Corps <b>$guid</b></p>",
        imageUrl = null,
    )

    private class Compteurs {
        var analyses = 0
        var transactions = 0
    }

    private fun depot(articles: ArticlesMemoire, compteurs: Compteurs) = FeedRepository(
        folderDao = folderDao,
        feedDao = feedDao,
        articleDao = articles.dao,
        http = Http("test"),
        transaction = { bloc ->
            compteurs.transactions++
            bloc()
        },
        texteDe = { html ->
            compteurs.analyses++
            FeedParser.texteSimple(html)
        },
    )

    @Test
    fun `un article deja connu n'est pas analyse`() = runBlocking {
        val articles = ArticlesMemoire()
        val compteurs = Compteurs()
        val depot = depot(articles, compteurs)
        val items = listOf(item("a"), item("b"), item("c"))

        assertEquals(3, depot.enregistrer(1L, items))
        assertEquals(3, compteurs.analyses)

        // Deuxième passe sur le même flux : rien de nouveau, aucune analyse.
        assertEquals(0, depot.enregistrer(1L, items))
        assertEquals(3, compteurs.analyses)
        assertEquals(3, articles.lignes.size)
    }

    @Test
    fun `seuls les nouveaux items d'un flux sont analyses`() = runBlocking {
        val articles = ArticlesMemoire()
        val compteurs = Compteurs()
        val depot = depot(articles, compteurs)
        depot.enregistrer(1L, listOf(item("a"), item("b")))
        val avant = compteurs.analyses

        val nouveaux = depot.enregistrer(1L, listOf(item("a"), item("b"), item("c"), item("d")))

        assertEquals(2, nouveaux)
        assertEquals(avant + 2, compteurs.analyses)
        assertEquals(listOf("a", "b", "c", "d"), articles.lignes.map { it.guid })
    }

    @Test
    fun `un guid repete dans le meme flux n'est insere qu'une fois`() = runBlocking {
        val articles = ArticlesMemoire()
        val compteurs = Compteurs()

        val nouveaux = depot(articles, compteurs).enregistrer(1L, listOf(item("a"), item("a")))

        assertEquals(1, nouveaux)
        assertEquals(1, compteurs.analyses)
    }

    @Test
    fun `le meme guid dans deux flux differents reste un article nouveau`() = runBlocking {
        val articles = ArticlesMemoire()
        val depot = depot(articles, Compteurs())

        assertEquals(1, depot.enregistrer(1L, listOf(item("a"))))
        assertEquals(1, depot.enregistrer(2L, listOf(item("a"))))
        assertEquals(2, articles.lignes.size)
    }

    @Test
    fun `les insertions d'un flux tiennent dans une seule transaction`() = runBlocking {
        val articles = ArticlesMemoire()
        val compteurs = Compteurs()
        val depot = depot(articles, compteurs)

        depot.enregistrer(1L, (1..30).map { item("g$it") })

        assertEquals(1, compteurs.transactions)
        assertEquals(1, articles.appelsGuids)
        assertEquals(30, articles.lignes.size)
    }

    @Test
    fun `aucune transaction ouverte quand tout est deja connu`() = runBlocking {
        val articles = ArticlesMemoire()
        val compteurs = Compteurs()
        val depot = depot(articles, compteurs)
        val items = listOf(item("a"), item("b"))
        depot.enregistrer(1L, items)

        depot.enregistrer(1L, items)

        assertEquals(1, compteurs.transactions)
    }

    @Test
    fun `l'index plein texte suit chaque nouvel article`() = runBlocking {
        val articles = ArticlesMemoire()
        val depot = depot(articles, Compteurs())

        depot.enregistrer(1L, listOf(item("a"), item("b")))

        assertEquals(2, articles.fts.size)
        assertEquals(articles.lignes.map { it.id }, articles.fts.map { it.rowId })
        assertTrue(articles.fts.first().body.contains("Corps a"))
        assertEquals("Titre a", articles.fts.first().title)
    }

    // ------------------------------------------------ collecte, suppression, lu

    /** DAO simulés qui notent chaque appel (nom de méthode) ; les réponses viennent de [reponses]. */
    private class Espion(
        private val reponses: Map<String, (Array<Any?>) -> Any?> = emptyMap(),
    ) {
        val appels = java.util.Collections.synchronizedList(mutableListOf<String>())
        val arguments = java.util.Collections.synchronizedList(mutableListOf<List<Any?>>())

        fun <T : Any> creer(classe: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return Proxy.newProxyInstance(classe.classLoader, arrayOf(classe)) { _, methode, args ->
                appels += methode.name
                // Le dernier argument d'une fonction suspend est la continuation : on l'écarte.
                val utiles = (args ?: emptyArray()).filter { it !is kotlin.coroutines.Continuation<*> }
                arguments += utiles
                reponses[methode.name]?.invoke(utiles.toTypedArray()) ?: when (methode.returnType) {
                    Int::class.javaPrimitiveType -> 0
                    Long::class.javaPrimitiveType -> 0L
                    else -> Unit
                }
            } as T
        }

        fun nombre(nom: String) = appels.count { it == nom }
    }

    private fun source(id: Long, erreurs: Int = 0, dernierEssai: Long = 0L) = FeedEntity(
        id = id,
        url = "https://exemple.fr/flux$id",
        title = "Source $id",
        errorCount = erreurs,
        lastFetchAt = dernierEssai,
    )

    /** Http qui répond par [reponse] pour chaque adresse, et compte les appels. */
    private class HttpSimule(
        private val reponse: (String) -> Http.Reponse,
    ) : Http("test") {
        val adresses = java.util.Collections.synchronizedList(mutableListOf<String>())

        override fun get(url: String, etag: String?, lastModified: String?): Reponse {
            adresses += url
            return reponse(url)
        }
    }

    private val nonModifie = Http.Reponse(304, null, null, null, null)

    private fun depotCollecte(
        feeds: Espion,
        articles: Espion,
        http: Http,
        reseau: () -> Boolean = { true },
    ) = FeedRepository(
        folderDao = folderDao,
        feedDao = feeds.creer(FeedDao::class.java),
        articleDao = articles.creer(ArticleDao::class.java),
        http = http,
        reseauDisponible = reseau,
    )

    @Test
    fun `hors connexion la collecte sort sans toucher aux sources`() = runBlocking {
        val feeds = Espion(mapOf("all" to { _ -> listOf(source(1), source(2)) }))
        val articles = Espion()
        val http = HttpSimule { nonModifie }

        val bilan = depotCollecte(feeds, articles, http, reseau = { false }).refreshAll()

        assertTrue(bilan.horsConnexion)
        assertTrue(http.adresses.isEmpty())
        assertTrue("aucun accès aux sources : ${feeds.appels}", feeds.appels.isEmpty())
        assertTrue("aucune purge d'archive : ${articles.appels}", articles.appels.isEmpty())
    }

    @Test
    fun `toutes les sources en erreur reseau ne sont pas marquees en echec`() = runBlocking {
        val feeds = Espion(mapOf("all" to { _ -> listOf(source(1), source(2), source(3)) }))
        val http = HttpSimule { throw java.net.UnknownHostException("exemple.fr") }

        val bilan = depotCollecte(feeds, Espion(), http).refreshAll()

        assertEquals(3, http.adresses.size)
        assertEquals(0, feeds.nombre("marquerEchec"))
        assertTrue(bilan.horsConnexion)
        assertTrue(bilan.echecs.isEmpty())
    }

    @Test
    fun `un delai depasse partout est aussi une panne locale`() = runBlocking {
        val feeds = Espion(mapOf("all" to { _ -> listOf(source(1), source(2)) }))
        val http = HttpSimule { url ->
            if (url.endsWith("1")) throw java.net.SocketTimeoutException("timeout")
            else throw java.net.ConnectException("refusée")
        }

        val bilan = depotCollecte(feeds, Espion(), http).refreshAll()

        assertEquals(0, feeds.nombre("marquerEchec"))
        assertTrue(bilan.horsConnexion)
    }

    @Test
    fun `une erreur HTTP reste comptee comme un echec de la source`() = runBlocking {
        val feeds = Espion(mapOf("all" to { _ -> listOf(source(1), source(2), source(3)) }))
        val http = HttpSimule { url ->
            if (url.endsWith("2")) Http.Reponse(500, "erreur", null, null, null)
            else throw java.net.UnknownHostException("exemple.fr")
        }

        val bilan = depotCollecte(feeds, Espion(), http).refreshAll()

        // Toutes les sources ne sont pas en panne réseau : chacune est comptée.
        assertEquals(3, feeds.nombre("marquerEchec"))
        assertFalse(bilan.horsConnexion)
        assertEquals(3, bilan.echecs.size)
    }

    @Test
    fun `une source qui repond bien n'est jamais declaree en panne locale`() = runBlocking {
        val feeds = Espion(mapOf("all" to { _ -> listOf(source(1), source(2)) }))
        val http = HttpSimule { url ->
            if (url.endsWith("1")) nonModifie else throw java.net.UnknownHostException("exemple.fr")
        }

        val bilan = depotCollecte(feeds, Espion(), http).refreshAll()

        assertEquals(1, feeds.nombre("marquerEchec"))
        assertEquals(1, feeds.nombre("marquerSucces"))
        assertFalse(bilan.horsConnexion)
    }

    @Test
    fun `l'actualisation demandee ignore le repos des sources`() = runBlocking {
        val maintenant = System.currentTimeMillis()
        val auRepos = source(1, erreurs = 4, dernierEssai = maintenant - 60_000L)
        val feeds = Espion(mapOf("all" to { _ -> listOf(auRepos, source(2)) }))
        val http = HttpSimule { nonModifie }

        val automatique = depotCollecte(feeds, Espion(), http).refreshAll()
        assertEquals(1, automatique.enPause)
        assertEquals(listOf("https://exemple.fr/flux2"), http.adresses.toList())

        http.adresses.clear()
        val manuelle = depotCollecte(feeds, Espion(), http).refreshAll(ignorerRepos = true)
        assertEquals(0, manuelle.enPause)
        assertEquals(setOf("https://exemple.fr/flux1", "https://exemple.fr/flux2"), http.adresses.toSet())
    }

    @Test
    fun `supprimer une source ne touche qu'aux articles non favoris`() = runBlocking {
        val feeds = Espion()
        val articles = Espion()

        depotCollecte(feeds, articles, HttpSimule { nonModifie }).deleteFeed(7L)

        // deleteArticles épargne les favoris (voir BaseDeDonneesTest) ; aucune autre
        // requête ne doit effacer des articles.
        assertEquals(listOf("deleteArticles", "delete"), feeds.appels.toList())
        assertEquals(listOf("pruneFts"), articles.appels.toList())
        assertEquals(7L, feeds.arguments.first().first())
    }

    @Test
    fun `supprimer plusieurs sources ne purge l'index plein texte qu'une fois`() = runBlocking {
        val feeds = Espion()
        val articles = Espion()

        val supprimees = depotCollecte(feeds, articles, HttpSimule { nonModifie })
            .deleteFeeds(listOf(1L, 2L, 3L, 2L))

        // L'identifiant répété ne compte qu'une fois ; chaque source est retirée,
        // mais la purge de l'index (NOT IN sur toute la table) n'est jouée qu'une fois.
        assertEquals(3, supprimees)
        assertEquals(3, feeds.nombre("deleteArticles"))
        assertEquals(3, feeds.nombre("delete"))
        assertEquals(listOf("pruneFts"), articles.appels.toList())
    }

    @Test
    fun `supprimer une liste vide ne touche pas a l'index`() = runBlocking {
        val articles = Espion()

        assertEquals(0, depotCollecte(Espion(), articles, HttpSimule { nonModifie }).deleteFeeds(emptyList()))
        assertEquals(emptyList<String>(), articles.appels.toList())
    }

    @Test
    fun `le nombre de favoris conserves s'additionne par source`() = runBlocking {
        val articles = Espion(mapOf("nombreFavoris" to { args -> if (args[0] == 1L) 3 else 2 }))
        val depot = depotCollecte(Espion(), articles, HttpSimule { nonModifie })

        assertEquals(5, depot.nombreFavoris(listOf(1L, 2L)))
        // Un identifiant répété ne compte qu'une fois.
        assertEquals(3, depot.nombreFavoris(listOf(1L, 1L)))
        assertEquals(0, depot.nombreFavoris(emptyList()))
    }

    @Test
    fun `tout marquer comme lu se limite a la selection courante`() = runBlocking {
        val articles = Espion(
            mapOf(
                "idsNonLus" to { _ -> listOf(1L, 2L, 3L) },
                "idsNonLusFavoris" to { _ -> listOf(2L) },
                "idsNonLusDeLaSource" to { args -> if (args[0] == 9L) listOf(4L, 5L) else emptyList<Long>() },
                "idsNonLusDuDossier" to { _ -> listOf(6L) },
            ),
        )
        val depot = depotCollecte(Espion(), articles, HttpSimule { nonModifie })

        assertEquals(listOf(4L, 5L), depot.marquerSelectionLue(Filtre.Flux(9L)))
        assertEquals(listOf(6L), depot.marquerSelectionLue(Filtre.Dossier(3L)))
        assertEquals(listOf(2L), depot.marquerSelectionLue(Filtre.Favoris))
        assertEquals(listOf(1L, 2L, 3L), depot.marquerSelectionLue(Filtre.Tous))
        assertEquals(listOf(1L, 2L, 3L), depot.marquerSelectionLue(Filtre.NonLus))
        // Jamais la requête globale « tout le parc » : seules des mises à jour par identifiants.
        assertEquals(0, articles.nombre("markAllRead"))
        assertEquals(5, articles.nombre("setReadPourIds"))
    }

    @Test
    fun `l'annulation remet les memes articles a non lu par paquets`() = runBlocking {
        val articles = Espion()
        val depot = depotCollecte(Espion(), articles, HttpSimule { nonModifie })
        val ids = (1L..1200L).toList()

        depot.annulerMarquageLu(ids)

        // 1 200 identifiants : trois paquets de 500 au plus (limite de paramètres SQLite).
        assertEquals(3, articles.nombre("setReadPourIds"))
        assertEquals(listOf(500, 500, 200), articles.arguments.map { (it[0] as List<*>).size })
        assertTrue(articles.arguments.all { it[1] == false })
    }

    @Test
    fun `l'apercu d'archive reprend les comptes de la base`() = runBlocking {
        val articles = Espion(
            mapOf(
                "total" to { _ -> 8000 },
                "compterPurge" to { args -> 8000 - (args[0] as Int) },
                "compterPurgeNonLus" to { _ -> 120 },
            ),
        )
        val apercu = depotCollecte(Espion(), articles, HttpSimule { nonModifie }).apercuArchive(5000)

        assertEquals(ApercuArchive(limite = 5000, total = 8000, aSupprimer = 3000, dontNonLus = 120), apercu)
        assertTrue(apercu.exigeConfirmation)
    }
}
