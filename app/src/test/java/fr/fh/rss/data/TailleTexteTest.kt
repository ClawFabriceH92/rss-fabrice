package fr.fh.rss.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Taille du texte du lecteur : réglage A- / A+ et zoom du WebView. */
class TailleTexteTest {

    @Test
    fun `une valeur hors plage est ramenee dans les bornes`() {
        assertEquals(TailleTexte.MIN, TailleTexte.normaliser(0))
        assertEquals(TailleTexte.MIN, TailleTexte.normaliser(-50))
        assertEquals(TailleTexte.MAX, TailleTexte.normaliser(10_000))
        assertEquals(130, TailleTexte.normaliser(130))
    }

    @Test
    fun `A+ et A- avancent d'un pas`() {
        assertEquals(110, TailleTexte.plus(100))
        assertEquals(90, TailleTexte.moins(100))
    }

    @Test
    fun `A+ s'arrete au maximum et A- au minimum`() {
        assertEquals(TailleTexte.MAX, TailleTexte.plus(TailleTexte.MAX))
        assertEquals(TailleTexte.MIN, TailleTexte.moins(TailleTexte.MIN))
        assertFalse(TailleTexte.peutAugmenter(TailleTexte.MAX))
        assertFalse(TailleTexte.peutDiminuer(TailleTexte.MIN))
        assertTrue(TailleTexte.peutAugmenter(TailleTexte.DEFAUT))
        assertTrue(TailleTexte.peutDiminuer(TailleTexte.DEFAUT))
    }

    @Test
    fun `un reglage corrompu ne bloque pas les boutons`() {
        // Valeur trop haute lue en base : A- repart de la borne, pas de la valeur brute.
        assertEquals(TailleTexte.MAX - TailleTexte.PAS, TailleTexte.moins(9_999))
        assertEquals(TailleTexte.MIN + TailleTexte.PAS, TailleTexte.plus(-9_999))
    }

    @Test
    fun `du minimum au maximum, tous les paliers sont atteints sans sortir de la plage`() {
        var valeur = TailleTexte.MIN
        var pas = 0
        while (TailleTexte.peutAugmenter(valeur)) {
            valeur = TailleTexte.plus(valeur)
            pas++
            assertTrue(valeur in TailleTexte.MIN..TailleTexte.MAX)
        }
        assertEquals(TailleTexte.MAX, valeur)
        assertEquals((TailleTexte.MAX - TailleTexte.MIN) / TailleTexte.PAS, pas)
    }

    @Test
    fun `le zoom du WebView multiplie le reglage par l'echelle de police du systeme`() {
        assertEquals(100, TailleTexte.zoomWebView(100, 1f))
        assertEquals(130, TailleTexte.zoomWebView(100, 1.3f))
        assertEquals(156, TailleTexte.zoomWebView(120, 1.3f))
        assertEquals(85, TailleTexte.zoomWebView(100, 0.85f))
    }

    @Test
    fun `une echelle de police absurde est ignoree`() {
        assertEquals(100, TailleTexte.zoomWebView(100, 0f))
        assertEquals(100, TailleTexte.zoomWebView(100, -2f))
        assertEquals(100, TailleTexte.zoomWebView(100, Float.NaN))
    }

    @Test
    fun `le zoom reste borne meme avec un reglage et une police au maximum`() {
        assertEquals(400, TailleTexte.zoomWebView(TailleTexte.MAX, 3f))
        assertEquals(50, TailleTexte.zoomWebView(TailleTexte.MIN, 0.1f))
    }
}
