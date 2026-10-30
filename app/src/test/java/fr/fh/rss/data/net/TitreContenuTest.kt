package fr.fh.rss.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le lecteur affiche le titre de l'article en tête de page : le contenu ne doit
 * pas le répéter.
 */
class TitreContenuTest {

    private val titre = "La CNIL publie son schéma pluriannuel d'accessibilité 2024-2027"

    @Test
    fun `un titre identique en tete de contenu est retire`() {
        val contenu = "<h1>$titre</h1><p>Communiqué du 1er octobre.</p>"

        val nettoye = TitreContenu.retirerTitreDuplique(contenu, titre)

        assertEquals(0, Regex("(?i)schéma pluriannuel").findAll(nettoye).count())
        assertFalse(nettoye.contains("<h1>"))
        assertTrue(nettoye.contains("Communiqué du 1er octobre"))
    }

    @Test
    fun `un titre proche saisi avec une casse et une ponctuation differentes est retire`() {
        val contenu = "<h2>LA CNIL PUBLIE SON SCHEMA PLURIANNUEL D'ACCESSIBILITE 2024 2027 !</h2><p>Suite.</p>"

        val nettoye = TitreContenu.retirerTitreDuplique(contenu, titre)

        assertFalse(nettoye.contains("<h2>"))
        assertTrue(nettoye.contains("Suite."))
    }

    @Test
    fun `un premier titre different est conserve`() {
        val contenu = "<h1>Édito : pourquoi ce sujet compte</h1><p>Texte.</p>"

        val nettoye = TitreContenu.retirerTitreDuplique(contenu, titre)

        assertTrue(nettoye.contains("Édito : pourquoi ce sujet compte"))
    }

    @Test
    fun `un contenu sans titre est inchange`() {
        val contenu = "<p>Un paragraphe sans titre.</p>"

        assertEquals(contenu, TitreContenu.retirerTitreDuplique(contenu, titre))
    }

    @Test
    fun `un titre cite plus loin dans le texte est conserve`() {
        val contenu = "<p>Introduction assez longue pour occuper le début du contenu, avec du texte.</p>" +
            "<h2>$titre</h2><p>Corps.</p>"

        val nettoye = TitreContenu.retirerTitreDuplique(contenu, titre)

        assertTrue(nettoye.contains("<h2>"))
    }

    @Test
    fun `un contenu vide ou nul ne casse rien`() {
        assertEquals("", TitreContenu.retirerTitreDuplique(null, titre))
        assertEquals("", TitreContenu.retirerTitreDuplique("", titre))
        assertEquals("<p>a</p>", TitreContenu.retirerTitreDuplique("<p>a</p>", ""))
    }
}
