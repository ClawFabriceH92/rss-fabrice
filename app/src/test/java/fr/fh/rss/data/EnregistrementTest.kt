package fr.fh.rss.data

import fr.fh.rss.data.db.ArticleDao
import fr.fh.rss.data.db.ArticleEntity
import fr.fh.rss.data.db.ArticleFts
import fr.fh.rss.data.db.FeedDao
import fr.fh.rss.data.db.FolderDao
import fr.fh.rss.data.model.ParsedItem
import fr.fh.rss.data.net.FeedParser
import fr.fh.rss.data.net.Http
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
}
