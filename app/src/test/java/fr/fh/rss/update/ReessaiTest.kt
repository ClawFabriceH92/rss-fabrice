package fr.fh.rss.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Politique de réessai : un 504 du CDN GitHub est passager (vécu le 09/11/2026),
 * il doit être retenté et annoncé comme un incident côté GitHub, pas comme une
 * panne de l'application.
 */
class ReessaiTest {

    @Test
    fun `les reponses 5xx sont retentees`() {
        assertTrue(Reessai.reessayable(500))
        assertTrue(Reessai.reessayable(502))
        assertTrue(Reessai.reessayable(503))
        assertTrue(Reessai.reessayable(504))
        assertTrue(Reessai.reessayable(599))
    }

    @Test
    fun `les echecs definitifs ne sont pas retentes`() {
        assertFalse(Reessai.reessayable(400))
        assertFalse(Reessai.reessayable(403))
        assertFalse(Reessai.reessayable(404))
        assertFalse(Reessai.reessayable(200))
    }

    @Test
    fun `un 504 est explique au lieu d'etre un code brut`() {
        val raison = Reessai.raison(504)
        assertTrue(raison, raison.contains("504"))
        assertTrue(raison, raison.contains("momentanément indisponible"))
        assertFalse(raison, raison == "HTTP 504")
    }

    @Test
    fun `un code inconnu reste lisible`() {
        assertEquals("HTTP 418", Reessai.raison(418))
    }

    @Test
    fun `l'attente augmente a chaque essai et reste bornee`() {
        assertTrue(Reessai.pauseMs(1) < Reessai.pauseMs(2))
        assertTrue(Reessai.pauseMs(0) > 0)
        assertEquals(3, Reessai.TENTATIVES)
    }
}
