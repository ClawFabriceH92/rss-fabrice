package fr.fh.rss.data

import fr.fh.rss.data.db.ArticleDao
import fr.fh.rss.data.db.ArticleEntity
import fr.fh.rss.data.db.ArticleFts
import fr.fh.rss.data.db.FeedDao
import fr.fh.rss.data.db.FolderDao
import fr.fh.rss.data.net.Http
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * Règle d'écrasement du texte complet, et son application par le dépôt
 * (mémoire de la tentative, index plein texte, absence de réseau).
 */
class TexteCompletTest {

    // ------------------------------------------------------------ règle pure

    @Test
    fun `un texte sous le seuil minimal ne remplace jamais`() {
        assertEquals(TexteComplet.Issue.ECHEC, TexteComplet.issue(longueurActuelle = 0, longueurNouvelle = 399))
        // Le mur de cookies de 100 caractères face à un résumé de 1 100.
        assertEquals(TexteComplet.Issue.ECHEC, TexteComplet.issue(1_100, 100))
        assertFalse(TexteComplet.doitRemplacer(1_100, 100))
    }

    @Test
    fun `au seuil minimal un texte remplace un texte vide ou tres court`() {
        assertTrue(TexteComplet.doitRemplacer(0, 400))
        assertTrue(TexteComplet.doitRemplacer(200, 400))
    }

    @Test
    fun `le nouveau texte doit etre nettement plus long`() {
        // 1,5 fois l'existant exactement : pas assez ; au-delà : oui.
        assertEquals(TexteComplet.Issue.CONSERVER, TexteComplet.issue(1_000, 1_500))
        assertEquals(TexteComplet.Issue.CONSERVER, TexteComplet.issue(1_000, 1_100))
        assertEquals(TexteComplet.Issue.CONSERVER, TexteComplet.issue(1_000, 900))
        assertEquals(TexteComplet.Issue.REMPLACER, TexteComplet.issue(1_000, 1_501))
        assertEquals(TexteComplet.Issue.REMPLACER, TexteComplet.issue(600, 5_000))
    }

    @Test
    fun `aucun depassement d'entier sur de tres longs textes`() {
        assertTrue(TexteComplet.doitRemplacer(Int.MAX_VALUE / 2, Int.MAX_VALUE))
        assertFalse(TexteComplet.doitRemplacer(Int.MAX_VALUE, Int.MAX_VALUE))
    }

    @Test
    fun `un texte deja long n'est pas retelecharge`() {
        assertFalse(TexteComplet.doitTenter(1_201, null, false, 0L))
        assertTrue(TexteComplet.doitTenter(1_200, null, false, 0L))
    }

    @Test
    fun `une tentative reussie n'est pas relancee et un echec attend son delai`() {
        val essai = 1_000_000L
        assertFalse(TexteComplet.doitTenter(300, essai, false, essai + 10 * TexteComplet.DELAI_NOUVEL_ESSAI_MS))
        assertFalse(TexteComplet.doitTenter(300, essai, true, essai + TexteComplet.DELAI_NOUVEL_ESSAI_MS - 1))
        assertTrue(TexteComplet.doitTenter(300, essai, true, essai + TexteComplet.DELAI_NOUVEL_ESSAI_MS))
    }

    // ------------------------------------------------------ dépôt, DAO simulés

    private class ArticleMemoire(var article: ArticleEntity) {
        val fts = HashMap<Long, ArticleFts>()
        val appels = mutableListOf<String>()

        val dao: ArticleDao = Proxy.newProxyInstance(
            ArticleDao::class.java.classLoader,
            arrayOf(ArticleDao::class.java),
        ) { _, methode, args ->
            appels += methode.name
            when (methode.name) {
                "byId" -> article
                "setContent" -> {
                    article = article.copy(
                        contentHtml = args[1] as String?,
                        textContent = args[2] as String?,
                        texteCompletEssaiLe = args[3] as Long,
                        texteCompletEchec = false,
                    )
                    Unit
                }
                "marquerEssaiTexteComplet" -> {
                    article = article.copy(
                        texteCompletEssaiLe = args[1] as Long,
                        texteCompletEchec = args[2] as Boolean,
                    )
                    Unit
                }
                "deleteFts" -> {
                    fts.remove(args[0] as Long)
                    Unit
                }
                "insertFts" -> {
                    val ligne = args[0] as ArticleFts
                    fts[ligne.rowId] = ligne
                    Unit
                }
                else -> throw UnsupportedOperationException(methode.name)
            }
        } as ArticleDao
    }

    private fun inutilise(): Nothing = throw UnsupportedOperationException()

    private val feedDao = Proxy.newProxyInstance(
        FeedDao::class.java.classLoader,
        arrayOf(FeedDao::class.java),
    ) { _, _, _ -> inutilise() } as FeedDao

    private val folderDao = Proxy.newProxyInstance(
        FolderDao::class.java.classLoader,
        arrayOf(FolderDao::class.java),
    ) { _, _, _ -> inutilise() } as FolderDao

    private class HttpPage(private val page: String?, private val code: Int = 200) : Http("test") {
        var appels = 0
        override fun getPage(url: String): Reponse {
            appels++
            if (page == null) throw java.io.IOException("réseau coupé")
            return Reponse(code, page, null, null, url)
        }
    }

    private fun depot(memoire: ArticleMemoire, http: Http, reseau: Boolean = true) = FeedRepository(
        folderDao = folderDao,
        feedDao = feedDao,
        articleDao = memoire.dao,
        http = http,
        reseauDisponible = { reseau },
    )

    private fun phrases(n: Int) = (1..n).joinToString(" ") { "Phrase numéro $it de l'article complet." }

    /** Page dont le texte extrait fait [longueur] caractères (un seul paragraphe). */
    private fun pageAvecTexte(longueur: Int): String {
        val corps = phrases(200).take(longueur)
        return "<html><body><article><p>$corps</p></article></body></html>"
    }

    private fun article(texte: String?, url: String? = "https://exemple.fr/a") = ArticleEntity(
        id = 7,
        feedId = 1,
        guid = "g7",
        title = "Inflation en zone euro",
        url = url,
        textContent = texte,
        contentHtml = texte?.let { "<p>$it</p>" },
    )

    @Test
    fun `le texte complet remplace un resume court et met l'index a jour`() = runBlocking {
        val memoire = ArticleMemoire(article("Court résumé du flux."))

        val ok = depot(memoire, HttpPage(pageAvecTexte(3_000))).fetchFullContent(7L)

        assertTrue(ok)
        val texte = memoire.article.textContent.orEmpty()
        assertTrue("texte remplacé : ${texte.length}", texte.length > 1_500)
        assertFalse(memoire.article.texteCompletEchec)
        assertNotNull(memoire.article.texteCompletEssaiLe)
        // L'index plein texte porte le nouveau texte, pour le titre actuel.
        val indexe = memoire.fts.getValue(7L)
        assertEquals(texte, indexe.body)
        assertEquals("Inflation en zone euro", indexe.title)
        // Suppression puis insertion : pas de ligne en double dans l'index.
        assertTrue(memoire.appels.indexOf("deleteFts") < memoire.appels.indexOf("insertFts"))
    }

    @Test
    fun `un mur de cookies ne remplace pas le resume et l'echec est memorise`() = runBlocking {
        val resume = phrases(60).take(1_100)
        val memoire = ArticleMemoire(article(resume))
        val http = HttpPage("<html><body><article><p>${"Cookies. ".repeat(12)}</p></article></body></html>")

        val ok = depot(memoire, http).fetchFullContent(7L)

        assertFalse(ok)
        assertEquals(resume, memoire.article.textContent)
        assertTrue(memoire.article.texteCompletEchec)
        assertNotNull(memoire.article.texteCompletEssaiLe)
        assertTrue("l'index ne bouge pas", memoire.fts.isEmpty())
        assertFalse("setContent ne doit pas être appelé", "setContent" in memoire.appels)
    }

    @Test
    fun `un echec recent n'est pas retente a chaque ouverture`() = runBlocking {
        val memoire = ArticleMemoire(article("Court résumé du flux."))
        val http = HttpPage(null)
        val depot = depot(memoire, http)

        assertFalse(depot.fetchFullContent(7L))
        assertEquals(1, http.appels)
        assertTrue(memoire.article.texteCompletEchec)

        // Deuxième et troisième ouvertures : aucune requête de plus.
        assertFalse(depot.fetchFullContent(7L))
        assertFalse(depot.fetchFullContent(7L))
        assertEquals(1, http.appels)
    }

    @Test
    fun `un echec ancien est retente`() = runBlocking {
        val ancien = System.currentTimeMillis() - TexteComplet.DELAI_NOUVEL_ESSAI_MS - 1_000
        val memoire = ArticleMemoire(
            article("Court résumé du flux.").copy(texteCompletEssaiLe = ancien, texteCompletEchec = true),
        )
        val http = HttpPage(pageAvecTexte(3_000))

        assertTrue(depot(memoire, http).fetchFullContent(7L))

        assertEquals(1, http.appels)
        assertFalse(memoire.article.texteCompletEchec)
    }

    @Test
    fun `sans reseau rien n'est tente ni memorise`() = runBlocking {
        val memoire = ArticleMemoire(article("Court résumé du flux."))
        val http = HttpPage(pageAvecTexte(3_000))

        val ok = depot(memoire, http, reseau = false).fetchFullContent(7L)

        assertFalse(ok)
        assertEquals(0, http.appels)
        assertNull(memoire.article.texteCompletEssaiLe)
        assertFalse(memoire.article.texteCompletEchec)
    }

    @Test
    fun `une page lisible mais pas plus longue garde le texte sans alerte`() = runBlocking {
        val texte = phrases(40).take(900)
        val memoire = ArticleMemoire(article(texte))

        val ok = depot(memoire, HttpPage(pageAvecTexte(1_000))).fetchFullContent(7L)

        assertTrue(ok)
        assertEquals(texte, memoire.article.textContent)
        assertFalse(memoire.article.texteCompletEchec)
        assertNotNull(memoire.article.texteCompletEssaiLe)
    }

    @Test
    fun `un texte deja long ne declenche aucune requete`() = runBlocking {
        val memoire = ArticleMemoire(article("x".repeat(1_300)))
        val http = HttpPage(pageAvecTexte(3_000))

        assertTrue(depot(memoire, http).fetchFullContent(7L))
        assertEquals(0, http.appels)
    }

    @Test
    fun `un article sans adresse n'est pas telecharge`() = runBlocking {
        val memoire = ArticleMemoire(article("Court", url = null))
        val http = HttpPage(pageAvecTexte(3_000))

        assertFalse(depot(memoire, http).fetchFullContent(7L))
        assertEquals(0, http.appels)
    }
}
