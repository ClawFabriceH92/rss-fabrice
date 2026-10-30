package fr.fh.rss.data

import fr.fh.rss.data.db.ArticleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegroupementTest {

    private fun article(id: Long, feedId: Long, titre: String, texte: String = "") = ArticleEntity(
        id = id,
        feedId = feedId,
        guid = "g$id",
        title = titre,
        textContent = texte,
    )

    private val sources = mapOf(1L to "AFP", 2L to "Le Monde", 3L to "ConsoGlobe")

    @Test
    fun `deux sources qui publient la meme depeche ne font qu'un groupe`() {
        val articles = listOf(
            article(1, 1, "Le Sénat vote le budget 2027"),
            article(2, 2, "Le Senat vote le budget 2027 !"),
            article(3, 3, "Une autre information sans rapport"),
        )

        val groupes = Regroupement.regrouper(articles, sources)

        assertEquals(2, groupes.size)
        assertEquals(1, Regroupement.nombreDoublons(groupes))
        val doublon = groupes.first { it.doublon }
        assertEquals(listOf("AFP", "Le Monde"), doublon.sources)
        assertEquals(1L, doublon.article.id)
    }

    @Test
    fun `les prefixes de type AFP ou direct sont ignores`() {
        assertEquals(
            Regroupement.normaliserTitre("Budget 2027 adopté"),
            Regroupement.normaliserTitre("DIRECT : Budget 2027 adopté (AFP)"),
        )
    }

    @Test
    fun `deux titres differents ne sont jamais fusionnes`() {
        val articles = listOf(
            article(1, 1, "Le Sénat vote le budget 2027"),
            article(2, 2, "Le Sénat rejette le budget 2027"),
        )

        val groupes = Regroupement.regrouper(articles, sources)

        assertEquals(2, groupes.size)
        assertFalse(groupes.any { it.doublon })
    }

    @Test
    fun `le premier article du groupe est celui qui reste`() {
        val articles = listOf(
            article(10, 3, "Canicule : alerte orange"),
            article(11, 1, "Canicule : alerte orange"),
        )

        val groupes = Regroupement.regrouper(articles, sources)

        assertEquals(1, groupes.size)
        assertEquals(10L, groupes.first().article.id)
    }

    @Test
    fun `une source inconnue ne fait pas planter le regroupement`() {
        val groupes = Regroupement.regrouper(listOf(article(1, 99, "Titre seul")), sources)
        assertEquals(listOf("source inconnue"), groupes.first().sources)
    }

    @Test
    fun `le theme se deduit des mots cles`() {
        assertEquals(
            "Climat et énergie",
            Regroupement.themeDe("Canicule et sécheresse : les renouvelables en hausse"),
        )
        assertEquals(
            "Fiscalité et droit",
            Regroupement.themeDe("Le BOFiP précise la TVA sur les cotisations"),
        )
        assertEquals(
            "Numérique et données",
            Regroupement.themeDe("La CNIL sanctionne une fuite de données"),
        )
        assertEquals(
            Regroupement.THEME_AUTRE,
            Regroupement.themeDe("Un chat traverse la route départementale"),
        )
    }

    @Test
    fun `le classement par theme ne rend que les themes remplis`() {
        val articles = listOf(
            article(1, 1, "La CNIL publie son rapport", "données personnelles et rgpd"),
            article(2, 2, "Le BOFiP sur la TVA", "fiscalité"),
            article(3, 3, "Un chat sur la route"),
        )

        val themes = Regroupement.parTheme(Regroupement.regrouper(articles, sources))

        assertEquals(3, themes.size)
        assertEquals("Fiscalité et droit", themes.first().first)
        assertTrue(themes.any { it.first == Regroupement.THEME_AUTRE })
    }
}
