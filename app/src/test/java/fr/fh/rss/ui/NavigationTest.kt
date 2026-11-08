package fr.fh.rss.ui

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Navigation de l'écran principal : sauvegarde de l'écran courant, Retour, barre de recherche. */
class NavigationTest {

    private val ecrans = listOf(
        Ecran.Liste,
        Ecran.Ia,
        Ecran.Reglages,
        Ecran.Lecteur(42L),
        Ecran.Lecteur(7L, Ecran.Ia),
    )

    @Test
    fun `chaque ecran survit a la sauvegarde et a la restauration`() {
        for (ecran in ecrans) {
            assertEquals(ecran, restaurerEcran(ecran.sauvegarder()))
        }
    }

    @Test
    fun `le saver compose sauvegarde le type et l'identifiant d'article`() {
        val portee = SaverScope { true }
        for (ecran in ecrans) {
            val sauve = with(SaverEcran) { portee.save(ecran) }
            assertEquals(ecran, SaverEcran.restore(sauve!!))
        }
        val lecteur = with(SaverEcran) { portee.save(Ecran.Lecteur(42L)) }!!
        assertEquals("lecteur", lecteur[0])
        assertEquals(42L, lecteur[1])
    }

    @Test
    fun `une sauvegarde illisible redonne la liste`() {
        assertEquals(Ecran.Liste, restaurerEcran(emptyList()))
        assertEquals(Ecran.Liste, restaurerEcran(listOf("inconnu")))
        assertEquals(Ecran.Liste, restaurerEcran(listOf("lecteur")))
        assertEquals(Ecran.Liste, restaurerEcran(listOf("lecteur", "pas un nombre")))
    }

    @Test
    fun `un identifiant d'article restaure comme entier reste valide`() {
        // Un Bundle peut rendre un Int à la place d'un Long.
        assertEquals(Ecran.Lecteur(5L), restaurerEcran(listOf("lecteur", 5, "liste")))
    }

    @Test
    fun `Retour depuis le lecteur revient a l'ecran d'origine`() {
        assertEquals(Ecran.Liste, Ecran.Lecteur(1L, Ecran.Liste).retour())
        assertEquals(Ecran.Ia, Ecran.Lecteur(1L, Ecran.Ia).retour())
    }

    @Test
    fun `Retour depuis les autres ecrans mene a la liste`() {
        assertEquals(Ecran.Liste, Ecran.Ia.retour())
        assertEquals(Ecran.Liste, Ecran.Reglages.retour())
        assertEquals(Ecran.Liste, Ecran.Liste.retour())
    }

    @Test
    fun `la barre de recherche n'apparait que sur la liste`() {
        assertTrue(barreRechercheVisible(Ecran.Liste, true))
        assertFalse(barreRechercheVisible(Ecran.Liste, false))
        // Recherche ouverte mais article, IA ou réglages affichés : une seule barre.
        assertFalse(barreRechercheVisible(Ecran.Lecteur(1L), true))
        assertFalse(barreRechercheVisible(Ecran.Ia, true))
        assertFalse(barreRechercheVisible(Ecran.Reglages, true))
    }

    @Test
    fun `Retour n'est capte que s'il y a quelque chose a fermer`() {
        assertFalse(retourActif(Ecran.Liste, false))
        assertTrue(retourActif(Ecran.Liste, true))
        assertTrue(retourActif(Ecran.Lecteur(1L), true))
        assertTrue(retourActif(Ecran.Ia, false))
    }

    @Test
    fun `depuis un resultat de recherche, Retour rend la liste avec sa recherche ouverte`() {
        var ecran: Ecran = Ecran.Lecteur(3L, Ecran.Liste)
        val rechercheOuverte = true
        // Le lecteur est traité en premier : l'écran change, la recherche reste telle quelle.
        ecran = ecran.retour()
        assertEquals(Ecran.Liste, ecran)
        assertTrue(barreRechercheVisible(ecran, rechercheOuverte))
    }
}
