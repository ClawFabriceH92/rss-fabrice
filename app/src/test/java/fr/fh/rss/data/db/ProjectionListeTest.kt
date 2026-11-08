package fr.fh.rss.data.db

import fr.fh.rss.data.Regroupement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Les listes d'articles ne chargent plus les corps : projection légère [ArticleLigne]. */
class ProjectionListeTest {

    private fun typeElement(nom: String): String {
        val methode = ArticleDao::class.java.methods.first { it.name == nom }
        // Flow<List<X>> : le type générique garde X, le type brut serait Flow.
        return methode.genericReturnType.toString()
    }

    @Test
    fun `les six requetes de liste renvoient la projection legere`() {
        for (nom in listOf(
            "observeAll", "observeUnread", "observeStarred",
            "observeByFeed", "observeByFolder", "observeSearch",
        )) {
            val type = typeElement(nom)
            assertTrue("$nom doit renvoyer ArticleLigne : $type", type.contains("ArticleLigne"))
            assertFalse("$nom ne doit plus renvoyer ArticleEntity : $type", type.contains("ArticleEntity"))
        }
    }

    @Test
    fun `le lecteur garde l'article complet`() {
        assertTrue(typeElement("observeById").contains("ArticleEntity"))
        // byId est suspend : le type d'article est le premier paramètre générique du retour.
        val byId = ArticleDao::class.java.methods.first { it.name == "byId" }
        assertTrue(byId.parameterTypes.first() == Long::class.javaPrimitiveType)
    }

    @Test
    fun `la notification ne lit que des titres`() {
        // Fonction suspend : le type générique du retour est effacé, on lit la déclaration.
        val source = File("src/main/java/fr/fh/rss/data/db/Daos.kt")
            .takeIf { it.exists() }?.readText()
            ?: File("app/src/main/java/fr/fh/rss/data/db/Daos.kt").readText()
        assertTrue(source.contains("SELECT title FROM articles WHERE isRead = 0 AND fetchedAt > :depuis"))
        assertTrue(source.contains("suspend fun titresNonLusDepuis(depuis: Long, limite: Int): List<String>"))
        assertTrue(ArticleDao::class.java.methods.none { it.name == "derniersNonLus" })
    }

    @Test
    fun `la projection ne porte aucun corps d'article`() {
        val champs = ArticleLigne::class.java.declaredFields
            .filterNot { it.isSynthetic || it.name == "\$stable" }
            .map { it.name }
            .toSet()
        assertEquals(
            setOf(
                "id", "feedId", "title", "url", "author", "publishedAt",
                "imageUrl", "isRead", "isStarred", "extrait",
            ),
            champs,
        )
        assertFalse("contentHtml" in champs || "textContent" in champs || "summaryHtml" in champs)
    }

    private fun ligne(id: Long, feedId: Long, titre: String, url: String? = null, extrait: String = "") =
        ArticleLigne(
            id = id, feedId = feedId, title = titre, url = url, author = null,
            publishedAt = 0L, imageUrl = null, isRead = false, isStarred = false, extrait = extrait,
        )

    @Test
    fun `le dedoublonnage fonctionne sur la projection`() {
        val lignes = listOf(
            ligne(1, 1, "Titre A assez long pour servir de cle", "https://exemple.fr/a?utm_source=x"),
            ligne(2, 2, "Autre titre sans rapport du tout ici", "https://www.exemple.fr/a"),
            ligne(3, 3, "Troisième article indépendant, titre long", "https://exemple.fr/c"),
        )

        val gardes = Regroupement.dedoublonner(lignes)

        assertEquals(listOf(1L, 3L), gardes.map { it.id })
        val autres = Regroupement.autresVersions(lignes, mapOf(1L to "A", 2L to "B"))
        assertEquals(listOf("B"), autres[1L]?.map { it.source })
    }

    @Test
    fun `le theme se devine sur l'extrait de la projection`() {
        val lignes = listOf(
            ligne(1, 1, "Une note", extrait = "La CNIL sanctionne une fuite de données personnelles"),
        )

        val themes = Regroupement.parTheme(Regroupement.regrouper(lignes, mapOf(1L to "S")))

        assertEquals("Numérique et données", themes.single().first)
    }

    @Test
    fun `l'entite complete reste regroupable avec un apercu borne`() {
        val entite = ArticleEntity(
            id = 1, feedId = 1, guid = "g", title = "T", textContent = "x".repeat(5000),
        )
        assertEquals(600, entite.texteApercu.length)
    }
}
