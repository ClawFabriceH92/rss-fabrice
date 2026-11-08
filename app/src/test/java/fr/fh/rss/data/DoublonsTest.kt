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

    // ------------------------------------------- doublons : sources différentes seulement

    @Test
    fun `deux articles de la meme source au titre identique restent tous les deux`() {
        val titre = "Les offres d'emploi de la semaine à Asnières"
        val articles = listOf(
            article(1, 1, titre, "https://a.fr/emploi/semaine-45"),
            article(2, 1, titre, "https://a.fr/emploi/semaine-44"),
        )

        assertEquals(listOf(1L, 2L), Regroupement.dedoublonner(articles).map { it.id })
    }

    @Test
    fun `deux articles de la meme source a la meme adresse restent tous les deux`() {
        val articles = listOf(
            article(1, 1, "Épisode 12 du podcast, partie un", "https://a.fr/podcast/ecouter"),
            article(2, 1, "Épisode 13 du podcast, partie deux", "https://a.fr/podcast/ecouter"),
        )

        assertEquals(2, Regroupement.dedoublonner(articles).size)
    }

    @Test
    fun `un doublon d'une autre source est masque mais pas le second de la premiere`() {
        val titre = "Le Sénat vote le budget 2027 en première lecture"
        val articles = listOf(
            article(1, 1, titre),
            article(2, 2, titre),
            article(3, 1, titre),
        )

        assertEquals(listOf(1L, 3L), Regroupement.dedoublonner(articles).map { it.id })
    }

    @Test
    fun `une adresse sans chemin ne sert pas de cle`() {
        // Flux dont tous les liens visent la page d'accueil du site.
        val articles = listOf(
            article(1, 1, "Épisode du lundi", "https://exemple.fr"),
            article(2, 2, "Épisode du mardi", "https://www.exemple.fr/"),
            article(3, 3, "Épisode du mercredi", "http://exemple.fr/#direct"),
        )

        assertEquals(3, Regroupement.dedoublonner(articles).size)
        assertTrue(Regroupement.autresVersions(articles, emptyMap()).isEmpty())
    }

    @Test
    fun `une adresse a parametre identifie l'article meme sans chemin`() {
        val articles = listOf(
            article(1, 1, "Premier titre", "https://exemple.fr/?p=42"),
            article(2, 2, "Second titre", "https://www.exemple.fr?p=42&utm_source=rss"),
            article(3, 3, "Troisième titre", "https://exemple.fr/?p=43"),
        )

        assertEquals(listOf(1L, 3L), Regroupement.dedoublonner(articles).map { it.id })
    }

    @Test
    fun `vu aussi sur ne relie pas deux articles de la meme source`() {
        val titre = "Le Sénat vote le budget 2027 en première lecture"
        val articles = listOf(
            article(1, 1, titre, "https://a.fr/x"),
            article(2, 1, titre, "https://a.fr/y"),
            article(3, 2, titre, "https://b.fr/z"),
        )
        val sources = mapOf(1L to "Le Monde", 2L to "AFP")

        val autres = Regroupement.autresVersions(articles, sources)

        // L'article 1 ne voit que l'autre source ; son voisin de la même source est affiché à part.
        assertEquals(listOf(3L), autres.getValue(1L).map { it.articleId })
        assertEquals(listOf(3L), autres.getValue(2L).map { it.articleId })
        assertEquals(listOf(1L, 2L), autres.getValue(3L).map { it.articleId })
    }

    // ------------------------------------------------------------- compteurs

    @Test
    fun `le compteur compte ce que la liste affiche`() {
        val titre = "Le Sénat vote le budget 2027 en première lecture"
        val articles = listOf(
            article(1, 1, titre),
            article(2, 2, titre),
            article(3, 3, titre, "https://c.fr/a"),
            article(4, 1, "Un autre article sans rapport avec le premier"),
        )

        assertEquals(2, Regroupement.nombreAffiche(articles))
        assertEquals(Regroupement.dedoublonner(articles).size, Regroupement.nombreAffiche(articles))
    }

    @Test
    fun `les compteurs de dossier dedoublonnent dans chaque dossier`() {
        val titre = "Le Sénat vote le budget 2027 en première lecture"
        val articles = listOf(
            article(1, 1, titre),
            article(2, 2, titre),
            article(3, 3, titre),
            article(4, 9, "Article d'une source dont le dossier est inconnu"),
        )
        // Sources 1 et 2 dans le dossier 10, source 3 sans dossier, source 9 absente.
        val dossiers = mapOf<Long, Long?>(1L to 10L, 2L to 10L, 3L to null)

        val comptes = Regroupement.nombreParDossier(articles, dossiers)

        assertEquals(mapOf(10L to 1, Regroupement.SANS_DOSSIER to 1), comptes)
    }
}
