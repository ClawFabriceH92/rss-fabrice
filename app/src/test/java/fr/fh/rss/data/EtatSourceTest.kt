package fr.fh.rss.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EtatSourceTest {

    private val jour = 24L * 60L * 60L * 1000L
    private val maintenant = 1_800_000_000_000L

    @Test
    fun `une source qui repond n'a pas de jours d'echec`() {
        assertEquals(0, EtatSource.joursEnEchec(null, maintenant))
        assertFalse(EtatSource.propositionDue(null, maintenant))
    }

    @Test
    fun `les jours d'echec se comptent en jours pleins`() {
        assertEquals(0, EtatSource.joursEnEchec(maintenant - jour / 2, maintenant))
        assertEquals(1, EtatSource.joursEnEchec(maintenant - jour, maintenant))
        assertEquals(7, EtatSource.joursEnEchec(maintenant - 7 * jour, maintenant))
    }

    @Test
    fun `la proposition attend huit jours d'echec`() {
        assertFalse(EtatSource.propositionDue(maintenant - 7 * jour, maintenant))
        assertFalse(EtatSource.propositionDue(maintenant - 8 * jour + jour / 3, maintenant))
        assertTrue(EtatSource.propositionDue(maintenant - 8 * jour, maintenant))
        assertTrue(EtatSource.propositionDue(maintenant - 30 * jour, maintenant))
    }

    @Test
    fun `le resume annonce le seuil avant de proposer`() {
        val avant = EtatSource.resume(maintenant - 3 * jour, 6, maintenant)
        assertTrue(avant.contains("3 jours"))
        assertTrue(avant.contains("proposition au 8e jour"))

        val apres = EtatSource.resume(maintenant - 9 * jour, 20, maintenant)
        assertTrue(apres.contains("9 jours"))
        assertTrue(apres.contains("à examiner"))
        assertTrue(apres.contains("20 échecs"))
    }

    @Test
    fun `une date d'echec dans le futur ne produit pas de proposition`() {
        assertFalse(EtatSource.propositionDue(maintenant + 5 * jour, maintenant))
        assertEquals(0, EtatSource.joursEnEchec(maintenant + 5 * jour, maintenant))
    }

    @Test
    fun `une source mise au repos apres trois echecs n'est plus interrogee`() {
        val ilYaUneHeure = maintenant - 60L * 60L * 1000L

        assertFalse(EtatSource.doitAttendre(2, ilYaUneHeure, maintenant))
        assertTrue(EtatSource.doitAttendre(3, ilYaUneHeure, maintenant))
        assertTrue(EtatSource.doitAttendre(10, ilYaUneHeure, maintenant))
    }

    @Test
    fun `le repos expire au bout de six heures`() {
        val sixHeures = 6L * 60L * 60L * 1000L

        assertTrue(EtatSource.doitAttendre(4, maintenant - sixHeures + 60_000L, maintenant))
        assertFalse(EtatSource.doitAttendre(4, maintenant - sixHeures, maintenant))
        assertFalse(EtatSource.doitAttendre(4, maintenant - 12 * sixHeures, maintenant))
    }

    @Test
    fun `les minutes de repos restantes sont arrondies vers le haut`() {
        val deuxHeures = 2L * 60L * 60L * 1000L

        assertEquals(0, EtatSource.minutesDePause(1, maintenant, maintenant))
        assertEquals(241, EtatSource.minutesDePause(3, maintenant - deuxHeures + 1, maintenant))
        assertEquals(240, EtatSource.minutesDePause(3, maintenant - deuxHeures, maintenant))
        assertEquals(0, EtatSource.minutesDePause(3, maintenant - 7L * 60L * 60L * 1000L, maintenant))
    }

    @Test
    fun `le resume signale le repos en cours`() {
        val texte = EtatSource.resume(
            errorDepuis = maintenant - 60L * 60L * 1000L,
            tentatives = 4,
            maintenant = maintenant,
            dernierEssai = maintenant - 60L * 60L * 1000L,
        )
        assertTrue(texte.contains("repos"))
        assertTrue(texte.contains("5 h") || texte.contains("300 min") || texte.contains("min"))
    }
}
