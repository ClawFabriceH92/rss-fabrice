package fr.fh.rss.data

import fr.fh.rss.data.db.ArticleDao
import fr.fh.rss.data.db.FeedDao
import fr.fh.rss.data.db.FeedEntity
import fr.fh.rss.data.db.FolderDao
import fr.fh.rss.data.net.FeedDiscovery
import fr.fh.rss.data.net.Http
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * Collecte d'une source : une page HTML n'est pas un flux (la source passe en
 * erreur), et une redirection permanente met l'adresse à jour. Les DAO sont simulés.
 */
class CollecteFluxTest {

    private val flux = """<?xml version="1.0"?><rss version="2.0"><channel><title>Source</title>
        <item><title>Un</title><link>https://exemple.fr/1</link><guid>1</guid></item></channel></rss>"""

    private val pageHtml = """<!DOCTYPE html><html><head><title>Connexion</title></head>
        <body><form>Identifiez-vous</form></body></html>"""

    private class Notes {
        val appels = java.util.Collections.synchronizedList(mutableListOf<String>())
        val arguments = java.util.Collections.synchronizedList(mutableListOf<List<Any?>>())
        fun nombre(nom: String) = appels.count { it == nom }
        fun argumentsDe(nom: String) = arguments.zip(appels).filter { it.second == nom }.map { it.first }
    }

    private fun <T : Any> simule(
        classe: Class<T>,
        notes: Notes,
        reponses: Map<String, Any?> = emptyMap(),
    ): T {
        @Suppress("UNCHECKED_CAST")
        return Proxy.newProxyInstance(classe.classLoader, arrayOf(classe)) { _, methode, args ->
            notes.appels += methode.name
            notes.arguments += (args ?: emptyArray()).filter { it !is kotlin.coroutines.Continuation<*> }
            when {
                reponses.containsKey(methode.name) -> reponses[methode.name]
                methode.returnType == Int::class.javaPrimitiveType -> 0
                methode.returnType == Long::class.javaPrimitiveType -> 1L
                else -> Unit
            }
        } as T
    }

    private val source = FeedEntity(id = 1, url = "https://exemple.fr/flux", title = "Source")

    private class HttpSimule(private val reponse: Http.Reponse) : Http("test") {
        override fun get(url: String, etag: String?, lastModified: String?) = reponse
    }

    private class Depot(val depot: FeedRepository, val feeds: Notes, val articles: Notes)

    private fun depot(reponse: Http.Reponse, urlDejaPrise: Boolean = false): Depot {
        val feeds = Notes()
        val articles = Notes()
        val feedDao = simule(
            FeedDao::class.java,
            feeds,
            mapOf(
                "all" to listOf(source),
                "byUrl" to if (urlDejaPrise) source.copy(id = 2) else null,
            ),
        )
        val articleDao = simule(ArticleDao::class.java, articles, mapOf("guids" to emptyList<String>(), "insert" to 1L))
        val folderDao = simule(FolderDao::class.java, Notes())
        return Depot(
            FeedRepository(
                folderDao = folderDao,
                feedDao = feedDao,
                articleDao = articleDao,
                http = HttpSimule(reponse),
            ),
            feeds,
            articles,
        )
    }

    // ------------------------------------------------------- page HTML

    @Test
    fun `une page HTML qui repond a la place d'un flux est une erreur`() = runBlocking {
        val d = depot(Http.Reponse(200, pageHtml, null, null, source.url))

        val bilan = d.depot.refreshAll()

        assertEquals(0, d.feeds.nombre("marquerSucces"))
        assertEquals(1, d.feeds.nombre("marquerEchec"))
        assertEquals(1, bilan.echecs.size)
        assertTrue(bilan.echecs.single().second, bilan.echecs.single().second.contains("n'est plus un flux"))
        // Le détail est mémorisé : la source apparaît dans « Flux en erreur ».
        assertTrue((d.feeds.argumentsDe("marquerEchec").single()[2] as String).contains("flux"))
    }

    @Test
    fun `une reponse vide n'est pas non plus une collecte reussie`() = runBlocking {
        val d = depot(Http.Reponse(200, "   ", null, null, source.url))
        d.depot.refreshAll()
        assertEquals(0, d.feeds.nombre("marquerSucces"))
        assertEquals(1, d.feeds.nombre("marquerEchec"))
    }

    @Test
    fun `un vrai flux reste une collecte reussie`() = runBlocking {
        val d = depot(Http.Reponse(200, flux, null, null, source.url))

        val bilan = d.depot.refreshAll()

        assertEquals(bilan.echecs.toString(), 1, d.feeds.nombre("marquerSucces"))
        assertEquals(0, d.feeds.nombre("marquerEchec"))
        assertEquals(1, bilan.nouveaux)
    }

    @Test
    fun `une page HTML qui cite un flux plus loin reste une page`() {
        val page = "<!doctype html><html><head><link rel=\"alternate\" type=\"application/rss+xml\" href=\"/feed\"></head>" +
            "<body>Exemple : &lt;rss version=2.0&gt;</body></html>"
        assertFalse(FeedDiscovery.looksLikeFeed(page))
        assertFalse(FeedDiscovery.looksLikeFeed("<html><body><feed>x</feed></body></html>"))
        assertTrue(FeedDiscovery.looksLikeFeed("<?xml version=\"1.0\"?>\n<!-- licence -->\n<feed xmlns=\"http://www.w3.org/2005/Atom\"></feed>"))
        assertFalse(FeedDiscovery.looksLikeFeed(""))
    }

    // ------------------------------------------------- redirection permanente

    @Test
    fun `une redirection permanente met a jour l'adresse du flux`() = runBlocking {
        val d = depot(
            Http.Reponse(200, flux, null, null, "https://nouveau.exemple.fr/rss", redirectionPermanente = true),
        )

        d.depot.refreshAll()

        assertEquals(1, d.feeds.nombre("updateUrl"))
        assertEquals(listOf<Any?>(1L, "https://nouveau.exemple.fr/rss"), d.feeds.argumentsDe("updateUrl").single())
    }

    @Test
    fun `une redirection temporaire ne change pas l'adresse`() = runBlocking {
        val d = depot(Http.Reponse(200, flux, null, null, "https://nouveau.exemple.fr/rss"))
        d.depot.refreshAll()
        assertEquals(0, d.feeds.nombre("updateUrl"))
    }

    @Test
    fun `une adresse deja suivie par une autre source n'est pas dupliquee`() = runBlocking {
        val d = depot(
            Http.Reponse(200, flux, null, null, "https://nouveau.exemple.fr/rss", redirectionPermanente = true),
            urlDejaPrise = true,
        )
        d.depot.refreshAll()
        assertEquals(0, d.feeds.nombre("updateUrl"))
        assertEquals(1, d.feeds.nombre("marquerSucces"))
    }

    @Test
    fun `une redirection permanente vers une page HTML ne remplace pas l'adresse`() = runBlocking {
        val d = depot(
            Http.Reponse(200, pageHtml, null, null, "https://exemple.fr/connexion", redirectionPermanente = true),
        )
        d.depot.refreshAll()
        assertEquals(0, d.feeds.nombre("updateUrl"))
        assertEquals(1, d.feeds.nombre("marquerEchec"))
    }

    @Test
    fun `l'adresse retenue apres redirection`() {
        val d = depot(Http.Reponse(304, null, null, null, null))
        val repo = d.depot
        fun r(url: String?, perm: Boolean) = Http.Reponse(200, "", null, null, url, redirectionPermanente = perm)
        assertEquals("https://b.fr/f", repo.adresseApresRedirection("https://a.fr/f", r("https://b.fr/f", true)))
        assertNull(repo.adresseApresRedirection("https://a.fr/f", r("https://b.fr/f", false)))
        assertNull(repo.adresseApresRedirection("https://a.fr/f", r("https://a.fr/f", true)))
        assertNull(repo.adresseApresRedirection("https://a.fr/f", r(null, true)))
        assertNull(repo.adresseApresRedirection("https://a.fr/f", r("ftp://b.fr/f", true)))
    }
}
