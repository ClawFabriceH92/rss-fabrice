package fr.fh.rss.ui

import fr.fh.rss.data.Filtre
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vue affichée à l'ouverture : les articles non lus. Ce test verrouille le choix
 * pour qu'une refonte de la navigation ne remette pas discrètement « Tous les
 * articles » par défaut.
 */
class VueParDefautTest {

    @Test
    fun `l application s ouvre sur les articles non lus`() {
        assertEquals(Filtre.NonLus, SELECTION_PAR_DEFAUT.filtre)
        assertEquals("Non lus", SELECTION_PAR_DEFAUT.titre)
    }

    @Test
    fun `la vue par defaut ne depend ni d un dossier ni d une source`() {
        // Une vue d'ensemble reste valable quoi qu'il arrive au dossier ou à la
        // source : elle ne doit donc pas être réinitialisée à chaque suppression.
        val filtre = SELECTION_PAR_DEFAUT.filtre
        assertTrue(filtre !is Filtre.Flux)
        assertTrue(filtre !is Filtre.Dossier)
    }
}
