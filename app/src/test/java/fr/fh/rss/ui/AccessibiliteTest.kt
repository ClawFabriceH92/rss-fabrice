package fr.fh.rss.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Textes d'accessibilité et bouton « Charger plus ». */
class AccessibiliteTest {

    @Test
    fun `l'etoile annonce l'action selon l'etat`() {
        assertEquals("Ajouter à Lire plus tard", TextesAccessibilite.favori(false))
        assertEquals("Retirer de Lire plus tard", TextesAccessibilite.favori(true))
    }

    @Test
    fun `l'etat lu ou non lu est dit en toutes lettres`() {
        assertEquals("Lu", TextesAccessibilite.etatLecture(true))
        assertEquals("Non lu", TextesAccessibilite.etatLecture(false))
    }

    @Test
    fun `l'action de lecture propose l'inverse de l'etat`() {
        assertEquals("Marquer comme lu", TextesAccessibilite.actionLecture(false))
        assertEquals("Marquer comme non lu", TextesAccessibilite.actionLecture(true))
        assertNotEquals(TextesAccessibilite.actionLecture(true), TextesAccessibilite.actionLecture(false))
    }

    @Test
    fun `l'annonce de collecte donne l'avancement quand il est connu`() {
        assertEquals("Collecte 8 sources sur 45", TextesAccessibilite.collecte(8, 45))
        // Jamais plus de sources faites que de sources à traiter.
        assertEquals("Collecte 45 sources sur 45", TextesAccessibilite.collecte(50, 45))
    }

    @Test
    fun `l'annonce de collecte reste generale sans total`() {
        assertEquals("Collecte en cours", TextesAccessibilite.collecte(null, null))
        assertEquals("Collecte en cours", TextesAccessibilite.collecte(3, 0))
    }

    @Test
    fun `charger plus apparait quand la page est pleine et sous le plafond`() {
        assertTrue(peutChargerPlus(LIMITE_PAGE, LIMITE_PAGE))
        assertTrue(peutChargerPlus(2 * LIMITE_PAGE, 2 * LIMITE_PAGE))
    }

    @Test
    fun `charger plus se cache quand il n'y a plus rien a charger`() {
        assertFalse(peutChargerPlus(LIMITE_PAGE - 1, LIMITE_PAGE))
        assertFalse(peutChargerPlus(0, LIMITE_PAGE))
    }

    @Test
    fun `charger plus se cache au plafond maximal`() {
        assertFalse(peutChargerPlus(LIMITE_MAX, LIMITE_MAX))
        assertFalse(peutChargerPlus(LIMITE_MAX + 10, LIMITE_MAX))
    }
}
