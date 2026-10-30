package fr.fh.rss.data

import fr.fh.rss.data.db.ArticleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DoublonsTest {

    private fun article(
        id: Long,
        feedId: Long,
        titre: String,
        url: String? = null,
    ) = ArticleEntity(
        id = id,
        feedId = feedId,
        guid = "g$id",
        title = titre,
        url = url,
    )

    @Test
    fun `meme adresse avec parametres de tracage ne s'affiche qu'une fois`() {
        val articles = listOf(
            article(1, 1, "Le Sénat vote le budget 2027", "https://exemple.fr/a?utm_source=rss"),
            article(2, 2, "Budget 2027 : le vote du Sénat", "http://www.exemple.fr/a/?fbclid=xyz"),
            article(3, 3, "Autre sujet du jour", "https://exemple.fr/b"),
        )

        val gardes = Regroupement.dedoublonner(articles)

        assertEquals(listOf(1L, 3L), gardes.map { it.id })
    }

    @Test
    fun `meme titre chez deux sources ne s'affiche qu'une fois`() {
        val articles = listOf(
            article(1, 1, "Le Sénat vote le budget 2027 en première lecture"),
            article(2, 2, "(AFP) Le Senat vote le budget 2027 en premiere lecture"),
            article(3, 3, "Une information sans rapport avec la précédente"),
        )

        val gardes = Regroupement.dedoublonner(articles)

        assertEquals(listOf(1L, 3L), gardes.map { it.id })
    }

    @Test
    fun `un titre court et generique ne sert pas de cle`() {
        val articles = listOf(
            article(1, 1, "Édito"),
            article(2, 2, "Édito"),
            article(3, 3, "Brève"),
        )

        val gardes = Regroupement.dedoublonner(articles)

        assertEquals(3, gardes.size)
    }

    @Test
    fun `l'ordre de la liste est conserve`() {
        val articles = listOf(
            article(1, 1, "Premier article assez long pour servir de cle", "https://a.fr/x"),
            article(2, 1, "Deuxième article assez long pour servir de cle", "https://a.fr/y"),
            article(3, 2, "Premier article assez long pour servir de cle", "https://a.fr/x"),
        )

        assertEquals(listOf(1L, 2L), Regroupement.dedoublonner(articles).map { it.id })
    }

    @Test
    fun `adresse absente et titre court est conservee`() {
        val gardes = Regroupement.dedoublonner(
            listOf(article(1, 1, "Info"), article(2, 1, "Info")),
        )

        assertEquals(2, gardes.size)
    }

    @Test
    fun `les autres parutions sont rattachees au premier article`() {
        val articles = listOf(
            article(1, 1, "Le Sénat vote le budget 2027 en première lecture", "https://a.fr/x"),
            article(2, 2, "Le Senat vote le budget 2027 en premiere lecture", "https://b.fr/y"),
            article(3, 3, "Une information sans rapport avec la précédente", "https://c.fr/z"),
        )
        val sources = mapOf(1L to "Le Monde", 2L to "AFP", 3L to "Les Échos")

        val autres = Regroupement.autresVersions(articles, sources)

        assertEquals(listOf("AFP"), autres.getValue(1L).map { it.source })
        assertEquals(listOf(2L), autres[1L]?.map { it.articleId })
        assertEquals(listOf("Le Monde"), autres.getValue(2L).map { it.source })
        assertTrue(autres[3L].isNullOrEmpty())
    }

    @Test
    fun `cleUrl normalise schema www ancre et parametres`() {
        assertEquals(
            "exemple.fr/article",
            Regroupement.cleUrl("HTTPS://www.Exemple.fr/article/#commentaires"),
        )
        assertEquals(
            "exemple.fr/article?a=1&b=2",
            Regroupement.cleUrl("https://exemple.fr/article?b=2&a=1&utm_medium=mail"),
        )
        assertNull(Regroupement.cleUrl("   "))
        assertNull(Regroupement.cleUrl(null))
    }
}
