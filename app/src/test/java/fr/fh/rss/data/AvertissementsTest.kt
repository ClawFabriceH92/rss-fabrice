package fr.fh.rss.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Textes de confirmation des actions irréversibles et seuil d'avertissement de l'archive. */
class AvertissementsTest {

    @Test
    fun `la suppression annonce les favoris conserves`() {
        val texte = Avertissements.suppressionSources("Le Monde", 1, 3)
        assertTrue(texte, texte.contains("« Le Monde »"))
        assertTrue(texte, texte.contains("3 favoris"))
        assertTrue(texte, texte.contains("conservés"))
    }

    @Test
    fun `un seul favori s'accorde au singulier`() {
        val texte = Avertissements.suppressionSources("Blog", 1, 1)
        assertTrue(texte, texte.contains("1 favori ") && texte.contains("sera conservé"))
    }

    @Test
    fun `sans favori la confirmation le dit par la mention definitive`() {
        val texte = Avertissements.suppressionSources("Blog", 1, 0)
        assertFalse(texte, texte.contains("conservé"))
        assertTrue(texte, texte.contains("définitive"))
    }

    @Test
    fun `plusieurs sources sont comptees`() {
        val texte = Avertissements.suppressionSources(null, 12, 5)
        assertTrue(texte, texte.contains("12 sources"))
        assertTrue(texte, texte.contains("5 favoris"))
    }

    @Test
    fun `reduire l'archive sous le nombre d'articles demande confirmation`() {
        assertTrue(ApercuArchive(limite = 5000, total = 8000, aSupprimer = 3000, dontNonLus = 10).exigeConfirmation)
        assertFalse(ApercuArchive(limite = 20000, total = 8000, aSupprimer = 0, dontNonLus = 0).exigeConfirmation)
        // Limite égale au nombre d'articles : rien ne sera supprimé.
        assertFalse(ApercuArchive(limite = 5000, total = 5000, aSupprimer = 0, dontNonLus = 0).exigeConfirmation)
    }

    @Test
    fun `l'avertissement d'archive chiffre la purge et les non lus`() {
        val texte = ApercuArchive(limite = 5000, total = 8000, aSupprimer = 3000, dontNonLus = 120).message()
        assertTrue(texte, texte.contains("8000 articles"))
        assertTrue(texte, texte.contains("limite de 5000"))
        assertTrue(texte, texte.contains("3000 articles"))
        assertTrue(texte, texte.contains("120 non lus"))
        assertTrue(texte, texte.contains("favoris"))
    }

    @Test
    fun `le libelle des favoris orphelins est stable`() {
        assertEquals("Source supprimée", Avertissements.SOURCE_SUPPRIMEE)
    }
}
