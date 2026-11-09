package fr.fh.rss.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Catalogue embarqué : il doit rester cohérent (adresses uniques, thèmes connus)
 * et le choix du modèle doit être traduit sans jamais sortir du catalogue.
 */
class CatalogueSourcesTest {

    @Test
    fun `chaque entree porte un theme connu et une adresse http`() {
        assertTrue(CatalogueSources.entrees.size >= 50)
        for (entree in CatalogueSources.entrees) {
            assertTrue(entree.nom.isNotBlank())
            assertTrue("${entree.url} n'est pas http(s)", entree.url.startsWith("http"))
            assertTrue(
                "${entree.nom} : thème inconnu ${entree.theme}",
                entree.theme in CatalogueSources.THEMES,
            )
        }
    }

    @Test
    fun `aucune adresse en double dans le catalogue`() {
        val cles = CatalogueSources.entrees.map { CatalogueSources.cle(it.url) }
        assertEquals(cles.size, cles.distinct().size)
    }

    @Test
    fun `chaque theme propose au moins une source`() {
        for (theme in CatalogueSources.THEMES) {
            assertTrue("thème vide : $theme", CatalogueSources.parTheme(theme).isNotEmpty())
            assertTrue(theme, CatalogueSources.compteParTheme()[theme]!! > 0)
        }
    }

    @Test
    fun `le domaine comptable est couvert`() {
        val compta = CatalogueSources.parTheme("Compta et audit").map { it.url }
        assertTrue(compta.any { it.contains("experts-comptables.fr") })
        assertTrue(compta.any { it.contains("anc.gouv.fr") })
    }

    @Test
    fun `les sources deja suivies sortent du catalogue d'un theme`() {
        val theme = "Compta et audit"
        val premiere = CatalogueSources.parTheme(theme).first()
        val restantes = CatalogueSources.nonSuivies(listOf(premiere.url), theme)
        assertFalse(restantes.any { CatalogueSources.cle(it.url) == CatalogueSources.cle(premiere.url) })
        assertEquals(CatalogueSources.parTheme(theme).size - 1, restantes.size)
    }

    @Test
    fun `une source suivie avec un autre schema ou une barre finale est reconnue`() {
        val entree = CatalogueSources.parTheme("Compta et audit").first()
        val variante = entree.url.replace("https://", "http://") + "/"
        // La comparaison ignore le schéma, la casse et la barre finale.
        assertTrue(
            CatalogueSources.nonSuivies(listOf(variante), entree.theme)
                .none { CatalogueSources.cle(it.url) == CatalogueSources.cle(entree.url) },
        )
    }

    @Test
    fun `les numeros du modele designent des entrees du catalogue`() {
        val candidats = CatalogueSources.entrees.take(10)
        val choix = CatalogueSources.depuisNumeros("Je retiens : 2, 5 et 9.", candidats)
        assertEquals(listOf(candidats[1], candidats[4], candidats[8]), choix)
    }

    @Test
    fun `un numero hors catalogue est ignore au lieu de lever une erreur`() {
        val candidats = CatalogueSources.entrees.take(5)
        val choix = CatalogueSources.depuisNumeros("1, 4, 42, 0, 99", candidats)
        assertEquals(2, choix.size)
        assertEquals(candidats[0], choix[0])
        assertEquals(candidats[3], choix[1])
    }

    @Test
    fun `un numero repete ne compte qu'une fois`() {
        val candidats = CatalogueSources.entrees.take(5)
        assertEquals(1, CatalogueSources.depuisNumeros("3, 3, 3", candidats).size)
    }

    @Test
    fun `le repli par nom retrouve les entrees citees en clair`() {
        val candidats = CatalogueSources.entrees.take(12)
        val choix = CatalogueSources.depuisNoms("Je propose Dalloz actualité et Kotlin (JetBrains).", candidats)
        assertTrue(choix.any { it.nom.contains("Dalloz") })
    }

    @Test
    fun `les mots-cles filtrent le catalogue sans tenir compte des accents ni de la casse`() {
        val compta = CatalogueSources.parTheme("Compta et audit")
        val filtre = CatalogueSources.filtrer(CatalogueSources.entrees, "EXPERTS-COMPTABLES")
        assertTrue(filtre.any { it.nom.contains("Ordre des experts") })

        // « economie » retrouve le thème « Économie » malgré l'accent.
        val economie = CatalogueSources.filtrer(CatalogueSources.entrees, "economie")
        assertTrue(economie.isNotEmpty())
        assertTrue(economie.all { CatalogueSources.normaliser(it.theme + it.nom).isNotEmpty() })
        assertTrue(compta.isNotEmpty())
    }

    @Test
    fun `plusieurs mots-cles fonctionnent en l'un ou l'autre`() {
        val filtre = CatalogueSources.filtrer(CatalogueSources.entrees, "kotlin, IFRS")
        assertTrue(filtre.any { it.nom.contains("Kotlin") })
        assertTrue(filtre.size >= 1)
    }

    @Test
    fun `sans mot-cle la liste est rendue telle quelle`() {
        assertEquals(CatalogueSources.entrees, CatalogueSources.filtrer(CatalogueSources.entrees, "   "))
        assertEquals(CatalogueSources.entrees, CatalogueSources.filtrer(CatalogueSources.entrees, ""))
    }

    @Test
    fun `un mot-cle inconnu ne rend rien plutot que tout`() {
        assertTrue(CatalogueSources.filtrer(CatalogueSources.entrees, "zzzzqqq").isEmpty())
    }

    @Test
    fun `les mots-cles sont decoupes sur les virgules et les espaces`() {
        assertEquals(
            listOf("ifrs", "tva", "kotlin"),
            CatalogueSources.decouperMotsCles("IFRS, TVA kotlin"),
        )
        // Un mot d'une lettre est ignoré : il filtrerait n'importe quoi.
        assertEquals(listOf("tva"), CatalogueSources.decouperMotsCles("a tva"))
    }

    @Test
    fun `les mots-cles sont transmis au modele`() {
        val candidats = CatalogueSources.entrees.take(3)
        val consigne = CatalogueSources.consigne(candidats, 4, motsCles = "IFRS, TVA")
        assertTrue(consigne, consigne.contains("IFRS, TVA"))
        assertTrue(consigne, consigne.contains("4 sources"))
    }

    @Test
    fun `la consigne numerote le catalogue et demande des numeros`() {
        val candidats = CatalogueSources.entrees.take(3)
        val consigne = CatalogueSources.consigne(candidats, 2)
        assertTrue(consigne, consigne.contains("1. ${candidats[0].nom}"))
        assertTrue(consigne, consigne.contains("numéros"))
        assertTrue(consigne, consigne.contains("2 sources"))
    }
}
